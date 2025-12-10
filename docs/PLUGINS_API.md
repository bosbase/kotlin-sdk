# Plugins Proxy API - Kotlin SDK

## Overview

`plugins` forwards HTTP requests from the Kotlin SDK to the Go backend, which then proxies them to your Python plugin (`PLUGIN_URL` in `docker-compose`). It supports the standard HTTP verbs plus helpers for SSE and WebSocket streams. Endpoints are public by default; the SDK will still forward your token when it exists.

**Key points**
- Supports `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`, `SSE`, `WS`/`WEBSOCKET`.
- Paths are routed through `/api/plugins/{your-plugin-path}` (leading slashes are trimmed; `/api/plugins/...` is accepted).
- Query params, bodies, and headers are passed through unchanged to the plugin service.
- HTTP calls respect global `beforeSend`/`afterSend` hooks and all normal request options.
- SSE/WebSocket URLs include `?token=...` when authenticated so plugins can still authorize callers.

## Quick start

```kotlin
import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.JsonObject

val pb = BosBase("http://127.0.0.1:8080")

// Simple GET to your plugin (e.g., FastAPI /health)
val health = pb.plugins.request<JsonObject?>("GET", "/health")
println(health)
```

## Send bodies and headers

```kotlin
import com.bosbase.sdk.services.PluginRequestOptions

pb.plugins.request<JsonObject?>(
    method = "POST",
    path = "tasks",
    options = PluginRequestOptions(
        body = mapOf("title" to "Generate docs", "priority" to "high"),
        headers = mapOf("X-Plugin-Key" to "demo-secret"),
    ),
)
```

## Work with query parameters

```kotlin
val summary = pb.plugins.request<JsonObject?>(
    method = "GET",
    path = "reports/summary",
    options = PluginRequestOptions(
        query = mapOf("since" to "2024-01-01", "limit" to 50, "tags" to listOf("ops", "ml")),
    ),
)
```

## Other verbs

```kotlin
pb.plugins.request<Unit>("PATCH", "tasks/42", PluginRequestOptions(body = mapOf("status" to "done")))
pb.plugins.request<Unit>("DELETE", "tasks/42")
pb.plugins.request<Unit>("HEAD", "health")
pb.plugins.request<Unit>("OPTIONS", "tasks")
```

## Server-Sent Events (SSE)

Provide an `EventSourceListener` to stream events from your plugin. The SDK appends `token` to the URL and forwards headers where supported.

```kotlin
import com.bosbase.sdk.services.PluginRequestOptions
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

val listener = object : EventSourceListener() {
    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        println("[$type] $data")
    }
}

val stream = pb.plugins.sse(
    path = "events/updates",
    listener = listener,
    options = PluginRequestOptions(
        query = mapOf("topic" to "team-alpha"),
        headers = mapOf("X-Plugin-Key" to "secret"),
    ),
)
```

## WebSockets

Use `websocket` to open a socket to your plugin. Subprotocols and headers are forwarded when supported by the runtime.

```kotlin
import com.bosbase.sdk.services.PluginRequestOptions
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

val socket = pb.plugins.websocket(
    path = "ws/chat",
    listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"type":"join","name":"lea"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            println("chat: $text")
        }
    },
    options = PluginRequestOptions(
        query = mapOf("room" to "general"),
        websocketProtocols = listOf("json"),
        headers = mapOf("X-Plugin-Key" to "secret"),
    ),
)
```

## Notes and behavior
- HTTP calls use `plugins.request` and run through the normal request pipeline.
- SSE/WebSocket helpers skip `beforeSend`/`afterSend` because they construct connections directly with OkHttp.
- When authenticated, the SDK appends `token` to SSE/WS URLs for environments where custom headers are ignored.
- Use `timeoutSeconds` in `PluginRequestOptions` to apply per-request timeouts to plugin HTTP, SSE, or WebSocket connections.
