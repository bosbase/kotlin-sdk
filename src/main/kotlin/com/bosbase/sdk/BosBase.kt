package com.bosbase.sdk

import com.bosbase.sdk.services.BackupService
import com.bosbase.sdk.services.BatchService
import com.bosbase.sdk.services.CacheService
import com.bosbase.sdk.services.CollectionService
import com.bosbase.sdk.services.CronService
import com.bosbase.sdk.services.FileService
import com.bosbase.sdk.services.GraphQLService
import com.bosbase.sdk.services.HealthService
import com.bosbase.sdk.services.LangChaingoService
import com.bosbase.sdk.services.LLMDocumentService
import com.bosbase.sdk.services.LogService
import com.bosbase.sdk.services.PubSubService
import com.bosbase.sdk.services.RecordService
import com.bosbase.sdk.services.RealtimeService
import com.bosbase.sdk.services.SettingsService
import com.bosbase.sdk.services.VectorService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
private const val USER_AGENT = "bosbase-kotlin-sdk/0.1.0"

private data class AutoRefreshState(
    val thresholdSeconds: Long,
    val refreshFunc: () -> Unit,
    val reauthenticateFunc: () -> Unit,
    val originalBeforeSend: ((String, RequestOptions) -> BeforeSendResult?)?,
    val initialRecordId: String?,
    val initialCollectionId: String?,
)

