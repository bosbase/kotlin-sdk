package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.toJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class RealtimeService(private val clientRef: BosBase) : BaseService(clientRef) {
    private val json = Json { ignoreUnknownKeys = true }
    private val subscriptions = ConcurrentHashMap<String, MutableList<(Map<String, Any?>) -> Unit>>()
    private var eventSource: EventSource? = null
    private var reconnectAttempt = 0
    private var manualDisconnect = false
    var clientId: String = ""
        private set

    var onDisconnect: ((List<String>) -> Unit)? = null

    fun subscribe(
        topic: String,
        callback: (Map<String, Any?>) -> Unit,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): () -> Unit {
        if (topic.isBlank()) throw IllegalArgumentException("topic must be set")
        val key = buildSubscriptionKey(topic, query, headers)
        val listeners = subscriptions.computeIfAbsent(key) { mutableListOf() }
        listeners.add(callback)

        manualDisconnect = false
        if (eventSource == null) {
            connect()
        } else {
            submitSubscriptions()
        }

        return {
            unsubscribeByTopicAndListener(topic, callback)
        }
    }

    fun unsubscribe(topic: String? = null) {
        if (topic == null) {
            subscriptions.clear()
            disconnect()
            return
        }
        val keys = subscriptions.keys.filter { it == topic || it.startsWith("$topic?") }
        keys.forEach { subscriptions.remove(it) }
        if (subscriptions.isEmpty()) {
            disconnect()
        } else {
            submitSubscriptions()
        }
    }

    fun unsubscribeByPrefix(prefix: String) {
        val keys = subscriptions.keys.filter { (it == prefix) || it.startsWith("$prefix?") }
        keys.forEach { key ->
            subscriptions.remove(key)
        }
        if (subscriptions.isEmpty()) {
            disconnect()
        } else {
            submitSubscriptions()
        }
    }

    fun unsubscribeByTopicAndListener(topic: String, listener: (Map<String, Any?>) -> Unit) {
        val keys = subscriptions.keys.filter { it == topic || it.startsWith("$topic?") }
        keys.forEach { key ->
            val listeners = subscriptions[key]
            listeners?.remove(listener)
            if (listeners != null && listeners.isEmpty()) {
                subscriptions.remove(key)
            }
        }
        if (subscriptions.isEmpty()) {
            disconnect()
        } else {
            submitSubscriptions()
        }
    }

    fun disconnect() {
        manualDisconnect = true
        eventSource?.cancel()
        eventSource = null
        clientId = ""
    }

    private fun connect() {
        if (manualDisconnect) return
        val reqBuilder = Request.Builder()
            .url(clientRef.buildUrl("/api/realtime").toString())
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-store")
            .header("Accept-Language", clientRef.lang)
            .header("User-Agent", "bosbase-kotlin-sdk")

        if (clientRef.authStore.isValid) {
            clientRef.authStore.token?.let { reqBuilder.header("Authorization", it) }
        }

        val factory = EventSources.createFactory(clientRef.httpClient)
        eventSource = factory.newEventSource(reqBuilder.build(), object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val eventName = type ?: "message"
                handleEvent(eventName, data, id)
            }

            override fun onClosed(eventSource: EventSource) {
                handleDisconnect()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                handleDisconnect()
            }
        })
    }

    private fun handleEvent(event: String, data: String, id: String?) {
        if (event == "PB_CONNECT") {
            val payload = parseJsonObject(data)
            clientId = payload["clientId"]?.toString()?.trim('"') ?: id.orEmpty()
            reconnectAttempt = 0
            submitSubscriptions()
            return
        }

        val payload = parseJsonObject(data)
        val listeners = subscriptions[event]?.toList() ?: emptyList()
        for (listener in listeners) {
            try {
                listener(payload)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleDisconnect() {
        val active = subscriptions.keys.toList()
        clientId = ""
        eventSource = null
        onDisconnect?.invoke(active)
        if (active.isNotEmpty() && !manualDisconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val delays = listOf(200L, 500L, 1000L, 2000L, 5000L)
        val delay = delays.getOrElse(reconnectAttempt) { delays.last() }
        reconnectAttempt++
        Thread {
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
            }
            if (subscriptions.isNotEmpty() && !manualDisconnect) {
                connect()
            }
        }.start()
    }

    private fun submitSubscriptions() {
        if (clientId.isBlank() || subscriptions.isEmpty()) return
        val payload = mapOf(
            "clientId" to clientId,
            "subscriptions" to subscriptions.keys.toList(),
        )
        try {
            clientRef.send("/api/realtime", method = "POST", body = payload)
        } catch (_: Exception) {
        }
    }

    private fun buildSubscriptionKey(
        topic: String,
        query: Map<String, Any?>?,
        headers: Map<String, String>?,
    ): String {
        if ((query == null || query.isEmpty()) && (headers == null || headers.isEmpty())) {
            return topic
        }
        val opts = mutableMapOf<String, Any?>()
        if (query != null) opts["query"] = query
        if (headers != null) opts["headers"] = headers
        val serialized = json.encodeToString(JsonObject.serializer(), toJsonElement(opts) as JsonObject)
        val encoded = URLEncoder.encode(serialized, StandardCharsets.UTF_8.toString())
        return topic + if (topic.contains("?")) "&options=$encoded" else "?options=$encoded"
    }

    private fun parseJsonObject(raw: String): Map<String, Any?> {
        return try {
            (json.parseToJsonElement(raw) as? JsonObject)?.mapValues { it.value } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
