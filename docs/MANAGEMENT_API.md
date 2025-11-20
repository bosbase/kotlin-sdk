# Management API - Kotlin SDK Documentation

This document covers the management API capabilities available in the Kotlin SDK, which correspond to the features available in the backend management UI.

> **Note**: All management API operations require superuser authentication (🔐).

> 📖 **Reference**: For detailed management API concepts, see the [JavaScript SDK Management API documentation](../js-sdk/docs/MANAGEMENT_API.md).

## Table of Contents

- [Settings Service](#settings-service)
- [Backup Service](#backup-service)
- [Log Service](#log-service)
- [Cron Service](#cron-service)
- [Health Service](#health-service)
- [Collection Service](#collection-service)

## Settings Service

The Settings Service provides comprehensive management of application settings.

### Get All Settings

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
pb.admins.authWithPassword("admin@example.com", "password")

// Get all settings
val settings = pb.settings.getAll()

// Get specific category
val metaSettings = pb.settings.getCategory("meta")
val appName = metaSettings?.get("appName")?.jsonPrimitive?.contentOrNull
println("App name: $appName")
```

### Update Settings

```kotlin
// Update settings
pb.settings.update(
    body = mapOf(
        "meta" to mapOf(
            "appName" to "My App",
            "appURL" to "https://example.com",
            "hideControls" to false
        ),
        "rateLimits" to mapOf(
            "enabled" to true,
            "rules" to listOf(
                mapOf(
                    "label" to "api/users",
                    "duration" to 3600,
                    "maxRequests" to 100
                )
            )
        )
    )
)
```

### Individual Settings Updates

```kotlin
// Update meta settings
pb.settings.updateMeta(
    config = mapOf(
        "appName" to "My App",
        "appURL" to "https://example.com",
        "senderName" to "My App",
        "senderAddress" to "noreply@example.com",
        "hideControls" to false
    )
)

// Update trusted proxy
pb.settings.updateTrustedProxy(
    config = mapOf(
        "headers" to listOf("X-Forwarded-For", "X-Real-IP"),
        "useLeftmostIP" to true
    )
)

// Update rate limits
pb.settings.updateRateLimits(
    config = mapOf(
        "enabled" to true,
        "rules" to listOf(
            mapOf(
                "label" to "api/posts",
                "duration" to 3600,
                "maxRequests" to 100
            )
        )
    )
)
```

### Test S3 Storage

```kotlin
// Test S3 storage connection
val success = pb.settings.testS3(filesystem = "storage")
if (success) {
    println("S3 storage connection successful")
} else {
    println("S3 storage connection failed")
}
```

### Test Email

```kotlin
// Send a test email
pb.settings.testEmail(
    email = "test@example.com",
    template = "verification",
    collectionIdOrName = "users"
)
```

### Generate Apple Client Secret

```kotlin
// Generate Apple OAuth2 client secret
val secret = pb.settings.generateAppleClientSecret(
    clientId = "com.example.app",
    teamId = "TEAM_ID",
    keyId = "KEY_ID",
    privateKey = "-----BEGIN PRIVATE KEY-----\n...",
    duration = 86400  // 1 day in seconds
)

val clientSecret = secret?.get("clientSecret")?.jsonPrimitive?.contentOrNull
println("Apple client secret: $clientSecret")
```

## Backup Service

See [BACKUPS_API.md](BACKUPS_API.md) for detailed backup operations.

## Log Service

See [LOGS_API.md](LOGS_API.md) for detailed log operations.

## Cron Service

See [CRONS_API.md](CRONS_API.md) for detailed cron operations.

## Health Service

See [HEALTH_API.md](HEALTH_API.md) for detailed health check operations.

## Collection Service

See [COLLECTIONS.md](COLLECTIONS.md) and [COLLECTION_API.md](COLLECTION_API.md) for detailed collection operations.

