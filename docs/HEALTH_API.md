# Health API - Kotlin SDK Documentation

## Overview

The Health API provides a simple endpoint to check the health status of the server. It returns basic health information and, when authenticated as a superuser, provides additional diagnostic information about the server state.

**Key Features:**
- No authentication required for basic health check
- Superuser authentication provides additional diagnostic data
- Lightweight endpoint for monitoring and health checks
- Supports both GET and HEAD methods

**Backend Endpoints:**
- `GET /api/health` - Check health status
- `HEAD /api/health` - Check health status (HEAD method)

**Note**: The health endpoint is publicly accessible, but superuser authentication provides additional information.

> 📖 **Reference**: For detailed health check concepts, see the [JavaScript SDK Health documentation](../js-sdk/docs/HEALTH_API.md).

## Authentication

Basic health checks do not require authentication:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Basic health check (no auth required)
val health = pb.health.check()
```

For additional diagnostic information, authenticate as a superuser:

```kotlin
// Authenticate as superuser for extended health data
pb.admins.authWithPassword("admin@example.com", "password")
val health = pb.health.check()
```

## Health Check Response Structure

### Basic Response (Guest/Regular User)

```kotlin
{
    "code": 200,
    "message": "API is healthy.",
    "data": {}
}
```

### Superuser Response

```kotlin
{
    "code": 200,
    "message": "API is healthy.",
    "data": {
        "canBackup": true,              // Whether backup operations are allowed
        "realIP": "192.168.1.100",      // Real IP address of the client
        "requireS3": false,              // Whether S3 storage is required
        "possibleProxyHeader": "X-Forwarded-For"  // Detected proxy header
    }
}
```

## Check Health Status

Returns the health status of the API server.

### Basic Usage

```kotlin
// Simple health check
val health = pb.health.check()

val message = health?.get("message")?.jsonPrimitive?.contentOrNull
val code = health?.get("code")?.jsonPrimitive?.intOrNull

println("Health: $message")  // "API is healthy."
println("Code: $code")       // 200
```

### With Superuser Authentication

```kotlin
// Authenticate as superuser first
pb.admins.authWithPassword("admin@example.com", "password")

// Get extended health information
val health = pb.health.check()
val data = health?.get("data")?.jsonObject

println("Can backup: ${data?.get("canBackup")?.jsonPrimitive?.booleanOrNull}")
println("Real IP: ${data?.get("realIP")?.jsonPrimitive?.contentOrNull}")
println("Require S3: ${data?.get("requireS3")?.jsonPrimitive?.booleanOrNull}")
println("Proxy header: ${data?.get("possibleProxyHeader")?.jsonPrimitive?.contentOrNull}")
```

## Examples

### Basic Health Monitoring

```kotlin
fun checkServerHealth(pb: BosBase): Boolean {
    return try {
        val health = pb.health.check()
        val code = health?.get("code")?.jsonPrimitive?.intOrNull
        val message = health?.get("message")?.jsonPrimitive?.contentOrNull
        
        code == 200 && message == "API is healthy."
    } catch (e: Exception) {
        false
    }
}

// Use in monitoring
fun monitorHealth(pb: BosBase) {
    val isHealthy = checkServerHealth(pb)
    if (isHealthy) {
        println("✓ Server is healthy")
    } else {
        println("✗ Server health check failed")
    }
}
```

### Backup Readiness Check

```kotlin
fun canPerformBackup(pb: BosBase): Boolean {
    return try {
        // Authenticate as superuser
        pb.admins.authWithPassword("admin@example.com", "password")
        
        val health = pb.health.check()
        val canBackup = health?.get("data")?.jsonObject
            ?.get("canBackup")?.jsonPrimitive?.booleanOrNull ?: false
        
        if (!canBackup) {
            println("⚠️ Backup operation is currently in progress")
            return false
        }
        
        println("✓ Backup operations are allowed")
        true
    } catch (e: Exception) {
        println("Failed to check backup readiness: ${e.message}")
        false
    }
}

// Use before creating backups
if (canPerformBackup(pb)) {
    pb.backups.create(name = "backup.zip")
}
```

### Health Monitor Class

```kotlin
class HealthMonitor(private val pb: BosBase) {
    private var isSuperuser = false
    
    fun authenticateAsSuperuser(email: String, password: String): Boolean {
        return try {
            pb.admins.authWithPassword(email, password)
            isSuperuser = true
            true
        } catch (e: Exception) {
            println("Superuser authentication failed: ${e.message}")
            false
        }
    }
    
    fun getHealthStatus(): Map<String, Any?> {
        return try {
            val health = pb.health.check()
            val code = health?.get("code")?.jsonPrimitive?.intOrNull ?: 0
            val message = health?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
            
            val status = mutableMapOf<String, Any?>(
                "healthy" to (code == 200),
                "message" to message,
                "code" to code,
                "timestamp" to System.currentTimeMillis()
            )
            
            if (isSuperuser) {
                val data = health?.get("data")?.jsonObject
                status["diagnostics"] = mapOf(
                    "canBackup" to (data?.get("canBackup")?.jsonPrimitive?.booleanOrNull),
                    "realIP" to (data?.get("realIP")?.jsonPrimitive?.contentOrNull),
                    "requireS3" to (data?.get("requireS3")?.jsonPrimitive?.booleanOrNull),
                    "behindProxy" to (data?.get("possibleProxyHeader")?.jsonPrimitive?.contentOrNull != null),
                    "proxyHeader" to (data?.get("possibleProxyHeader")?.jsonPrimitive?.contentOrNull)
                )
            }
            
            status
        } catch (e: Exception) {
            mapOf(
                "healthy" to false,
                "error" to e.message,
                "timestamp" to System.currentTimeMillis()
            )
        }
    }
}

// Usage
val monitor = HealthMonitor(pb)
monitor.authenticateAsSuperuser("admin@example.com", "password")

val status = monitor.getHealthStatus()
println("Health status: $status")
```

## Error Handling

```kotlin
import com.bosbase.sdk.ClientResponseError

fun safeHealthCheck(pb: BosBase): Map<String, Any?> {
    return try {
        val health = pb.health.check()
        mapOf(
            "success" to true,
            "data" to health
        )
    } catch (e: ClientResponseError) {
        // Network errors, server down, etc.
        mapOf(
            "success" to false,
            "error" to e.message,
            "code" to e.status
        )
    }
}

// Handle different error scenarios
val result = safeHealthCheck(pb)
if (result["success"] as? Boolean != true) {
    val code = result["code"] as? Int ?: 0
    if (code == 0) {
        println("Network error or server unreachable")
    } else {
        println("Server returned error: $code")
    }
}
```

## Best Practices

1. **Monitoring**: Use health checks for regular monitoring (e.g., every 30-60 seconds)
2. **Load Balancers**: Configure load balancers to use the health endpoint for health checks
3. **Pre-flight Checks**: Check `canBackup` before initiating backup operations
4. **Error Handling**: Always handle errors gracefully as the server may be down
5. **Rate Limiting**: Don't poll the health endpoint too frequently (avoid spamming)
6. **Caching**: Consider caching health check results for a few seconds to reduce load
7. **Superuser Auth**: Only authenticate as superuser when you need diagnostic information

