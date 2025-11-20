# Backups API - Kotlin SDK Documentation

## Overview

The Backups API provides endpoints for managing application data backups. You can create backups, upload existing backup files, download backups, delete backups, and restore the application from a backup.

**Key Features:**
- List all available backup files
- Create new backups with custom names or auto-generated names
- Upload existing backup ZIP files
- Download backup files (requires file token)
- Delete backup files
- Restore the application from a backup (restarts the app)

**Backend Endpoints:**
- `GET /api/backups` - List backups
- `POST /api/backups` - Create backup
- `POST /api/backups/upload` - Upload backup
- `GET /api/backups/{key}` - Download backup
- `DELETE /api/backups/{key}` - Delete backup
- `POST /api/backups/{key}/restore` - Restore backup

**Note**: All Backups API operations require superuser authentication (except download which requires a superuser file token).

> 📖 **Reference**: For detailed backup concepts, see the [JavaScript SDK Backups documentation](../js-sdk/docs/BACKUPS_API.md).

## Authentication

All Backups API operations require superuser authentication:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Authenticate as superuser
pb.admins.authWithPassword("admin@example.com", "password")
```

**Downloading backups** requires a superuser file token (obtained via `pb.files.getToken()`), but does not require the Authorization header.

## Backup File Structure

Each backup file contains:
- `key`: The filename/key of the backup file (string)
- `size`: File size in bytes (number)
- `modified`: ISO 8601 timestamp of when the backup was last modified (string)

```kotlin
data class BackupFileInfo(
    val key: String,
    val size: Long,
    val modified: String
)
```

## List Backups

Returns a list of all available backup files with their metadata.

### Basic Usage

```kotlin
// Get all backups
val backups = pb.backups.getFullList()

backups.forEach { backup ->
    println("Key: ${backup["key"]?.jsonPrimitive?.contentOrNull}")
    println("Size: ${backup["size"]?.jsonPrimitive?.longOrNull}")
    println("Modified: ${backup["modified"]?.jsonPrimitive?.contentOrNull}")
}
```

### Working with Backup Lists

```kotlin
// Sort backups by modification date (newest first)
val backups = pb.backups.getFullList()
val sorted = backups.sortedByDescending { backup ->
    backup["modified"]?.jsonPrimitive?.contentOrNull ?: ""
}

// Find the most recent backup
val mostRecent = sorted.firstOrNull()

// Filter backups by size (larger than 100MB)
val largeBackups = backups.filter { backup ->
    val size = backup["size"]?.jsonPrimitive?.longOrNull ?: 0L
    size > 100 * 1024 * 1024
}

// Get total storage used by backups
val totalSize = backups.sumOf { backup ->
    backup["size"]?.jsonPrimitive?.longOrNull ?: 0L
}
println("Total backup storage: ${totalSize / 1024 / 1024} MB")
```

## Create Backup

Creates a new backup of the application data. The backup process is asynchronous and may take some time depending on the size of your data.

```kotlin
// Create backup with auto-generated name
pb.backups.create(name = "")

// Create backup with custom name
pb.backups.create(name = "backup_20240115")
```

## Upload Backup

Upload an existing backup file to restore it later.

```kotlin
import com.bosbase.sdk.FileAttachment
import java.io.File

val backupFile = File("path/to/backup.zip")
val attachment = FileAttachment.fromFile(backupFile)

pb.backups.upload(
    body = mapOf("name" to "uploaded_backup"),
    files = mapOf("file" to listOf(attachment))
)
```

## Download Backup

Get a download URL for a backup file. Requires a superuser file token.

```kotlin
// Get file access token (must be authenticated as superuser)
val token = pb.files.getToken()

// Build download URL
val backupKey = "pb_backup_20240115.zip"
val downloadUrl = pb.backups.getDownloadURL(token = token, key = backupKey)

println("Download URL: $downloadUrl")
```

## Delete Backup

Delete a backup file by its key.

```kotlin
val backupKey = "pb_backup_20240115.zip"
pb.backups.delete(key = backupKey)
```

## Restore Backup

Restore the application from a backup. This operation restarts the application.

```kotlin
val backupKey = "pb_backup_20240115.zip"
pb.backups.restore(key = backupKey)

// Note: The application will restart after restore
```

## Complete Example

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError
import java.text.SimpleDateFormat
import java.util.*

fun backupWorkflow(pb: BosBase) {
    try {
        // Authenticate as superuser
        pb.admins.authWithPassword("admin@example.com", "password")
        
        // List existing backups
        val backups = pb.backups.getFullList()
        println("Existing backups: ${backups.size}")
        
        // Create new backup
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val backupName = "backup_${dateFormat.format(Date())}"
        pb.backups.create(name = backupName)
        println("Created backup: $backupName")
        
        // Wait a bit for backup to complete (in production, poll status)
        Thread.sleep(2000)
        
        // List backups again
        val updatedBackups = pb.backups.getFullList()
        val newBackup = updatedBackups.find { backup ->
            backup["key"]?.jsonPrimitive?.contentOrNull?.contains(backupName) == true
        }
        
        if (newBackup != null) {
            val size = newBackup["size"]?.jsonPrimitive?.longOrNull ?: 0L
            println("Backup size: ${size / 1024 / 1024} MB")
        }
        
        // Get download URL
        if (newBackup != null) {
            val token = pb.files.getToken()
            val key = newBackup["key"]?.jsonPrimitive?.contentOrNull ?: ""
            val downloadUrl = pb.backups.getDownloadURL(token = token, key = key)
            println("Download URL: $downloadUrl")
        }
        
    } catch (e: ClientResponseError) {
        println("Error: ${e.status} - ${e.response}")
    }
}
```

## Error Handling

```kotlin
import com.bosbase.sdk.ClientResponseError

try {
    pb.backups.create(name = "my_backup")
} catch (e: ClientResponseError) {
    when (e.status) {
        403 -> println("Access forbidden - superuser authentication required")
        400 -> println("Invalid backup name or parameters: ${e.response}")
        else -> println("Error: ${e.status}")
    }
}
```

