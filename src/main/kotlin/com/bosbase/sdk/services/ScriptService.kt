package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.FileAttachment
import com.bosbase.sdk.USER_AGENT
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

data class ScriptExecuteParams(
    val arguments: List<Any>? = null,
    val functionName: String? = null,
)

class ScriptService(private val clientRef: BosBase) : BaseService(clientRef) {
    private val basePath = "/api/scripts"

    fun create(
        name: String,
        content: String,
        description: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmedName = name.trim()
        val trimmedContent = content.trim()
        if (trimmedName.isEmpty()) throw IllegalArgumentException("script name is required")
        if (trimmedContent.isEmpty()) throw IllegalArgumentException("script content is required")

        val payload = mutableMapOf<String, Any?>(
            "name" to trimmedName,
            "content" to trimmedContent,
        )
        if (!description.isNullOrBlank()) {
            payload["description"] = description
        }

        return clientRef.send(basePath, method = "POST", body = payload, query = query, headers = headers) as? JsonObject
    }

    fun command(
        command: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmed = command.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("command is required")

        return clientRef.send(
            "$basePath/command",
            method = "POST",
            body = mapOf("command" to trimmed),
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun commandAsync(
        command: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("command is required")

        return clientRef.send(
            "$basePath/command",
            method = "POST",
            body = mapOf("command" to trimmed, "async" to true),
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun commandStatus(
        jobId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()
        val trimmed = jobId.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("command id is required")
        return clientRef.send("$basePath/command/${encodePath(trimmed)}", query = query, headers = headers) as? JsonObject
    }

    fun upload(
        file: FileAttachment,
        path: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val payload = mutableMapOf<String, Any?>()
        if (!path.isNullOrBlank()) {
            payload["path"] = path
        }

        return clientRef.send(
            "$basePath/upload",
            method = "POST",
            body = payload,
            files = mapOf("file" to listOf(file)),
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun get(
        name: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        return clientRef.send("$basePath/${encodePath(trimmed)}", query = query, headers = headers) as? JsonObject
    }

    fun list(
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): List<JsonObject> {
        requireSuperuser()
        val data = clientRef.send(basePath, query = query, headers = headers)
        return (data as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    fun update(
        name: String,
        content: String? = null,
        description: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) throw IllegalArgumentException("script name is required")
        if (content == null && description == null) {
            throw IllegalArgumentException("at least one of content or description must be provided")
        }

        val payload = mutableMapOf<String, Any?>()
        content?.let { payload["content"] = it }
        description?.let { payload["description"] = it }

        return clientRef.send(
            "$basePath/${encodePath(trimmedName)}",
            method = "PATCH",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun execute(
        name: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        return executeInternal(trimmed, null, query, headers)
    }

    fun execute(
        name: String,
        args: List<Any>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        val payload = mapOf("arguments" to normalizeArguments(args))
        return executeInternal(trimmed, payload, query, headers)
    }

    fun execute(
        name: String,
        params: ScriptExecuteParams,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        val payload = buildExecutePayload(params)
        return executeInternal(trimmed, payload, query, headers)
    }

    fun executeSse(
        name: String,
        listener: EventSourceListener,
        params: ScriptExecuteParams? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        timeoutSeconds: Long? = null,
    ): EventSource {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        val url = buildExecuteUrl("$basePath/${encodePath(trimmed)}/execute/sse", params, query, includeToken = true)
        val requestBuilder = Request.Builder()
            .url(url.toString())
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-store")
            .header("Accept-Language", clientRef.lang)
            .header("User-Agent", USER_AGENT)

        if (clientRef.authStore.isValid) {
            clientRef.authStore.token?.let { requestBuilder.header("Authorization", it) }
        }
        headers?.forEach { (key, value) -> requestBuilder.header(key, value) }

        val client = timeoutSeconds?.let {
            clientRef.httpClient.newBuilder().callTimeout(it, TimeUnit.SECONDS).build()
        } ?: clientRef.httpClient

        return EventSources.createFactory(client).newEventSource(requestBuilder.build(), listener)
    }

    fun executeWebSocket(
        name: String,
        listener: WebSocketListener,
        params: ScriptExecuteParams? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
        timeoutSeconds: Long? = null,
        websocketProtocols: List<String>? = null,
    ): WebSocket {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        val httpUrl = buildExecuteUrl("$basePath/${encodePath(trimmed)}/execute/ws", params, query, includeToken = true)
        val wsScheme = if (httpUrl.scheme == "https") "wss" else "ws"
        val wsUrl = httpUrl.newBuilder().scheme(wsScheme).build()

        val requestBuilder = Request.Builder()
            .url(wsUrl)
            .header("Accept-Language", clientRef.lang)
            .header("User-Agent", USER_AGENT)

        val headerMap = headers?.toMutableMap() ?: mutableMapOf()
        val hasAuthHeader = headerMap.keys.any { it.equals("authorization", ignoreCase = true) }
        if (!hasAuthHeader && clientRef.authStore.isValid) {
            clientRef.authStore.token?.let { headerMap["Authorization"] = it }
        }
        headerMap.forEach { (key, value) -> requestBuilder.header(key, value) }

        websocketProtocols?.takeIf { it.isNotEmpty() }
            ?.let { requestBuilder.header("Sec-WebSocket-Protocol", it.joinToString(",")) }

        val client = timeoutSeconds?.let {
            clientRef.httpClient.newBuilder().callTimeout(it, TimeUnit.SECONDS).build()
        } ?: clientRef.httpClient

        return client.newWebSocket(requestBuilder.build(), listener)
    }

    fun executeAsync(
        name: String,
        params: ScriptExecuteParams? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        val payload = params?.let { buildExecutePayload(it) }
        return clientRef.send(
            "$basePath/async/${encodePath(trimmed)}/execute",
            method = "POST",
            body = payload ?: emptyMap<String, Any?>(),
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun executeAsyncStatus(
        jobId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = jobId.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("execution job id is required")
        return clientRef.send("$basePath/async/${encodePath(trimmed)}", query = query, headers = headers) as? JsonObject
    }

    fun wasm(
        cliOptions: String,
        wasmName: String,
        params: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = wasmName.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("wasm name is required")

        val payload = mapOf(
            "options" to cliOptions.trim(),
            "wasm" to trimmed,
            "params" to (params?.trim() ?: ""),
        )

        return clientRef.send(
            "$basePath/wasm",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun wasmAsync(
        cliOptions: String,
        wasmName: String,
        params: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = wasmName.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("wasm name is required")

        val payload = mapOf(
            "options" to cliOptions.trim(),
            "wasm" to trimmed,
            "params" to (params?.trim() ?: ""),
        )

        return clientRef.send(
            "$basePath/wasm/async",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun wasmAsyncStatus(
        jobId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val trimmed = jobId.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("wasm execution job id is required")
        return clientRef.send("$basePath/wasm/async/${encodePath(trimmed)}", query = query, headers = headers) as? JsonObject
    }

    fun delete(
        name: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): Boolean {
        requireSuperuser()

        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")
        clientRef.send("$basePath/${encodePath(trimmed)}", method = "DELETE", query = query, headers = headers)
        return true
    }

    private fun requireSuperuser() {
        if (!clientRef.authStore.isSuperuser) {
            throw IllegalStateException("Superuser authentication is required to manage scripts")
        }
    }

    private fun normalizeArguments(args: List<Any>): List<String> =
        args.map { arg -> if (arg is String) arg else arg.toString() }

    private fun buildExecutePayload(params: ScriptExecuteParams): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>()
        if (!params.arguments.isNullOrEmpty()) {
            payload["arguments"] = normalizeArguments(params.arguments)
        }
        if (!params.functionName.isNullOrBlank()) {
            payload["function_name"] = params.functionName
        }
        return payload
    }

    private fun executeInternal(
        trimmedName: String,
        body: Map<String, Any?>?,
        query: Map<String, Any?>?,
        headers: Map<String, String>?,
    ): JsonObject? {
        return clientRef.send(
            "$basePath/${encodePath(trimmedName)}/execute",
            method = "POST",
            body = body ?: emptyMap<String, Any?>(),
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    private fun buildExecuteUrl(
        path: String,
        params: ScriptExecuteParams?,
        query: Map<String, Any?>?,
        includeToken: Boolean,
    ): okhttp3.HttpUrl {
        val merged = mutableMapOf<String, Any?>()
        if (query != null) merged.putAll(query)
        if (!params?.arguments.isNullOrEmpty()) {
            merged["arguments"] = normalizeArguments(params?.arguments ?: emptyList())
        }
        if (!params?.functionName.isNullOrBlank()) {
            merged["function_name"] = params?.functionName
        }
        if (includeToken && clientRef.authStore.token != null && !merged.containsKey("token")) {
            merged["token"] = clientRef.authStore.token
        }
        return clientRef.buildUrl(path, merged)
    }
}
