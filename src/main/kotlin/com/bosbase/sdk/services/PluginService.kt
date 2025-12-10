package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.USER_AGENT
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PluginRequestOptions(
    val headers: Map<String, String>? = null,
    val query: Map<String, Any?>? = null,
    val body: Any? = null,
    val files: Map<String, List<com.bosbase.sdk.FileAttachment>>? = null,
    val timeoutSeconds: Long? = null,
    val requestKey: String? = null,
    val autoCancel: Boolean = true,
    val websocketProtocols: List<String>? = null,
)

class PluginService(private val clientRef: BosBase) : BaseService(clientRef) {
    private val httpMethods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    private val wsMethods = setOf("WS", "WEBSOCKET")

    @Suppress("UNCHECKED_CAST")
    fun <T> request(
        method: String,
        path: String,
        options: PluginRequestOptions? = null,
        sseListener: EventSourceListener? = null,
        webSocketListener: WebSocketListener? = null,
    ): T {
        val normalized = method.trim().uppercase(Locale.US)
        return when {
            normalized == "SSE" -> {
                val listener = sseListener ?: throw IllegalArgumentException("sseListener is required for SSE plugin calls")
                sse(path, listener, options) as T
            }

            wsMethods.contains(normalized) -> {
                val listener = webSocketListener ?: object : WebSocketListener() {}
                websocket(path, listener, options) as T
            }

            httpMethods.contains(normalized) -> clientRef.send(
                normalizePath(path),
                method = normalized,
                headers = options?.headers,
                query = options?.query,
                body = options?.body,
                files = options?.files,
                timeoutSeconds = options?.timeoutSeconds,
                requestKey = options?.requestKey,
                autoCancel = options?.autoCancel ?: true,
            ) as T

            else -> throw IllegalArgumentException(
                "Unsupported plugin method \"$method\". Expected one of ${httpMethods + setOf("SSE") + wsMethods}.",
            )
        }
    }

    fun sse(
        path: String,
        listener: EventSourceListener,
        options: PluginRequestOptions? = null,
    ): EventSource {
        val url = buildUrl(normalizePath(path), options?.query, includeToken = true)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-store")
            .header("Accept-Language", clientRef.lang)
            .header("User-Agent", USER_AGENT)

        if (clientRef.authStore.isValid) {
            clientRef.authStore.token?.let { requestBuilder.header("Authorization", it) }
        }
        options?.headers?.forEach { (key, value) -> requestBuilder.header(key, value) }

        val client = options?.timeoutSeconds?.let {
            clientRef.httpClient.newBuilder().callTimeout(it, TimeUnit.SECONDS).build()
        } ?: clientRef.httpClient

        return EventSources.createFactory(client).newEventSource(requestBuilder.build(), listener)
    }

    fun websocket(
        path: String,
        listener: WebSocketListener,
        options: PluginRequestOptions? = null,
    ): WebSocket {
        val httpUrl = buildUrl(normalizePath(path), options?.query, includeToken = true)
        val wsScheme = when (httpUrl.scheme.lowercase(Locale.US)) {
            "https" -> "wss"
            "http" -> "ws"
            else -> httpUrl.scheme
        }
        val wsUrl = httpUrl.newBuilder().scheme(wsScheme).build()
        val requestBuilder = Request.Builder()
            .url(wsUrl)
            .header("Accept-Language", clientRef.lang)
            .header("User-Agent", USER_AGENT)

        if (clientRef.authStore.isValid) {
            clientRef.authStore.token?.let { token ->
                val hasAuthHeader = options?.headers?.keys?.any { it.equals("authorization", ignoreCase = true) } == true
                if (!hasAuthHeader) {
                    requestBuilder.header("Authorization", token)
                }
            }
        }
        options?.headers?.forEach { (key, value) -> requestBuilder.header(key, value) }
        options?.websocketProtocols?.takeIf { it.isNotEmpty() }
            ?.let { requestBuilder.header("Sec-WebSocket-Protocol", it.joinToString(",")) }

        val client = options?.timeoutSeconds?.let {
            clientRef.httpClient.newBuilder().callTimeout(it, TimeUnit.SECONDS).build()
        } ?: clientRef.httpClient

        return client.newWebSocket(requestBuilder.build(), listener)
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim().trimStart('/')
        return if (trimmed.isEmpty()) {
            "/api/plugins"
        } else if (trimmed.startsWith("api/plugins")) {
            "/${trimmed}"
        } else {
            "/api/plugins/$trimmed"
        }
    }

    private fun buildUrl(path: String, query: Map<String, Any?>?, includeToken: Boolean): HttpUrl {
        val params = mutableMapOf<String, Any?>()
        if (query != null) params.putAll(query)

        if (includeToken && clientRef.authStore.token != null) {
            params.putIfAbsent("token", clientRef.authStore.token!!)
        }

        return clientRef.buildUrl(path, params)
    }
}
