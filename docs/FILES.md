# Files Upload and Handling - Kotlin SDK Documentation

## Overview

BosBase allows you to upload and manage files through file fields in your collections. Files are stored with sanitized names and a random suffix for security (e.g., `test_52iwbgds7l.png`).

**Key Features:**
- Upload multiple files per field
- Maximum file size: ~8GB (2^53-1 bytes)
- Automatic filename sanitization and random suffix
- Image thumbnails support
- Protected files with token-based access
- File modifiers for append/prepend/delete operations

**Backend Endpoints:**
- `POST /api/files/token` - Get file access token for protected files
- `GET /api/files/{collection}/{recordId}/{filename}` - Download file

> 📖 **Reference**: For detailed file handling concepts, see the [JavaScript SDK Files documentation](../js-sdk/docs/FILES.md).

## File Field Configuration

Before uploading files, you must add a file field to your collection:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
// Authenticate as admin first
pb.admins.authWithPassword("admin@example.com", "password")

val collection = pb.collections.getOne("example")
val fields = (collection["fields"]?.jsonArray?.toMutableList() ?: mutableListOf()).toMutableList()

fields.add(
    mapOf(
        "name" to "documents",
        "type" to "file",
        "maxSelect" to 5,        // Maximum number of files (1 for single file)
        "maxSize" to 5242880,    // 5MB in bytes (optional, default: 5MB)
        "mimeTypes" to listOf("image/jpeg", "image/png", "application/pdf"),
        "thumbs" to listOf("100x100", "300x300"),  // Thumbnail sizes for images
        "protected" to false     // Require token for access
    )
)

pb.collections.update("example", body = mapOf("fields" to fields))
```

## FileAttachment

The Kotlin SDK uses `FileAttachment` objects to represent files for upload:

```kotlin
import com.bosbase.sdk.FileAttachment
import java.io.File

// From file path
val attachment = FileAttachment.fromFile(File("path/to/image.jpg"))

// From bytes
val bytes = "file content".toByteArray()
val attachment = FileAttachment(
    filename = "document.txt",
    bytes = bytes,
    contentType = "text/plain"
)

// With custom content type
val attachment = FileAttachment(
    filename = "data.json",
    bytes = jsonBytes,
    contentType = "application/json"
)
```

## Uploading Files

### Basic Upload with Create

When creating a new record, you can upload files directly:

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.FileAttachment
import java.io.File

val pb = BosBase("http://localhost:8090")

// Upload single file
val file = File("path/to/image.jpg")
val attachment = FileAttachment.fromFile(file)

val createdRecord = pb.collection("example").create(
    body = mapOf(
        "title" to "Hello world!"
    ),
    files = mapOf(
        "image" to listOf(attachment)
    )
)
```

### Upload Multiple Files

```kotlin
val attachments = listOf(
    FileAttachment.fromFile(File("file1.txt")),
    FileAttachment.fromFile(File("file2.txt"))
)

val createdRecord = pb.collection("example").create(
    body = mapOf("title" to "Multiple Files"),
    files = mapOf(
        "documents" to attachments
    )
)
```

### Upload with Update

```kotlin
// Update record and upload new files
val updatedRecord = pb.collection("example").update(
    id = "RECORD_ID",
    body = mapOf("title" to "Updated title"),
    files = mapOf(
        "documents" to listOf(FileAttachment.fromFile(File("new-file.pdf")))
    )
)
```

## File URLs

### Get File URL

Build URLs to access files:

```kotlin
import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.jsonObject

val pb = BosBase("http://localhost:8090")

// Get a record with file field
val record = pb.collection("example").getOne("RECORD_ID")

// Get file URL
val filename = record["image"]?.jsonPrimitive?.contentOrNull
if (filename != null) {
    val fileUrl = pb.files.getURL(
        record = record.jsonObject,
        filename = filename
    )
    println("File URL: $fileUrl")
}
```

### Thumbnails

Get thumbnail URLs for images:

```kotlin
val fileUrl = pb.files.getURL(
    record = record.jsonObject,
    filename = filename,
    thumb = "100x100"  // Thumbnail size
)
```

### Protected Files

For protected files, you need to get an access token:

```kotlin
// Get file access token (must be authenticated)
val token = pb.files.getToken()

// Build URL with token
val fileUrl = pb.files.getURL(
    record = record.jsonObject,
    filename = filename,
    token = token
)
```

### Download Files

Force download instead of display:

```kotlin
val fileUrl = pb.files.getURL(
    record = record.jsonObject,
    filename = filename,
    download = true
)
```

