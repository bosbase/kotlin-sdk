# File API - Kotlin SDK Documentation

## Overview

The File API provides endpoints for downloading and accessing files stored in collection records. It supports thumbnail generation for images, protected file access with tokens, and force download options.

**Key Features:**
- Download files from collection records
- Generate thumbnails for images (crop, fit, resize)
- Protected file access with short-lived tokens
- Force download option for any file type
- Automatic content-type detection

**Backend Endpoints:**
- `GET /api/files/{collection}/{recordId}/{filename}` - Download/fetch file
- `POST /api/files/token` - Generate protected file token

> 📖 **Reference**: For detailed file handling concepts, see the [JavaScript SDK File API documentation](../js-sdk/docs/FILE_API.md) and [FILES.md](FILES.md).

## Download / Fetch File

Downloads a single file resource from a record.

### Basic Usage

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Get a record with a file field
val record = pb.collection("posts").getOne("RECORD_ID")
val filename = record["image"]?.jsonPrimitive?.contentOrNull

// Get the file URL
if (filename != null) {
    val fileUrl = pb.files.getURL(
        record = record.jsonObject,
        filename = filename
    )
    println("File URL: $fileUrl")
}
```

## Thumbnails

Generate thumbnails for image files on-the-fly.

### Thumbnail Formats

The following thumbnail formats are supported:

| Format | Example | Description |
|--------|---------|-------------|
| `WxH` | `100x300` | Crop to WxH viewbox (from center) |
| `WxHt` | `100x300t` | Crop to WxH viewbox (from top) |
| `WxHb` | `100x300b` | Crop to WxH viewbox (from bottom) |
| `WxHf` | `100x300f` | Fit inside WxH viewbox (without cropping) |
| `0xH` | `0x300` | Resize to H height preserving aspect ratio |
| `Wx0` | `100x0` | Resize to W width preserving aspect ratio |

### Using Thumbnails

```kotlin
// Get thumbnail URL
val record = pb.collection("posts").getOne("RECORD_ID")
val filename = record["image"]?.jsonPrimitive?.contentOrNull

if (filename != null) {
    val thumbUrl = pb.files.getURL(
        record = record.jsonObject,
        filename = filename,
        thumb = "100x100"
    )
    println("Thumbnail URL: $thumbUrl")
    
    // Different thumbnail sizes
    val smallThumb = pb.files.getURL(
        record = record.jsonObject,
        filename = filename,
        thumb = "50x50"
    )
    
    val mediumThumb = pb.files.getURL(
        record = record.jsonObject,
        filename = filename,
        thumb = "200x200"
    )
}
```

## Protected Files

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

## Download Files

Force download instead of display:

```kotlin
val fileUrl = pb.files.getURL(
    record = record.jsonObject,
    filename = filename,
    download = true
)
```

## Complete Example

```kotlin
fun fileAccessExample(pb: BosBase) {
    // Get record with file
    val record = pb.collection("posts").getOne("RECORD_ID")
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
        
        // Protected file URL (if needed)
        val token = pb.files.getToken()
        val protectedUrl = pb.files.getURL(
            record = record.jsonObject,
            filename = filename,
            token = token
        )
        
        println("Full image: $fullImageUrl")
        println("Thumbnail: $thumbnailUrl")
        println("Protected: $protectedUrl")
    }
}
```

