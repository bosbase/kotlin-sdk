# BosBase Kotlin SDK

Official Kotlin SDK for interacting with the [BosBase API](https://docs.bosbase.com/docs). This module mirrors the JavaScript SDK surface so JVM and Android apps can talk to the BosBase HTTP API with a small, coroutine-friendly wrapper built on OkHttp and `kotlinx.serialization`.

## Quick Start

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Authenticate against an auth collection
val auth = pb.collection("users").authWithPassword("test@example.com", "123456")
println(auth["token"])

// List records with filter/expand
val posts = pb.collection("posts").getList(page = 1, perPage = 10, expand = "author")
posts.items.forEach { post ->
    println(post["title"])
}

// Create a record (files supported via FileAttachment)
pb.collection("posts").create(
    mapOf("title" to "Hello Kotlin!")
)
```

> 📖 **New to BosBase?** Check out the [Quick Start Guide](docs/QUICK_START.md) to get up and running in minutes!

## Documentation

Comprehensive documentation is available in the [`docs/`](docs/) directory:

- **[Quick Start Guide](docs/QUICK_START.md)** - Get started in minutes
- **[Authentication](docs/AUTHENTICATION.md)** - User authentication, OAuth2, OTP, password reset
- **[API Records](docs/API_RECORDS.md)** - CRUD operations, filtering, sorting, relations
- **[Collections](docs/COLLECTIONS.md)** - Collection management, fields, indexes, API rules
- **[Files](docs/FILES.md)** - File uploads, downloads, thumbnails, protected files
- **[Realtime](docs/REALTIME.md)** - Real-time subscriptions and live updates

> 📚 **Reference**: The Kotlin SDK mirrors the JavaScript SDK API. For additional examples and concepts, see the [JavaScript SDK documentation](../js-sdk/docs/).

## Key Features

- `BosBase.send(...)` thin HTTP wrapper with `beforeSend`/`afterSend` hooks and auth header injection
- `pb.collection("name")` exposes record CRUD, auth helpers (`authWithPassword`, `authRefresh`, OTP), and convenience counters
- `pb.collections` exposes collection CRUD plus scaffold helpers (`createBase`, `createAuth`, `createView`)
- `pb.files` builds download URLs and requests private file tokens
- Services mirror the JS SDK: collections, records, logs, realtime, health, backups, crons, vectors, LLM documents, LangChaingo, caches, settings, and transactional batch operations
- Filter helper `pb.filter("title ~ {:title}", mapOf("title" to "demo"))` mirrors the JS SDK escaping rules
- Multipart uploads via `FileAttachment.fromFile(...)` or `FileAttachment(filename, bytes, contentType)`
- Persistent auth stores (`LocalAuthStore`, `AsyncAuthStore`) for JVM and Android
- Automatic token refresh and reconnection handling

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.bosbase:bosbase-kotlin-sdk:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.bosbase:bosbase-kotlin-sdk:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.bosbase</groupId>
    <artifactId>bosbase-kotlin-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Requirements

- **Kotlin**: 1.8.0+
- **Java**: 11+
- **Gradle**: 8.x (for building)

The SDK is compatible with:
- JVM applications (desktop/server)
- Android applications
- Any Kotlin/JVM project

## Examples

### Authentication

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Password authentication
val authData = pb.collection("users").authWithPassword(
    identity = "user@example.com",
    password = "password123"
)

// Check auth status
println(pb.authStore.isValid)  // true
println(pb.authStore.token)     // JWT token

// Logout
pb.authStore.clear()
```

### CRUD Operations

```kotlin
// Create
val newPost = pb.collection("posts").create(
    body = mapOf(
        "title" to "My Post",
        "content" to "Post content"
    )
)

// Read
val post = pb.collection("posts").getOne("RECORD_ID")

// List with filter
val results = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    filter = "status = 'published'",
    sort = "-created"
)

// Update
val updated = pb.collection("posts").update(
    id = "RECORD_ID",
    body = mapOf("title" to "Updated Title")
)

// Delete
pb.collection("posts").delete("RECORD_ID")
```

### File Upload

```kotlin
import com.bosbase.sdk.FileAttachment
import java.io.File

val attachment = FileAttachment.fromFile(File("image.jpg"))

val record = pb.collection("posts").create(
    body = mapOf("title" to "Post with Image"),
    files = mapOf("image" to listOf(attachment))
)
```

### Realtime Subscriptions

```kotlin
val unsubscribe = pb.collection("posts").subscribe("*") { event ->
    val action = event["action"]?.toString()  // 'create', 'update', 'delete'
    val record = event["record"]
    println("$action: $record")
}

// Later...
unsubscribe()
```

For more examples, see the [documentation](docs/).

## Building Locally

The module uses Gradle with the Kotlin DSL. From `kotlin-sdk/`:

```bash
gradle build  # or ./gradlew build if you add a wrapper
```

The resulting JVM artifact targets Java 11+. The code avoids Android-specific APIs so it works in both server and Android projects when packaged.

## Publishing

This library is published to Maven Central. For instructions on how to publish new versions, see [PUBLISHING.md](PUBLISHING.md).

### Current Version

The current published version is `0.1.0`. Check [Maven Central](https://central.sonatype.com/artifact/com.bosbase/bosbase-kotlin-sdk) for the latest version.

## License

See [LICENSE](LICENSE) file for details.
