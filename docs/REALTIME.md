# Realtime API - Kotlin SDK Documentation

## Overview

The Realtime API enables real-time updates for collection records using **Server-Sent Events (SSE)**. It allows you to subscribe to changes in collections or specific records and receive instant notifications when records are created, updated, or deleted.

**Key Features:**
- Real-time notifications for record changes
- Collection-level and record-level subscriptions
- Automatic connection management and reconnection
- Authorization support
- Subscription options (expand, custom headers, query params)
- Event-driven architecture

**Backend Endpoints:**
- `GET /api/realtime` - Establish SSE connection
- `POST /api/realtime` - Set subscriptions

> 📖 **Reference**: For detailed realtime concepts, see the [JavaScript SDK Realtime documentation](../js-sdk/docs/REALTIME.md).

## How It Works

1. **Connection**: The SDK establishes an SSE connection to `/api/realtime`
2. **Client ID**: Server sends `PB_CONNECT` event with a unique `clientId`
3. **Subscriptions**: Client submits subscription topics via POST request
4. **Events**: Server sends events when matching records change
5. **Reconnection**: SDK automatically reconnects on connection loss

## Basic Usage

### Subscribe to Collection Changes

Subscribe to all changes in a collection:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Subscribe to all changes in the 'posts' collection
val unsubscribe = pb.collection("posts").subscribe("*") { event ->
    val action = event["action"]?.toString()  // 'create', 'update', or 'delete'
    val record = event["record"]             // The record data
    
    println("Action: $action")
    println("Record: $record")
}

// Later, unsubscribe
unsubscribe()
```

### Subscribe to Specific Record

Subscribe to changes for a single record:

```kotlin
// Subscribe to changes for a specific post
val unsubscribe = pb.collection("posts").subscribe("RECORD_ID") { event ->
    val record = event["record"]
    val action = event["action"]?.toString()
    
    println("Record changed: $record")
    println("Action: $action")
}

// Unsubscribe when done
unsubscribe()
```

### Multiple Subscriptions

You can subscribe multiple times to the same or different topics:

```kotlin
// Subscribe to multiple records
val unsubscribe1 = pb.collection("posts").subscribe("RECORD_ID_1") { event ->
    handleChange(event)
}

val unsubscribe2 = pb.collection("posts").subscribe("RECORD_ID_2") { event ->
    handleChange(event)
}

val unsubscribe3 = pb.collection("posts").subscribe("*") { event ->
    handleAllChanges(event)
}

fun handleChange(event: Map<String, Any?>) {
    println("Change event: $event")
}

fun handleAllChanges(event: Map<String, Any?>) {
    println("Collection-wide change: $event")
}

// Unsubscribe individually
unsubscribe1()
unsubscribe2()
unsubscribe3()
```

## Subscription Options

### With Expand

Subscribe with relation expansion:

```kotlin
val unsubscribe = pb.collection("posts").subscribe(
    topic = "*",
    callback = { event ->
        val record = event["record"]
        println("Record: $record")
    },
    query = mapOf("expand" to "author,categories")
)
```

### With Custom Headers

```kotlin
val unsubscribe = pb.collection("posts").subscribe(
    topic = "*",
    callback = { event ->
        println("Event: $event")
    },
    headers = mapOf(
        "X-Custom-Header" to "value"
    )
)
```

### With Query Parameters

```kotlin
val unsubscribe = pb.collection("posts").subscribe(
    topic = "*",
    callback = { event ->
        println("Event: $event")
    },
    query = mapOf(
        "expand" to "author",
        "fields" to "id,title,content"
    )
)
```

## Unsubscribing

### Unsubscribe from Specific Topic

```kotlin
// Unsubscribe from a specific record
pb.collection("posts").unsubscribe("RECORD_ID")

// Unsubscribe from all collection subscriptions
pb.collection("posts").unsubscribe()
```

### Unsubscribe by Prefix

```kotlin
// Unsubscribe from all topics starting with a prefix
pb.realtime.unsubscribeByPrefix("posts/")
```

### Unsubscribe All

```kotlin
// Unsubscribe from all realtime subscriptions
pb.realtime.unsubscribe()
```

## Connection Management

### Check Connection Status

```kotlin
val isConnected = pb.realtime.clientId.isNotBlank()
println("Realtime connected: $isConnected")
```

### Manual Disconnect

```kotlin
// Manually disconnect (will not auto-reconnect)
pb.realtime.disconnect()
```

### Disconnect Callback

Handle disconnection events:

```kotlin
pb.realtime.onDisconnect = { activeSubscriptions ->
    println("Disconnected. Active subscriptions: $activeSubscriptions")
    // Handle reconnection logic if needed
}
```

## Event Structure

Events received in callbacks have the following structure:

```kotlin
{
    "action": "create" | "update" | "delete",
    "record": { /* record data */ }
}
```

### Accessing Event Data

```kotlin
val unsubscribe = pb.collection("posts").subscribe("*") { event ->
    val action = event["action"]?.toString()
    val record = event["record"] as? Map<String, Any?>
    
    when (action) {
        "create" -> {
            val title = record?.get("title")?.toString()
            println("New post created: $title")
        }
        "update" -> {
            val id = record?.get("id")?.toString()
            println("Post updated: $id")
        }
        "delete" -> {
            val id = record?.get("id")?.toString()
            println("Post deleted: $id")
        }
    }
}
```

## Error Handling

Realtime subscriptions handle errors gracefully with automatic reconnection:

```kotlin
val unsubscribe = pb.collection("posts").subscribe("*") { event ->
    try {
        // Process event
        val record = event["record"]
        println("Received: $record")
    } catch (e: Exception) {
        println("Error processing event: $e")
    }
}

