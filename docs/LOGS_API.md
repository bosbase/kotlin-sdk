# Logs API - Kotlin SDK Documentation

## Overview

The Logs API provides endpoints for viewing and analyzing application logs. All operations require superuser authentication and allow you to query request logs, filter by various criteria, and get aggregated statistics.

**Key Features:**
- List and paginate logs
- View individual log entries
- Filter logs by status, URL, method, IP, etc.
- Sort logs by various fields
- Get hourly aggregated statistics
- Filter statistics by criteria

**Backend Endpoints:**
- `GET /api/logs` - List logs
- `GET /api/logs/{id}` - View log
- `GET /api/logs/stats` - Get statistics

**Note**: All Logs API operations require superuser authentication.

> 📖 **Reference**: For detailed logs concepts, see the [JavaScript SDK Logs documentation](../js-sdk/docs/LOGS_API.md).

## Authentication

All Logs API operations require superuser authentication:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Authenticate as superuser
pb.admins.authWithPassword("admin@example.com", "password")
```

## List Logs

Returns a paginated list of logs with support for filtering and sorting.

### Basic Usage

```kotlin
// Basic list
val result = pb.logs.getList(page = 1, perPage = 30)

val page = result?.get("page")?.jsonPrimitive?.intOrNull
val perPage = result?.get("perPage")?.jsonPrimitive?.intOrNull
val totalItems = result?.get("totalItems")?.jsonPrimitive?.intOrNull
val items = result?.get("items")?.jsonArray

println("Page: $page, Per Page: $perPage, Total: $totalItems")
```

### Log Entry Structure

Each log entry contains:

```kotlin
{
    "id": "ai5z3aoed6809au",
    "created": "2024-10-27 09:28:19.524Z",
    "level": 0,
    "message": "GET /api/collections/posts/records",
    "data": {
        "auth": "_superusers",
        "execTime": 2.392327,
        "method": "GET",
        "referer": "http://localhost:8090/_/",
        "remoteIP": "127.0.0.1",
        "status": 200,
        "type": "request",
        "url": "/api/collections/posts/records?page=1",
        "userAgent": "Mozilla/5.0...",
        "userIP": "127.0.0.1"
    }
}
```

### Filtering Logs

```kotlin
// Filter by HTTP status code
val errorLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    filter = "data.status >= 400"
)

// Filter by method
val getLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    filter = "data.method = \"GET\""
)

// Filter by URL pattern
val apiLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    filter = "data.url ~ \"/api/\""
)

// Filter by IP address
val ipLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    filter = "data.remoteIP = \"127.0.0.1\""
)

// Filter by execution time (slow requests)
val slowLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    filter = "data.execTime > 1.0"
)
```

### Sorting Logs

```kotlin
// Sort by creation date (newest first)
val recentLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    sort = "-created"
)

// Sort by execution time (slowest first)
val slowestLogs = pb.logs.getList(
    page = 1,
    perPage = 50,
    sort = "-data.execTime"
)
```

## Get Single Log

Retrieve a single log entry by ID.

```kotlin
val log = pb.logs.getOne(logId = "ai5z3aoed6809au")

val message = log?.get("message")?.jsonPrimitive?.contentOrNull
val data = log?.get("data")?.jsonObject
val status = data?.get("status")?.jsonPrimitive?.intOrNull

println("Message: $message")
println("Status: $status")
```

## Get Statistics

Get hourly aggregated statistics.

```kotlin
// Get all statistics
val stats = pb.logs.getStats()

// Get filtered statistics
val errorStats = pb.logs.getStats(
    query = mapOf("filter" to "data.status >= 400")
)

stats.forEach { stat ->
    // Process statistics data
    println("Stat: $stat")
}
```

## Examples

### Error Log Monitor

```kotlin
fun monitorErrors(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Get recent errors
    val errors = pb.logs.getList(
        page = 1,
        perPage = 50,
        filter = "data.status >= 400",
        sort = "-created"
    )
    
    val items = errors?.get("items")?.jsonArray
    items?.forEach { log ->
        val data = log.jsonObject["data"]?.jsonObject
        val status = data?.get("status")?.jsonPrimitive?.intOrNull
        val url = data?.get("url")?.jsonPrimitive?.contentOrNull
        val method = data?.get("method")?.jsonPrimitive?.contentOrNull
        
        println("$status $method $url")
    }
}
```

### Performance Monitor

```kotlin
fun monitorPerformance(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Get slow requests
    val slowRequests = pb.logs.getList(
        page = 1,
        perPage = 20,
        filter = "data.execTime > 1.0",
        sort = "-data.execTime"
    )
    
    val items = slowRequests?.get("items")?.jsonArray
    items?.forEach { log ->
        val data = log.jsonObject["data"]?.jsonObject
        val execTime = data?.get("execTime")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val url = data?.get("url")?.jsonPrimitive?.contentOrNull
        
        println("${execTime}s - $url")
    }
}
```

### Log Analytics

```kotlin
class LogAnalytics(private val pb: BosBase) {
    fun getErrorCount(): Int {
        val errors = pb.logs.getList(
            page = 1,
            perPage = 1,
            filter = "data.status >= 400"
        )
        return errors?.get("totalItems")?.jsonPrimitive?.intOrNull ?: 0
    }
    
    fun getAverageResponseTime(): Double {
        val allLogs = pb.logs.getList(page = 1, perPage = 100)
        val items = allLogs?.get("items")?.jsonArray ?: return 0.0
        
        val totalTime = items.sumOf { log ->
            val data = log.jsonObject["data"]?.jsonObject
            data?.get("execTime")?.jsonPrimitive?.doubleOrNull ?: 0.0
        }
        
        return if (items.isNotEmpty()) totalTime / items.size else 0.0
    }
    
    fun getMostFrequentEndpoints(limit: Int = 10): List<Pair<String, Int>> {
        val logs = pb.logs.getList(page = 1, perPage = 1000)
        val items = logs?.get("items")?.jsonArray ?: return emptyList()
        
        val endpointCounts = mutableMapOf<String, Int>()
        items.forEach { log ->
            val data = log.jsonObject["data"]?.jsonObject
            val url = data?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
            val method = data?.get("method")?.jsonPrimitive?.contentOrNull ?: ""
            val endpoint = "$method $url"
            endpointCounts[endpoint] = endpointCounts.getOrDefault(endpoint, 0) + 1
        }
        
        return endpointCounts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Pair(it.key, it.value) }
    }
}

// Usage
val analytics = LogAnalytics(pb)
println("Error count: ${analytics.getErrorCount()}")
println("Average response time: ${analytics.getAverageResponseTime()}s")
analytics.getMostFrequentEndpoints(5).forEach { (endpoint, count) ->
    println("$endpoint: $count requests")
}
```