class BosBase(
    baseUrl: String,
    val lang: String = "en-US",
    val authStore: BaseAuthStore = LocalAuthStore(),
    internal val httpClient: OkHttpClient = OkHttpClient(),
) {
    val baseUrl: String = baseUrl.trimEnd('/').ifEmpty { "/" }
    private val normalizedBaseUrl = this.baseUrl
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val recordServices = ConcurrentHashMap<String, RecordService>()
    private val cancelCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private var autoRefreshState: AutoRefreshState? = null

    var enableAutoCancellation: Boolean = true

    val collections = CollectionService(this)
    val files = FileService(this)
    val logs = LogService(this)
    val realtime = RealtimeService(this)
    val health = HealthService(this)
    val backups = BackupService(this)
    val crons = CronService(this)
    val vectors = VectorService(this)
    val llmDocuments = LLMDocumentService(this)
    val langchaingo = LangChaingoService(this)
    val caches = CacheService(this)
    val graphql = GraphQLService(this)
    val pubsub = PubSubService(this)
    val settings = SettingsService(this)

    var beforeSend: ((String, RequestOptions) -> BeforeSendResult?)? = null
    var afterSend: ((okhttp3.Response, JsonElement?, RequestOptions) -> JsonElement?)? = null

    val admins: RecordService
        get() = collection("_superusers")

    fun collection(idOrName: String): RecordService {
        return recordServices.computeIfAbsent(idOrName) { RecordService(this, it) }
    }

    fun createBatch(): BatchService = BatchService(this)

    fun autoCancellation(enable: Boolean): BosBase {
        enableAutoCancellation = enable
        return this
    }

    fun cancelRequest(requestKey: String): BosBase {
        cancelCalls.remove(requestKey)?.cancel()
        return this
    }

    fun cancelAllRequests(): BosBase {
        cancelCalls.values.forEach { it.cancel() }
        cancelCalls.clear()
        return this
    }

    fun registerAutoRefresh(
        thresholdSeconds: Long,
        refreshFunc: () -> Unit,
        reauthenticateFunc: () -> Unit,
    ) {
        resetAutoRefresh()

        val initialModel = authStore.model
        val initialRecordId = initialModel?.get("id")?.jsonPrimitive?.contentOrNull
        val initialCollectionId = initialModel?.get("collectionId")?.jsonPrimitive?.contentOrNull
        val originalBeforeSend = beforeSend

        autoRefreshState = AutoRefreshState(
            thresholdSeconds = thresholdSeconds,
            refreshFunc = refreshFunc,
            reauthenticateFunc = reauthenticateFunc,
            originalBeforeSend = originalBeforeSend,
            initialRecordId = initialRecordId,
            initialCollectionId = initialCollectionId,
        )

        beforeSend = block@{ url, options ->
            val state = autoRefreshState
            if (state == null) {
                return@block originalBeforeSend?.invoke(url, options)
            }

            val currentRecordId = authStore.model?.get("id")?.jsonPrimitive?.contentOrNull
            val currentCollectionId = authStore.model?.get("collectionId")?.jsonPrimitive?.contentOrNull
            if (state.initialRecordId != null &&
                (currentRecordId == null ||
                    currentRecordId != state.initialRecordId ||
                    currentCollectionId != state.initialCollectionId)
            ) {
                resetAutoRefresh()
                return@block originalBeforeSend?.invoke(url, options) ?: BeforeSendResult(options = options)
            }

            val skipAutoRefresh = options.query["autoRefresh"]?.toString()?.toBoolean() == true
            val previousToken = authStore.token
            if (!skipAutoRefresh) {
                var isValid = authStore.isValid
                if (isValid && isTokenExpiring(authStore.token, state.thresholdSeconds)) {
                    try {
                        state.refreshFunc()
                    } catch (_: Exception) {
                        isValid = authStore.isValid
                    }
                }

                if (!authStore.isValid && !isValid) {
                    try {
                        state.reauthenticateFunc()
                    } catch (_: Exception) {
                        // Ignore to let the underlying request bubble up the failure.
                    }
                }

                syncAuthorizationHeader(options, previousToken)
            }

            return@block originalBeforeSend?.invoke(url, options) ?: BeforeSendResult(options = options)
        }
    }

    fun resetAutoRefresh() {
        val state = autoRefreshState
        if (state != null) {
            beforeSend = state.originalBeforeSend
            autoRefreshState = null
        }
    }

    fun filter(expr: String, params: Map<String, Any?>? = null): String {
        if (params.isNullOrEmpty()) return expr
        var result = expr
        for ((key, value) in params) {
            val placeholder = "{:$key}"
            val replacement = when (value) {
                null -> "null"
                is Boolean -> if (value) "true" else "false"
                is Number -> value.toString()
                is java.time.temporal.TemporalAccessor -> "'$value'"
                is java.util.Date -> {
                    val instant = value.toInstant()
                    val iso = java.time.format.DateTimeFormatter.ISO_INSTANT.format(instant)
                    "'${iso.replace('T', ' ')}'"
                }
                else -> {
                    val serialized = try {
                        json.encodeToString(JsonElement.serializer(), toJsonElement(value))
                    } catch (_: Exception) {
                        value.toString()
                    }
                    "'" + serialized.replace("'", "\\'") + "'"
                }
            }
            result = result.replace(placeholder, replacement)
        }
        return result
    }

    fun buildUrl(path: String, query: Map<String, Any?>? = null): HttpUrl {
        val target = "${normalizedBaseUrl}/${path.trimStart('/')}"
        val builder = target.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL: $target")

        query?.forEach { (key, value) ->
            appendQuery(builder, key, value)
        }

        return builder.build()
    }

    fun send(
        path: String,
        method: String = "GET",
        headers: Map<String, String>? = null,
        query: Map<String, Any?>? = null,
        body: Any? = null,
        files: Map<String, List<FileAttachment>>? = null,
        timeoutSeconds: Long? = null,
        requestKey: String? = null,
        autoCancel: Boolean = true,
    ): JsonElement? {
        val hookOptions = RequestOptions(
            method = method,
            headers = headers?.toMutableMap() ?: mutableMapOf(),
            query = query?.toMutableMap() ?: mutableMapOf(),
            body = body,
            files = files,
            timeoutSeconds = timeoutSeconds,
            requestKey = requestKey,
            autoCancel = autoCancel,
        )

        val urlBeforeHooks = buildUrl(path, hookOptions.query)

        val (finalOptions, overrideUrl) = applyBeforeSend(urlBeforeHooks.toString(), hookOptions)

        normalizeRequestKey(finalOptions)

        val targetUrl = overrideUrl?.toHttpUrlOrNull() ?: buildUrl(path, finalOptions.query)
        val requestBuilder = Request.Builder().url(targetUrl)

        val computedHeaders = mutableMapOf(
            "Accept-Language" to lang,
            "User-Agent" to USER_AGENT,
        )
        if (authStore.isValid) {
            authStore.token?.let { computedHeaders["Authorization"] = it }
        }
        computedHeaders.putAll(finalOptions.headers)
        computedHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

        val payload = finalOptions.body
        val filesPayload = finalOptions.files

        val requestBody = buildRequestBody(payload, filesPayload)
        val upperMethod = finalOptions.method.trim().ifEmpty { "GET" }.uppercase(Locale.US)
        if (requestBody != null) {
            requestBuilder.method(upperMethod, requestBody)
        } else {
            requestBuilder.method(
                upperMethod,
                if (requiresRequestBody(upperMethod)) "".toRequestBody(null) else null,
            )
        }

        val cancelKey = if (enableAutoCancellation && finalOptions.autoCancel) {
            finalOptions.requestKey ?: "$upperMethod $path"
        } else {
            null
        }
        if (cancelKey != null) {
            cancelRequest(cancelKey)
        }

        val client = if (finalOptions.timeoutSeconds != null) {
            httpClient.newBuilder()
                .callTimeout(finalOptions.timeoutSeconds!!, TimeUnit.SECONDS)
                .build()
        } else {
            httpClient
        }

        val call = client.newCall(requestBuilder.build())
        if (cancelKey != null) {
            cancelCalls[cancelKey] = call
        }

        val response = try {
            call.execute()
        } catch (io: IOException) {
            throw ClientResponseError(
                url = targetUrl.toString(),
                originalError = io,
                isAbort = io is java.io.InterruptedIOException,
            )
        } finally {
            if (cancelKey != null) {
                cancelCalls.remove(cancelKey)
            }
        }

        response.use { resp ->
            val status = resp.code
            val contentType = resp.header("Content-Type")?.lowercase(Locale.US) ?: ""
            val rawBody = resp.body?.string().orEmpty()
            val data: JsonElement? = if (status == 204 || rawBody.isEmpty()) {
                null
            } else if (contentType.contains("application/json")) {
                try {
                    json.parseToJsonElement(rawBody)
                } catch (_: Exception) {
                    JsonPrimitive(rawBody)
                }
            } else {
                JsonPrimitive(rawBody)
            }

            if (status >= 400) {
                throw ClientResponseError(
                    url = targetUrl.toString(),
                    status = status,
                    response = jsonElementToMap(data),
                )
            }

            val result = afterSend?.invoke(resp, data, finalOptions) ?: data
            return result
        }
    }

    private fun buildRequestBody(
        body: Any?,
        files: Map<String, List<FileAttachment>>?,
    ): RequestBody? {
        val payloadElement: JsonElement? = when (body) {
            null -> null
            is JsonElement -> body
            else -> toJsonElement(body)
        }

        return if (!files.isNullOrEmpty()) {
            val multipart = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
            if (payloadElement != null) {
                val jsonString = json.encodeToString(JsonElement.serializer(), payloadElement)
                multipart.addFormDataPart("@jsonPayload", jsonString)
            }
            files.forEach { (key, attachments) ->
                attachments.forEachIndexed { idx, attachment ->
                    val partKey = if (attachments.size > 1) "$key[$idx]" else key
                    val partBody = attachment.bytes.toRequestBody(attachment.mediaType)
                    multipart.addFormDataPart(partKey, attachment.filename, partBody)
                }
            }
            multipart.build()
        } else if (payloadElement != null) {
            val jsonString = json.encodeToString(JsonElement.serializer(), payloadElement)
            jsonString.toRequestBody(MEDIA_TYPE_JSON)
        } else {
            null
        }
    }

    private fun applyBeforeSend(url: String, options: RequestOptions): Pair<RequestOptions, String?> {
        val override = beforeSend?.invoke(url, options) ?: return options to null

        var targetUrl: String? = override.url
        if (override.url != null) {
            val parsed = override.url.toHttpUrlOrNull()
            if (parsed != null) {
                val rebuiltQuery = parsed.queryParameterNames.associateWith { name ->
                    val values = parsed.queryParameterValues(name)
                    if (values.size == 1) values.first() else values
                }
                options.query = rebuiltQuery.toMutableMap()
            }
        }

        return (override.options ?: options) to targetUrl
    }

    private fun normalizeRequestKey(options: RequestOptions) {
        val autoCancelFlag = options.query.remove("\$autoCancel")?.toString()
        val cancelKey = options.query.remove("\$cancelKey")?.toString()

        val params = options.query.remove("params")
        if (params is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            options.query.putAll(params as Map<String, Any?>)
        }

        if (autoCancelFlag?.lowercase(Locale.US) == "false") {
            options.autoCancel = false
            options.requestKey = options.requestKey // explicit no-op to emphasize disabled auto-cancel
            return
        }

        if (options.requestKey == null && !cancelKey.isNullOrBlank()) {
            options.requestKey = cancelKey
        }
    }

    private fun syncAuthorizationHeader(options: RequestOptions, previousToken: String?) {
        if (previousToken.isNullOrBlank()) return
        val headerKey = options.headers.keys.firstOrNull { it.equals("authorization", ignoreCase = true) }
        if (headerKey != null && options.headers[headerKey] == previousToken && authStore.token != null) {
            options.headers[headerKey] = authStore.token!!
        }
    }

    private fun isTokenExpiring(token: String?, thresholdSeconds: Long): Boolean {
        val exp = getTokenExp(token) ?: return true
        val threshold = if (thresholdSeconds < 0) 0 else thresholdSeconds
        val now = System.currentTimeMillis() / 1000
        return now >= exp - threshold
    }

    private fun getTokenExp(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = Base64.getUrlDecoder().decode(padBase64(parts[1]))
            val obj = json.parseToJsonElement(String(payload)) as? JsonObject
            obj?.get("exp")?.jsonPrimitive?.longOrNull
        } catch (_: Exception) {
            null
        }
    }

    private fun padBase64(raw: String): String {
        val padding = (4 - raw.length % 4) % 4
        return raw + "=".repeat(padding)
    }
}

private fun appendQuery(builder: HttpUrl.Builder, key: String, value: Any?) {
    when (value) {
        null -> {}
        is Iterable<*> -> value.forEach { appendQuery(builder, key, it) }
        is Array<*> -> value.forEach { appendQuery(builder, key, it) }
        else -> builder.addQueryParameter(key, value.toString())
    }
}

private fun requiresRequestBody(method: String): Boolean {
    return method in setOf("POST", "PUT", "PATCH", "DELETE")
}
