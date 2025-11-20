# Cache API - Kotlin SDK Documentation

BosBase caches combine in-memory FreeCache storage with persistent database copies. Each cache instance is safe to use in single-node or multi-node (cluster) mode: nodes read from FreeCache first, fall back to the database if an item is missing or expired, and then reload FreeCache automatically.

The Kotlin SDK exposes the cache endpoints through `pb.caches`. Typical use cases include:

- Caching AI prompts/responses that must survive restarts.
- Quickly sharing feature flags and configuration between workers.
- Preloading expensive vector search results for short periods.

> **Timeouts & TTLs:** Each cache defines a default TTL (in seconds). Individual entries may provide their own `ttlSeconds`. A value of `0` keeps the entry until it is manually deleted.

> 📖 **Reference**: For detailed cache concepts, see the [JavaScript SDK Cache documentation](../js-sdk/docs/CACHE_API.md).

## List Available Caches

The `list()` function allows you to query and retrieve all currently available caches, including their names and capacities.

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")
pb.admins.authWithPassword("root@example.com", "hunter2")

// Query all available caches
val caches = pb.caches.list()

// Each cache object contains:
// - name: string - The cache identifier
// - sizeBytes: number - The cache capacity in bytes
// - defaultTTLSeconds: number - Default expiration time
// - readTimeoutMs: number - Read timeout in milliseconds
// - created: string - Creation timestamp (RFC3339)
// - updated: string - Last update timestamp (RFC3339)

// Example: Find a cache by name and check its capacity
val targetCache = caches.find { it["name"]?.jsonPrimitive?.contentOrNull == "ai-session" }
if (targetCache != null) {
    val sizeBytes = targetCache["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L
    println("Cache \"ai-session\" has capacity of $sizeBytes bytes")
} else {
    println("Cache not found, create a new one if needed")
}
```

## Manage Cache Configurations

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")
pb.admins.authWithPassword("root@example.com", "hunter2")

// List all available caches (including name and capacity)
val caches = pb.caches.list()
println("Available caches: ${caches.size}")

// Find an existing cache by name
val existingCache = caches.find { it["name"]?.jsonPrimitive?.contentOrNull == "ai-session" }
if (existingCache != null) {
    val sizeBytes = existingCache["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L
    println("Found cache \"ai-session\" with capacity $sizeBytes bytes")
} else {
    // Create a new cache only if it doesn't exist
    pb.caches.create(
        name = "ai-session",
        sizeBytes = 64 * 1024 * 1024,  // 64MB
        defaultTTLSeconds = 300,        // 5 minutes
        readTimeoutMs = 25              // optional concurrency guard
    )
}

// Update limits later (eg. shrink TTL to 2 minutes)
pb.caches.update(
    name = "ai-session",
    body = mapOf("defaultTTLSeconds" to 120)
)

// Delete the cache (DB rows + FreeCache)
pb.caches.delete("ai-session")
```

## Cache Entry Operations

### Set Entry

```kotlin
// Set a cache entry with default TTL
pb.caches.setEntry(
    cache = "ai-session",
    key = "user_123",
    value = mapOf("prompt" to "Hello", "response" to "Hi there!")
)

// Set a cache entry with custom TTL (60 seconds)
pb.caches.setEntry(
    cache = "ai-session",
    key = "user_456",
    value = "Some cached value",
    ttlSeconds = 60
)

// Set a cache entry that never expires (TTL = 0)
pb.caches.setEntry(
    cache = "ai-session",
    key = "feature_flags",
    value = mapOf("feature1" to true, "feature2" to false),
    ttlSeconds = 0
)
```

### Get Entry

```kotlin
// Get a cache entry
val entry = pb.caches.getEntry(
    cache = "ai-session",
    key = "user_123"
)

val value = entry?.get("value")
println("Cached value: $value")
```

### Renew Entry

Extend the TTL of an existing entry without changing its value:

```kotlin
// Renew entry TTL (extend by 300 seconds)
pb.caches.renewEntry(
    cache = "ai-session",
    key = "user_123",
    ttlSeconds = 300
)
```

### Delete Entry

```kotlin
// Delete a cache entry
pb.caches.deleteEntry(
    cache = "ai-session",
    key = "user_123"
)
```

## Complete Example

```kotlin
import com.bosbase.sdk.BosBase

fun cacheExample(pb: BosBase) {
    // Authenticate as superuser
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Check if cache exists
    val caches = pb.caches.list()
    val aiCache = caches.find { it["name"]?.jsonPrimitive?.contentOrNull == "ai-session" }
    
    if (aiCache == null) {
        // Create cache if it doesn't exist
        pb.caches.create(
            name = "ai-session",
            sizeBytes = 64 * 1024 * 1024,  // 64MB
            defaultTTLSeconds = 300          // 5 minutes default
        )
        println("Created ai-session cache")
    }
    
    // Set cache entry
    pb.caches.setEntry(
        cache = "ai-session",
        key = "user_123_prompt",
        value = mapOf(
            "prompt" to "What is Kotlin?",
            "response" to "Kotlin is a programming language..."
        ),
        ttlSeconds = 600  // 10 minutes
    )
    
    // Get cache entry
    val cached = pb.caches.getEntry(
        cache = "ai-session",
        key = "user_123_prompt"
    )
    
    if (cached != null) {
        val value = cached["value"]
        println("Retrieved from cache: $value")
    }
    
    // Renew entry (extend TTL)
    pb.caches.renewEntry(
        cache = "ai-session",
        key = "user_123_prompt",
        ttlSeconds = 300  // Add 5 more minutes
    )
    
    // Delete entry when done
    pb.caches.deleteEntry(
        cache = "ai-session",
        key = "user_123_prompt"
    )
}
```

