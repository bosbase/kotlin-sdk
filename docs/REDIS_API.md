# Redis API (Kotlin SDK)

Redis support is powered by [rueidis](https://github.com/redis/rueidis) and is **disabled unless `REDIS_URL` is provided** on the server. `REDIS_PASSWORD` is optional. Routes are superuser-only; authenticate as `_superusers` first.

- Set `REDIS_URL` (eg. `redis://redis:6379` or `rediss://cache:6379`). Optionally set `REDIS_PASSWORD`.
- Authenticate as a superuser before calling the Redis endpoints.
- When `ttlSeconds` is omitted during updates, the existing TTL is preserved. Use `ttlSeconds = 0` to remove a TTL, or a positive value to set a new one.

## Discover keys

```kotlin
import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

val pb = BosBase("http://127.0.0.1:8090")
pb.admins.authWithPassword("root@example.com", "hunter2")

val page = pb.redis.listKeys(mapOf("pattern" to "session:*", "count" to 100))
val cursor = page?.get("cursor")?.jsonPrimitive?.contentOrNull // pass back to listKeys to continue
val items = page?.get("items")
println(cursor)
println(items)
```

## Create, read, update, delete keys

```kotlin
// Create a key if it does NOT already exist.
pb.redis.createKey(
    key = "session:123",
    value = mapOf("prompt" to "hello", "tokens" to 42),
    ttlSeconds = 3600,
)

// Read the value back with the current TTL (if any).
val entry = pb.redis.getKey("session:123")
println(entry)

// Update an existing key (preserves TTL when ttlSeconds is omitted).
pb.redis.updateKey(
    key = "session:123",
    value = mapOf("prompt" to "updated", "tokens" to 99),
    ttlSeconds = 120, // set to 0 to remove TTL, omit to preserve
)

// Delete the key.
pb.redis.deleteKey("session:123")
```

API responses:
- `listKeys` returns an object like `{ cursor: string, items: [{ key: "..." }, ...] }`.
- `createKey`, `getKey`, and `updateKey` return `{ key, value, ttlSeconds? }`.
- `createKey` fails with HTTP 409 if the key already exists.
