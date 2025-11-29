package com.bosbase.sdk.services

import com.bosbase.sdk.AuthStore
import com.bosbase.sdk.BaseAuthStore
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError
import com.bosbase.sdk.FileAttachment
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RecordService(
    client: BosBase,
    private val collectionIdOrName: String,
) : BaseCrudService(client) {

    private val authStore: BaseAuthStore
        get() = client.authStore

    private val baseCollectionPath: String
        get() = "/api/collections/${encodePath(collectionIdOrName)}"

    override val baseCrudPath: String
        get() = "$baseCollectionPath/records"

    private val isSuperusers: Boolean
        get() = collectionIdOrName == "_superusers" || collectionIdOrName == "_pbc_2773867675"

    private data class OAuth2ProviderInfo(
        val name: String,
        val authURL: String,
        val codeVerifier: String,
    )

    fun subscribe(
        topic: String,
        callback: (Map<String, Any?>) -> Unit,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): () -> Unit {
        if (topic.isBlank()) throw IllegalArgumentException("Missing topic.")
        return client.realtime.subscribe("${collectionIdOrName}/$topic", callback, query, headers)
    }

    fun unsubscribe(topic: String? = null) {
        if (topic != null) {
            client.realtime.unsubscribe("${collectionIdOrName}/$topic")
        } else {
            client.realtime.unsubscribeByPrefix(collectionIdOrName)
        }
    }

    fun getCount(
        filter: String? = null,
        expand: String? = null,
        fields: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): Int {
        val params = mutableMapOf<String, Any?>()
        if (filter != null) params["filter"] = filter
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)

        val data = client.send("$baseCrudPath/count", query = params, headers = headers)
        return (data as? JsonObject)
            ?.get("count")
            ?.jsonPrimitive
            ?.intOrNull ?: 0
    }

    fun listAuthMethods(
        fields: String? = "mfa,otp,password,oauth2",
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        requestKey: String? = null,
    ): JsonObject {
        val params = mutableMapOf<String, Any?>()
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)
        val data = client.send(
            "$baseCollectionPath/auth-methods",
            query = params,
            headers = headers,
            requestKey = requestKey,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    fun authWithPassword(
        identity: String,
        password: String,
        expand: String? = null,
        fields: String? = null,
        mfaId: String? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        autoRefreshThresholdSeconds: Long? = null,
        requestKey: String? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("identity" to identity, "password" to password)
        if (body != null) payload.putAll(body)

        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (mfaId != null) params["mfaId"] = mfaId
        if (query != null) params.putAll(query)

        val data = client.send(
            "$baseCollectionPath/auth-with-password",
            method = "POST",
            body = payload,
            query = params,
            headers = headers,
            requestKey = requestKey,
        )
        val authData = authResponse(data)

        if (autoRefreshThresholdSeconds != null && isSuperusers) {
            val refreshQuery = mutableMapOf<String, Any?>("autoRefresh" to true)
            if (query != null) refreshQuery.putAll(query)

            client.registerAutoRefresh(
                autoRefreshThresholdSeconds,
                { authRefresh(query = refreshQuery, headers = headers) },
                {
                    val reauthQuery = mutableMapOf<String, Any?>("autoRefresh" to true)
                    if (query != null) reauthQuery.putAll(query)
                    if (mfaId != null) reauthQuery["mfaId"] = mfaId
                    authWithPassword(
                        identity,
                        password,
                        expand,
                        fields,
                        mfaId,
                        body,
                        reauthQuery,
                        headers,
                        null,
                        requestKey,
                    )
                },
            )
        }

        return authData
    }

    fun bindCustomToken(
        email: String,
        password: String,
        token: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        requestKey: String? = null,
    ): Boolean {
        val payload = mutableMapOf<String, Any?>(
            "email" to email,
            "password" to password,
            "token" to token,
        )
        if (body != null) payload.putAll(body)

        client.send(
            "$baseCollectionPath/bind-token",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
            requestKey = requestKey,
        )
        return true
    }

    fun unbindCustomToken(
        email: String,
        password: String,
        token: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        requestKey: String? = null,
    ): Boolean {
        val payload = mutableMapOf<String, Any?>(
            "email" to email,
            "password" to password,
            "token" to token,
        )
        if (body != null) payload.putAll(body)

        client.send(
            "$baseCollectionPath/unbind-token",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
            requestKey = requestKey,
        )
        return true
    }

    fun authWithToken(
        token: String,
        expand: String? = null,
        fields: String? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        autoRefreshThresholdSeconds: Long? = null,
        requestKey: String? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("token" to token)
        if (body != null) payload.putAll(body)

        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)

        val data = client.send(
            "$baseCollectionPath/auth-with-token",
            method = "POST",
            body = payload,
            query = params,
            headers = headers,
            requestKey = requestKey,
        )
        val authData = authResponse(data)

        if (autoRefreshThresholdSeconds != null && isSuperusers) {
            val refreshQuery = mutableMapOf<String, Any?>("autoRefresh" to true)
            if (query != null) refreshQuery.putAll(query)

            client.registerAutoRefresh(
                autoRefreshThresholdSeconds,
                { authRefresh(query = refreshQuery, headers = headers) },
                {
                    val reauthQuery = mutableMapOf<String, Any?>("autoRefresh" to true)
                    if (query != null) reauthQuery.putAll(query)
                    authWithToken(
                        token,
                        expand,
                        fields,
                        body,
                        reauthQuery,
                        headers,
                        null,
                        requestKey,
                    )
                },
            )
        }

        return authData
    }

    fun authRefresh(
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val data = client.send(
            "$baseCollectionPath/auth-refresh",
            method = "POST",
            body = body ?: emptyMap<String, Any?>(),
            query = query,
            headers = headers,
        )
        return authResponse(data)
    }

    fun requestOtp(
        email: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("email" to email)
        if (body != null) payload.putAll(body)
        val data = client.send(
            "$baseCollectionPath/request-otp",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    fun authWithOtp(
        otpId: String,
        otp: String,
        mfaId: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        requestKey: String? = null,
    ): JsonObject {
        val payload = mapOf("otpId" to otpId, "otp" to otp)
        val params = mutableMapOf<String, Any?>()
        if (mfaId != null) params["mfaId"] = mfaId
        if (query != null) params.putAll(query)
        val data = client.send(
            "$baseCollectionPath/auth-with-otp",
            method = "POST",
            body = payload,
            query = params,
            headers = headers,
            requestKey = requestKey,
        )
        return authResponse(data)
    }

    fun authWithOAuth2Code(
        provider: String,
        code: String,
        codeVerifier: String,
        redirectURL: String,
        createData: Map<String, Any?>? = null,
        expand: String? = null,
        fields: String? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        mfaId: String? = null,
        timeoutSeconds: Long? = null,
        requestKey: String? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>(
            "provider" to provider,
            "code" to code,
            "codeVerifier" to codeVerifier,
            "redirectURL" to redirectURL,
        )
        if (createData != null) payload["createData"] = createData
        if (body != null) payload.putAll(body)

        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (mfaId != null) params["mfaId"] = mfaId
        if (query != null) params.putAll(query)

        val data = client.send(
            "$baseCollectionPath/auth-with-oauth2",
            method = "POST",
            body = payload,
            query = params,
            headers = headers,
            timeoutSeconds = timeoutSeconds,
            requestKey = requestKey,
        )
        return authResponse(data)
    }

    fun authWithOAuth2(
        provider: String,
        urlCallback: (String) -> Unit,
        scopes: List<String>? = null,
        createData: Map<String, Any?>? = null,
        expand: String? = null,
        fields: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        mfaId: String? = null,
        timeoutSeconds: Long? = null,
        requestKey: String? = null,
    ): JsonObject {
        val authMethods = listAuthMethods(query = query, headers = headers, requestKey = requestKey)
        val providerInfo = findOAuthProvider(authMethods, provider)
            ?: throw ClientResponseError(
                url = client.buildUrl("$baseCollectionPath/auth-methods").toString(),
                originalError = IllegalArgumentException("Missing or invalid provider \"$provider\"."),
            )

        val realtime = RealtimeService(client)
        var unsubscribe: (() -> Unit)? = null
        val result = CompletableFuture<JsonObject>()
        val cleanup = {
            unsubscribe?.let { runCatching { it() } }
            realtime.unsubscribe()
        }

        unsubscribe = realtime.subscribe("@oauth2", { event ->
            val state = asString(event["state"])
            val code = asString(event["code"])
            val error = asString(event["error"])
            try {
                if (state.isNullOrBlank() || state != realtime.clientId) {
                    throw ClientResponseError(
                        url = client.buildUrl("$baseCollectionPath/auth-with-oauth2").toString(),
                        originalError = IllegalStateException("State parameters don't match."),
                    )
                }

                if (!error.isNullOrBlank() || code.isNullOrBlank()) {
                    throw ClientResponseError(
                        url = client.buildUrl("$baseCollectionPath/auth-with-oauth2").toString(),
                        originalError = IllegalStateException(
                            "OAuth2 redirect error or missing code: ${error.orEmpty()}",
                        ),
                    )
                }

                val params = mutableMapOf<String, Any?>()
                if (query != null) params.putAll(query)
                if (mfaId != null) params["mfaId"] = mfaId

                val authData = authWithOAuth2Code(
                    providerInfo.name,
                    code,
                    providerInfo.codeVerifier,
                    client.buildUrl("/api/oauth2-redirect").toString(),
                    createData,
                    expand,
                    fields,
                    null,
                    params,
                    headers,
                    mfaId,
                    timeoutSeconds,
                    requestKey,
                )

                result.complete(authData)
            } catch (err: Exception) {
                result.completeExceptionally(err)
            } finally {
                cleanup()
            }
        })

        if (!waitForRealtimeClientId(realtime, timeoutSeconds)) {
            cleanup()
            throw ClientResponseError(
                url = client.buildUrl("$baseCollectionPath/auth-with-oauth2").toString(),
                originalError = IllegalStateException("Failed to initialize realtime OAuth2 session."),
            )
        }

        val oauthUrl = buildOAuthUrl(providerInfo.authURL, realtime.clientId, scopes, client.buildUrl("/api/oauth2-redirect").toString())
        try {
            urlCallback(oauthUrl)
        } catch (err: Exception) {
            cleanup()
            throw err
        }

        return try {
            val waitFor = timeoutSeconds ?: 120
            result.get(TimeUnit.SECONDS.toMillis(waitFor), TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            throw ClientResponseError(
                url = client.buildUrl("$baseCollectionPath/auth-with-oauth2").toString(),
                originalError = timeout,
            )
        } finally {
            cleanup()
        }
    }

    fun requestPasswordReset(
        email: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("email" to email)
        if (body != null) payload.putAll(body)
        val data = client.send(
            "$baseCollectionPath/request-password-reset",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    fun confirmPasswordReset(
        token: String,
        password: String,
        passwordConfirm: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>(
            "token" to token,
            "password" to password,
            "passwordConfirm" to passwordConfirm,
        )
        if (body != null) payload.putAll(body)
        val data = client.send(
            "$baseCollectionPath/confirm-password-reset",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return authResponse(data)
    }

    fun requestVerification(
        email: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("email" to email)
        if (body != null) payload.putAll(body)
        val data = client.send(
            "$baseCollectionPath/request-verification",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    fun confirmVerification(
        token: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mapOf("token" to token)
        val data = client.send(
            "$baseCollectionPath/confirm-verification",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return authResponse(data)
    }

    fun requestEmailChange(
        newEmail: String,
        oldEmail: String? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("newEmail" to newEmail)
        if (oldEmail != null) payload["oldEmail"] = oldEmail
        if (body != null) payload.putAll(body)
        val data = client.send(
            "$baseCollectionPath/request-email-change",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    fun confirmEmailChange(
        token: String,
        password: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val payload = mutableMapOf<String, Any?>("token" to token)
        if (password != null) payload["password"] = password
        val data = client.send(
            "$baseCollectionPath/confirm-email-change",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        )
        return authResponse(data)
    }

    fun impersonate(
        recordId: String,
        durationSeconds: Int? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): BosBase {
        val params = mutableMapOf<String, Any?>()
        if (durationSeconds != null) params["duration"] = durationSeconds
        if (query != null) params.putAll(query)

        val headerMap = headers?.toMutableMap() ?: mutableMapOf()
        val hasAuthHeader = headerMap.keys.any { it.equals("authorization", ignoreCase = true) }
        if (!hasAuthHeader) {
            client.authStore.token?.let { headerMap["Authorization"] = it }
        }

        val data = client.send(
            "$baseCollectionPath/impersonate/${encodePath(recordId)}",
            method = "POST",
            query = params,
            headers = headerMap,
        ) as? JsonObject ?: JsonObject(emptyMap())

        val token = data["token"]?.jsonPrimitive?.contentOrNull
        val record = data["record"] as? JsonObject
        val impersonated = BosBase(client.baseUrl, client.lang, AuthStore(), client.httpClient)
        if (token != null) {
            impersonated.authStore.save(token, record)
        }
        return impersonated
    }

    override fun update(
        id: String,
        body: Map<String, Any?>,
        files: Map<String, List<FileAttachment>>?,
        query: Map<String, Any?>?,
        headers: Map<String, String>?,
    ): JsonObject {
        val item = super.update(id, body, files, query, headers)
        maybeUpdateAuthRecord(id, item)
        return item
    }

    override fun delete(
        id: String,
        query: Map<String, Any?>?,
        headers: Map<String, String>?,
    ) {
        super.delete(id, query, headers)
        if (isAuthRecord(id)) {
            authStore.clear()
        }
    }

    private fun findOAuthProvider(authMethods: JsonObject, providerName: String): OAuth2ProviderInfo? {
        val oauth = authMethods["oauth2"] as? JsonObject ?: return null
        val providers = oauth["providers"] as? JsonArray ?: return null
        val provider = providers.firstOrNull {
            (it as? JsonObject)
                ?.get("name")
                ?.jsonPrimitive
                ?.contentOrNull == providerName
        } as? JsonObject ?: return null

        val authURL = provider["authURL"]?.jsonPrimitive?.contentOrNull
        val codeVerifier = provider["codeVerifier"]?.jsonPrimitive?.contentOrNull
        if (authURL.isNullOrBlank() || codeVerifier.isNullOrBlank()) {
            return null
        }

        return OAuth2ProviderInfo(
            name = providerName,
            authURL = authURL,
            codeVerifier = codeVerifier,
        )
    }

    private fun asString(value: Any?): String? {
        return when (value) {
            is JsonPrimitive -> value.contentOrNull
            else -> value?.toString()?.trim('"')
        }
    }

    private fun waitForRealtimeClientId(realtime: RealtimeService, timeoutSeconds: Long?): Boolean {
        val timeoutMs = TimeUnit.SECONDS.toMillis(timeoutSeconds ?: 15)
        val start = System.currentTimeMillis()
        while (realtime.clientId.isBlank() && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(25)
        }
        return realtime.clientId.isNotBlank()
    }

    private fun buildOAuthUrl(
        authUrl: String,
        state: String,
        scopes: List<String>?,
        redirectUrl: String,
    ): String {
        val combined = authUrl + redirectUrl
        val parsed = combined.toHttpUrlOrNull()
        if (parsed != null) {
            val builder = parsed.newBuilder()
            builder.setQueryParameter("state", state)
            if (!scopes.isNullOrEmpty()) {
                builder.setQueryParameter("scope", scopes.joinToString(" "))
            }
            return builder.build().toString()
        }

        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8.toString())
        val scopePart = if (scopes.isNullOrEmpty()) {
            ""
        } else {
            "&scope=" + URLEncoder.encode(scopes.joinToString(" "), StandardCharsets.UTF_8.toString())
        }
        val separator = if (combined.contains("?")) "&" else "?"
        return "$combined${separator}state=$encodedState$scopePart"
    }

    private fun authResponse(data: Any?): JsonObject {
        val obj = data as? JsonObject ?: JsonObject(emptyMap())
        val token = obj["token"]?.jsonPrimitive?.contentOrNull
        val record = obj["record"] as? JsonObject
        if (token != null) {
            authStore.save(token, record)
        }
        return obj
    }

    private fun isAuthRecord(recordId: String): Boolean {
        val currentId = authStore.model?.get("id")?.jsonPrimitive?.contentOrNull
        return currentId != null && currentId == recordId
    }

    private fun maybeUpdateAuthRecord(recordId: String, newRecord: JsonObject) {
        if (isAuthRecord(recordId)) {
            authStore.token?.let { authStore.save(it, newRecord) }
        }
    }
}