## File Modifiers

Modify existing file fields (append, prepend, delete):

### Append Files

```kotlin
val newFiles = listOf(
    FileAttachment.fromFile(File("new-file1.pdf")),
    FileAttachment.fromFile(File("new-file2.pdf"))
)

val updated = pb.collection("example").update(
    id = "RECORD_ID",
    body = mapOf(
        "documents+" to newFiles  // Append files
    )
)
```

### Prepend Files

```kotlin
val updated = pb.collection("example").update(
    id = "RECORD_ID",
    body = mapOf(
        "documents!" to listOf(FileAttachment.fromFile(File("prepended-file.pdf")))  // Prepend file
    )
)
```

### Delete Files

```kotlin
// Delete specific files by filename
val updated = pb.collection("example").update(
    id = "RECORD_ID",
    body = mapOf(
        "documents-" to listOf("old-file.pdf", "another-file.pdf")  // Delete files
    )
)
```

## Examples

### Complete File Upload Example

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.FileAttachment
import java.io.File

fun uploadPostWithImage(pb: BosBase, title: String, content: String, imagePath: String) {
    val imageFile = File(imagePath)
    val attachment = FileAttachment.fromFile(imageFile)
    
    val post = pb.collection("posts").create(
        body = mapOf(
            "title" to title,
            "content" to content
        ),
        files = mapOf(
            "image" to listOf(attachment)
        )
    )
    
    println("Post created with image: ${post["id"]?.jsonPrimitive?.contentOrNull}")
}
```

### Multiple File Upload

```kotlin
fun uploadMultipleFiles(pb: BosBase, recordId: String, filePaths: List<String>) {
    val attachments = filePaths.map { FileAttachment.fromFile(File(it)) }
    
    val updated = pb.collection("documents").update(
        id = recordId,
        body = mapOf("title" to "Updated"),
        files = mapOf(
            "files" to attachments
        )
    )
    
    println("Uploaded ${attachments.size} files")
}
```

### Display Image with Thumbnail

```kotlin
fun displayImageWithThumbnail(pb: BosBase, recordId: String) {
    val record = pb.collection("posts").getOne(recordId)
    val filename = record["image"]?.jsonPrimitive?.contentOrNull
    
    if (filename != null) {
        // Full image URL
        val fullImageUrl = pb.files.getURL(
            record = record.jsonObject,
            filename = filename
        )
        
        // Thumbnail URL
        val thumbnailUrl = pb.files.getURL(
            record = record.jsonObject,
            filename = filename,
            thumb = "300x300"
        )
        
        println("Full image: $fullImageUrl")
        println("Thumbnail: $thumbnailUrl")
    }
}
```

### Protected File Access

```kotlin
fun accessProtectedFile(pb: BosBase, recordId: String) {
    // Must be authenticated
    val record = pb.collection("private_docs").getOne(recordId)
    val filename = record["document"]?.jsonPrimitive?.contentOrNull
    
    if (filename != null) {
        // Get access token
        val token = pb.files.getToken()
        
        // Build protected URL
        val fileUrl = pb.files.getURL(
            record = record.jsonObject,
            filename = filename,
            token = token
        )
        
        println("Protected file URL: $fileUrl")
    }
}
```

### File Management (Append/Delete)

```kotlin
fun manageFiles(pb: BosBase, recordId: String) {
    // Append new files
    val newFiles = listOf(
        FileAttachment.fromFile(File("new-doc1.pdf")),
        FileAttachment.fromFile(File("new-doc2.pdf"))
    )
    
    pb.collection("documents").update(
        id = recordId,
        body = mapOf(
            "documents+" to newFiles  // Append
        )
    )
    
    // Later, delete specific files
    pb.collection("documents").update(
        id = recordId,
        body = mapOf(
            "documents-" to listOf("old-doc.pdf")  // Delete
        )
    )
}
```

### Android File Upload Example

```kotlin
import android.content.Context
import android.net.Uri
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.FileAttachment
import java.io.InputStream

fun uploadImageFromUri(context: Context, pb: BosBase, uri: Uri) {
    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
    val bytes = inputStream?.readBytes()
    
    if (bytes != null) {
        val attachment = FileAttachment(
            filename = "image.jpg",
            bytes = bytes,
            contentType = "image/jpeg"
        )
        
        val record = pb.collection("images").create(
            body = mapOf("title" to "Uploaded Image"),
            files = mapOf("image" to listOf(attachment))
        )
        
        println("Image uploaded: ${record["id"]?.jsonPrimitive?.contentOrNull}")
    }
}
```