// The SDK will automatically reconnect on connection loss
```

## Examples

### Complete Realtime Example

```kotlin
import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun main() {
    val pb = BosBase("http://localhost:8090")
    
    // Authenticate if needed
    pb.collection("users").authWithPassword("user@example.com", "password")
    
    // Subscribe to all posts
    val unsubscribe = pb.collection("posts").subscribe("*") { event ->
        val action = event["action"]?.toString()
        val record = event["record"] as? Map<String, Any?>
        
        when (action) {
            "create" -> {
                val title = record?.get("title")?.toString()
                println("✨ New post: $title")
            }
            "update" -> {
                val title = record?.get("title")?.toString()
                println("📝 Updated post: $title")
            }
            "delete" -> {
                val id = record?.get("id")?.toString()
                println("🗑️  Deleted post: $id")
            }
        }
    }
    
    println("Listening for realtime updates...")
    
    // Keep the program running
    Thread.sleep(60000) // Listen for 60 seconds
    
    // Clean up
    unsubscribe()
    pb.realtime.disconnect()
}
```

### Real-time Chat Example

```kotlin
class ChatManager(private val pb: BosBase, private val roomId: String) {
    private var unsubscribe: (() -> Unit)? = null
    
    fun startListening(onMessage: (Map<String, Any?>) -> Unit) {
        unsubscribe = pb.collection("messages").subscribe("*") { event ->
            val action = event["action"]?.toString()
            val record = event["record"] as? Map<String, Any?>
            
            if (action == "create") {
                val messageRoomId = record?.get("roomId")?.toString()
                if (messageRoomId == roomId) {
                    onMessage(record)
                }
            }
        }
    }
    
    fun stopListening() {
        unsubscribe?.invoke()
        unsubscribe = null
    }
}

// Usage
val chatManager = ChatManager(pb, "room_123")
chatManager.startListening { message ->
    val text = message["text"]?.toString()
    val author = message["author"]?.toString()
    println("$author: $text")
}
```

### Live Dashboard Example

```kotlin
class LiveDashboard(private val pb: BosBase) {
    private val subscriptions = mutableListOf<() -> Unit>()
    
    fun start() {
        // Subscribe to posts
        subscriptions.add(
            pb.collection("posts").subscribe("*") { event ->
                updatePostsCounter(event)
            }
        )
        
        // Subscribe to users
        subscriptions.add(
            pb.collection("users").subscribe("*") { event ->
                updateUsersCounter(event)
            }
        )
        
        // Subscribe to comments
        subscriptions.add(
            pb.collection("comments").subscribe("*") { event ->
                updateCommentsCounter(event)
            }
        )
    }
    
    fun stop() {
        subscriptions.forEach { it.invoke() }
        subscriptions.clear()
    }
    
    private fun updatePostsCounter(event: Map<String, Any?>) {
        val action = event["action"]?.toString()
        println("Posts: $action event received")
    }
    
    private fun updateUsersCounter(event: Map<String, Any?>) {
        val action = event["action"]?.toString()
        println("Users: $action event received")
    }
    
    private fun updateCommentsCounter(event: Map<String, Any?>) {
        val action = event["action"]?.toString()
        println("Comments: $action event received")
    }
}

// Usage
val dashboard = LiveDashboard(pb)
dashboard.start()

// Later...
dashboard.stop()
```

### Android Realtime Example

```kotlin
import android.os.Handler
import android.os.Looper
import com.bosbase.sdk.BosBase

class RealtimeViewModel(private val pb: BosBase) {
    private val handler = Handler(Looper.getMainLooper())
    private var unsubscribe: (() -> Unit)? = null
    
    fun observePosts(onUpdate: (List<Map<String, Any?>>) -> Unit) {
        unsubscribe = pb.collection("posts").subscribe("*") { event ->
            // Update on main thread
            handler.post {
                // Fetch updated list
                val posts = pb.collection("posts").getList(page = 1, perPage = 50)
                onUpdate(posts.items.map { it.jsonObject })
            }
        }
    }
    
    fun stopObserving() {
        unsubscribe?.invoke()
        unsubscribe = null
    }
}
```

