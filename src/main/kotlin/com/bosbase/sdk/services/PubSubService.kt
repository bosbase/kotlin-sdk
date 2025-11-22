package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError
import com.bosbase.sdk.jsonElementToMap
import com.bosbase.sdk.toJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class PubSubMessage<T>(
    val id: String,
    val topic: String,
    val created: String,
    val data: T,
)

data class PublishAck(
    val id: String,
    val topic: String,
    val created: String,
)

private data class PendingAck(
    val resolve: (Map<String, Any?>) -> Unit,
    val reject: (Throwable) -> Unit,
    val timeout: ScheduledFuture<*>?,
)

class PubSubService(client: BosBase) : BaseService(client) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "bosbase-pubsub") }

    private val pendingConnects = mutableListOf<CompletableFuture<Void>>()
    private val pendingAcks = ConcurrentHashMap<String, PendingAck>()
    private val subscriptions =
        ConcurrentHashMap<String, MutableSet<(PubSubMessage<Any?>) -> Unit>>()

    private val predefinedReconnectIntervals =
        listOf(200L, 300L, 500L, 1000L, 1200L, 1500L, 2000L)
    private val ackTimeoutMs = 10_000L
    private val maxConnectTimeout = 15_000L

    private var socket: WebSocket? = null
    private var connectTimeout: ScheduledFuture<*>? = null
    private var reconnectTimeout: ScheduledFuture<*>? = null
    private var reconnectAttempts = 0
    private var manualClose = false
    private var isReady = false
    private var clientId: String = ""
    private val lock = Any()

    val isConnected: Boolean
        get() = synchronized(lock) { socket != null && isReady }

    fun publish(topic: String, data: Any?): PublishAck {
        if (topic.isBlank()) throw IllegalArgumentException("topic must be set.")

        ensureSocket().get()

        val requestId = nextRequestId()
        val ackFuture = waitForAck(requestId) { payload ->
            PublishAck(
                id = payload["id"]?.toString().orEmpty(),
                topic = payload["topic"]?.toString()?.ifBlank { topic } ?: topic,
                created = payload["created"]?.toString().orEmpty(),
            )
        }

        sendEnvelope(
            mapOf(
                "type" to "publish",
                "topic" to topic,
                "data" to data,
                "requestId" to requestId,
            ),
        )

        return ackFuture.get()
    }

    fun subscribe(
        topic: String,
        callback: (PubSubMessage<Any?>) -> Unit,
    ): () -> Unit {
        if (topic.isBlank()) throw IllegalArgumentException("topic must be set.")

        val isFirstListener: Boolean
        synchronized(lock) {
            val listeners = subscriptions.getOrPut(topic) { mutableSetOf() }
            isFirstListener = listeners.isEmpty()
            listeners.add(callback)
        }

        ensureSocket().get()

        if (isFirstListener) {
            val requestId = nextRequestId()
            val ackFuture = waitForAck(requestId) { true as Boolean }.exceptionally { null }
            sendEnvelope(
                mapOf(
                    "type" to "subscribe",
                    "topic" to topic,
                    "requestId" to requestId,
                ),
            )
            ackFuture.get()
        }

        return {
            var shouldDisconnect = false
            var shouldUnsubscribe = false
            synchronized(lock) {
                subscriptions[topic]?.remove(callback)
                if (subscriptions[topic]?.isEmpty() == true) {
                    subscriptions.remove(topic)
                    shouldUnsubscribe = true
                    shouldDisconnect = subscriptions.isEmpty()
                }
            }

            if (shouldUnsubscribe) {
                try {
                    sendUnsubscribe(topic)
                } catch (_: Exception) {
                }
            }

            if (shouldDisconnect) {
                disconnect()
            }
        }
    }

    fun unsubscribe(topic: String? = null) {
        val topicsToRemove: List<String>
        synchronized(lock) {
            topicsToRemove = if (topic == null) {
                subscriptions.keys.toList()
            } else {
                subscriptions.keys.filter { it == topic }
            }
            topicsToRemove.forEach { subscriptions.remove(it) }
        }

        if (topicsToRemove.isEmpty()) {
            return
        }

        if (topic == null) {
            sendEnvelope(mapOf("type" to "unsubscribe"))
            disconnect()
            return
        }

        sendUnsubscribe(topic)
        if (!hasSubscriptions()) {
            disconnect()
        }
    }

    fun disconnect() {
        manualClose = true
        rejectAllPending(RuntimeException("pubsub connection closed"))
        closeSocket()
        synchronized(lock) {
            pendingConnects.clear()
        }
    }

    private fun hasSubscriptions(): Boolean = synchronized(lock) { subscriptions.isNotEmpty() }

    private fun buildWebSocketURL(): String {
        val query = mutableMapOf<String, Any?>()
        client.authStore.token?.let { query["token"] = it }

        val httpUrl = client.buildUrl("/api/pubsub", query)
        val scheme = if (httpUrl.scheme == "https") "wss" else "ws"
        return httpUrl.newBuilder().scheme(scheme).build().toString()
    }

    private fun nextRequestId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun ensureSocket(): CompletableFuture<Void> {
        synchronized(lock) {
            if (isReady && socket != null) {
                return CompletableFuture.completedFuture(null)
            }

            val future = CompletableFuture<Void>()
            pendingConnects.add(future)

            if (pendingConnects.size == 1) {
                initConnect()
            }

            return future
        }
    }

    private fun initConnect() {
        closeSocket(true)
        manualClose = false
        isReady = false

        val url = try {
            buildWebSocketURL()
        } catch (err: Throwable) {
            connectErrorHandler(err)
            return
        }

        val request = Request.Builder().url(url).build()
        try {
            socket = client.httpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleMessage(text)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        handleClose()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: okhttp3.Response?,
                    ) {
                        connectErrorHandler(t)
                    }
                },
            )
        } catch (err: Throwable) {
            connectErrorHandler(err)
            return
        }

        connectTimeout?.cancel(true)
        connectTimeout = scheduler.schedule(
            { connectErrorHandler(RuntimeException("WebSocket connect took too long.")) },
            maxConnectTimeout,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun handleMessage(payload: String) {
        connectTimeout?.cancel(true)

        val data: JsonElement = try {
            json.parseToJsonElement(payload)
        } catch (_: Exception) {
            return
        }

        val obj = data as? JsonObject ?: return
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return

        when (type) {
            "ready" -> {
                clientId = obj["clientId"]?.jsonPrimitive?.content.orEmpty()
                handleConnected()
            }

            "message" -> {
                val topic = obj["topic"]?.jsonPrimitive?.content ?: return
                val listeners = synchronized(lock) { subscriptions[topic]?.toList() } ?: return
                val message = PubSubMessage(
                    id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                    topic = topic,
                    created = obj["created"]?.jsonPrimitive?.content.orEmpty(),
                    data = decodeValue(obj["data"]),
                )
                for (listener in listeners) {
                    try {
                        listener(message)
                    } catch (_: Exception) {
                    }
                }
            }

            "published", "subscribed", "unsubscribed", "pong" -> {
                val map = jsonElementToMap(obj)
                val requestId = obj["requestId"]?.jsonPrimitive?.content
                if (requestId != null) {
                    resolvePending(requestId, map)
                }
            }

            "error" -> {
                val message = obj["message"]?.jsonPrimitive?.content ?: "pubsub error"
                val requestId = obj["requestId"]?.jsonPrimitive?.content
                val err = RuntimeException(message)
                if (requestId != null) {
                    rejectPending(requestId, err)
                }
            }
        }
    }

    private fun handleConnected() {
        val shouldResubscribe = reconnectAttempts > 0
        reconnectAttempts = 0
        isReady = true
        reconnectTimeout?.cancel(true)
        connectTimeout?.cancel(true)

        val waiters = synchronized(lock) {
            val current = pendingConnects.toList()
            pendingConnects.clear()
            current
        }
        waiters.forEach { it.complete(null) }

        if (shouldResubscribe) {
            val topics = synchronized(lock) { subscriptions.keys.toList() }
            for (topic in topics) {
                val requestId = nextRequestId()
                sendEnvelope(
                    mapOf(
                        "type" to "subscribe",
                        "topic" to topic,
                        "requestId" to requestId,
                    ),
                )
            }
        }
    }

    private fun handleClose() {
        socket = null
        isReady = false

        if (manualClose) {
            return
        }

        rejectAllPending(RuntimeException("pubsub connection closed"))

        if (!hasSubscriptions()) {
            synchronized(lock) { pendingConnects.clear() }
            return
        }

        val delay =
            predefinedReconnectIntervals.getOrElse(reconnectAttempts) { predefinedReconnectIntervals.last() }
        if (reconnectAttempts < Int.MAX_VALUE) {
            reconnectAttempts++
            reconnectTimeout?.cancel(true)
            reconnectTimeout = scheduler.schedule({ initConnect() }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun sendEnvelope(data: Map<String, Any?>) {
        if (!isReady || socket == null) {
            ensureSocket().get()
        }

        val payload = json.encodeToString(JsonElement.serializer(), toJsonElement(data))
        val ws = socket ?: throw RuntimeException("Unable to send websocket message - socket not initialized.")
        ws.send(payload)
    }

    private fun sendUnsubscribe(topic: String) {
        val requestId = nextRequestId()
        val ackFuture = waitForAck(requestId) { true as Boolean }.exceptionally { null }
        sendEnvelope(
            mapOf(
                "type" to "unsubscribe",
                "topic" to topic,
                "requestId" to requestId,
            ),
        )
        ackFuture.get()
    }

    private fun connectErrorHandler(err: Throwable) {
        connectTimeout?.cancel(true)

        if (reconnectAttempts > Int.MAX_VALUE - 1 || manualClose) {
            val waiters = synchronized(lock) {
                val current = pendingConnects.toList()
                pendingConnects.clear()
                current
            }
            waiters.forEach { it.completeExceptionally(ClientResponseError("", originalError = err)) }
            closeSocket()
            return
        }

        closeSocket(true)
        val delay =
            predefinedReconnectIntervals.getOrElse(reconnectAttempts) { predefinedReconnectIntervals.last() }
        reconnectAttempts++
        reconnectTimeout?.cancel(true)
        reconnectTimeout = scheduler.schedule({ initConnect() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun closeSocket(keepSubscriptions: Boolean = false) {
        try {
            socket?.cancel()
        } catch (_: Exception) {
        }
        socket = null
        isReady = false

        connectTimeout?.cancel(true)
        reconnectTimeout?.cancel(true)

        if (!keepSubscriptions) {
            synchronized(lock) { subscriptions.clear() }
            pendingAcks.values.forEach { it.timeout?.cancel(true) }
            pendingAcks.clear()
        }
    }

    private fun <T> waitForAck(
        requestId: String,
        mapper: ((Map<String, Any?>) -> T)? = null,
    ): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val timeout = scheduler.schedule(
            {
                pendingAcks.remove(requestId)
                future.completeExceptionally(RuntimeException("Timed out waiting for pubsub response."))
            },
            ackTimeoutMs,
            TimeUnit.MILLISECONDS,
        )

        pendingAcks[requestId] = PendingAck(
            resolve = { payload ->
                try {
                    val result = mapper?.invoke(payload) ?: payload as T
                    future.complete(result)
                } catch (err: Throwable) {
                    future.completeExceptionally(err)
                }
            },
            reject = { err -> future.completeExceptionally(err) },
            timeout = timeout,
        )

        return future
    }

    private fun resolvePending(requestId: String, payload: Map<String, Any?>) {
        val pending = pendingAcks.remove(requestId) ?: return
        pending.timeout?.cancel(true)
        pending.resolve(payload)
    }

    private fun rejectPending(requestId: String, err: Throwable) {
        val pending = pendingAcks.remove(requestId) ?: return
        pending.timeout?.cancel(true)
        pending.reject(err)
    }

    private fun rejectAllPending(err: Throwable) {
        pendingAcks.values.forEach {
            it.timeout?.cancel(true)
            it.reject(err)
        }
        pendingAcks.clear()

        val waiters = synchronized(lock) {
            val current = pendingConnects.toList()
            pendingConnects.clear()
            current
        }
        waiters.forEach { it.completeExceptionally(err) }
    }

    private fun decodeValue(element: JsonElement?): Any? {
        if (element == null || element is JsonNull) return null
        return when (element) {
            is JsonObject -> jsonElementToMap(element)
            is JsonArray -> element.map { decodeValue(it) }
            is JsonElement -> {
                val prim = element.jsonPrimitive
                prim.booleanOrNull ?: prim.longOrNull ?: prim.doubleOrNull ?: prim.content
            }
            else -> element.toString()
        }
    }
}
