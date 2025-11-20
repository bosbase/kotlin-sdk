# Crons API - Kotlin SDK Documentation

## Overview

The Crons API provides endpoints for viewing and manually triggering scheduled cron jobs. All operations require superuser authentication and allow you to list registered cron jobs and execute them on-demand.

**Key Features:**
- List all registered cron jobs
- View cron job schedules (cron expressions)
- Manually trigger cron jobs
- Built-in system jobs for maintenance tasks

**Backend Endpoints:**
- `GET /api/crons` - List cron jobs
- `POST /api/crons/{jobId}` - Run cron job

**Note**: All Crons API operations require superuser authentication.

> 📖 **Reference**: For detailed crons concepts, see the [JavaScript SDK Crons documentation](../js-sdk/docs/CRONS_API.md).

## Authentication

All Crons API operations require superuser authentication:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Authenticate as superuser
pb.admins.authWithPassword("admin@example.com", "password")
```

## List Cron Jobs

Returns a list of all registered cron jobs with their IDs and schedule expressions.

### Basic Usage

```kotlin
// Get all cron jobs
val jobs = pb.crons.getFullList()

jobs.forEach { job ->
    val id = job["id"]?.jsonPrimitive?.contentOrNull
    val expression = job["expression"]?.jsonPrimitive?.contentOrNull
    println("Job ID: $id, Expression: $expression")
}
```

### Cron Job Structure

Each cron job contains:

```kotlin
{
    "id": "string",        // Unique identifier for the job
    "expression": "string" // Cron expression defining the schedule
}
```

### Built-in System Jobs

The following cron jobs are typically registered by default:

| Job ID | Expression | Description | Schedule |
|--------|-----------|-------------|----------|
| `__pbLogsCleanup__` | `0 */6 * * *` | Cleans up old log entries | Every 6 hours |
| `__pbDBOptimize__` | `0 0 * * *` | Optimizes database | Daily at midnight |
| `__pbMFACleanup__` | `0 * * * *` | Cleans up expired MFA records | Every hour |
| `__pbOTPCleanup__` | `0 * * * *` | Cleans up expired OTP codes | Every hour |

### Working with Cron Jobs

```kotlin
// List all cron jobs
val jobs = pb.crons.getFullList()

// Find a specific job
val logsCleanup = jobs.find { job ->
    job["id"]?.jsonPrimitive?.contentOrNull == "__pbLogsCleanup__"
}

if (logsCleanup != null) {
    val expression = logsCleanup["expression"]?.jsonPrimitive?.contentOrNull
    println("Logs cleanup runs: $expression")
}

// Filter system jobs
val systemJobs = jobs.filter { job ->
    job["id"]?.jsonPrimitive?.contentOrNull?.startsWith("__pb") == true
}

// Filter custom jobs
val customJobs = jobs.filter { job ->
    job["id"]?.jsonPrimitive?.contentOrNull?.startsWith("__pb") != true
}
```

## Run Cron Job

Manually trigger a cron job to execute immediately.

### Basic Usage

```kotlin
// Run a specific cron job
pb.crons.run(jobId = "__pbLogsCleanup__")
```

### Error Handling

```kotlin
import com.bosbase.sdk.ClientResponseError

try {
    pb.crons.run(jobId = "__pbLogsCleanup__")
    println("Cron job executed successfully")
} catch (e: ClientResponseError) {
    when (e.status) {
        404 -> println("Cron job not found")
        403 -> println("Access forbidden - superuser required")
        else -> println("Error: ${e.status}")
    }
}
```

## Examples

### List All Cron Jobs

```kotlin
fun listAllCronJobs(pb: BosBase) {
    // Authenticate as superuser
    pb.admins.authWithPassword("admin@example.com", "password")
    
    val jobs = pb.crons.getFullList()
    
    println("Registered cron jobs: ${jobs.size}")
    jobs.forEach { job ->
        val id = job["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val expression = job["expression"]?.jsonPrimitive?.contentOrNull ?: ""
        println("  $id: $expression")
    }
}
```

### Manual Database Optimization

```kotlin
fun optimizeDatabase(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Manually trigger database optimization
    pb.crons.run(jobId = "__pbDBOptimize__")
    println("Database optimization job triggered")
}
```

### Cleanup Operations

```kotlin
fun cleanupSystemData(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Clean up expired logs
    pb.crons.run(jobId = "__pbLogsCleanup__")
    
    // Clean up expired MFA records
    pb.crons.run(jobId = "__pbMFACleanup__")
    
    // Clean up expired OTP codes
    pb.crons.run(jobId = "__pbOTPCleanup__")
    
    println("Cleanup jobs executed")
}
```

### Cron Job Monitor

```kotlin
class CronJobMonitor(private val pb: BosBase) {
    fun getCronJobs(): List<Map<String, String>> {
        val jobs = pb.crons.getFullList()
        return jobs.mapNotNull { job ->
            val id = job["id"]?.jsonPrimitive?.contentOrNull
            val expression = job["expression"]?.jsonPrimitive?.contentOrNull
            if (id != null && expression != null) {
                mapOf("id" to id, "expression" to expression)
            } else null
        }
    }
    
    fun runJob(jobId: String): Boolean {
        return try {
            pb.crons.run(jobId = jobId)
            true
        } catch (e: Exception) {
            println("Failed to run job $jobId: ${e.message}")
            false
        }
    }
}

// Usage
val monitor = CronJobMonitor(pb)
monitor.getCronJobs().forEach { job ->
    println("Job: ${job["id"]} - ${job["expression"]}")
}
```

