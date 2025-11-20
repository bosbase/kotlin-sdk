# Quick Start Guide - Kotlin SDK

This guide will help you get started with the BosBase Kotlin SDK in just a few minutes.

## Installation

### Gradle (Kotlin DSL)

Add the SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.bosbase:bosbase-kotlin-sdk:0.1.0")
}
```

### Gradle (Groovy)

Add to your `build.gradle`:

```groovy
dependencies {
    implementation 'com.bosbase:bosbase-kotlin-sdk:0.1.0'
}
```

### Maven

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.bosbase</groupId>
    <artifactId>bosbase-kotlin-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Basic Setup

### 1. Create a Client Instance

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")
```

### 2. Authenticate

```kotlin
// Authenticate with email and password
val authData = pb.collection("users").authWithPassword(
    identity = "test@example.com",
    password = "123456"
)

println("Authenticated: ${authData["token"]?.jsonPrimitive?.contentOrNull}")
```

### 3. Create a Record

```kotlin
val newPost = pb.collection("posts").create(
    body = mapOf(
        "title" to "Hello Kotlin!",
        "content" to "This is my first post"
    )
)

println("Created post ID: ${newPost["id"]?.jsonPrimitive?.contentOrNull}")
```

### 4. List Records

```kotlin
val result = pb.collection("posts").getList(page = 1, perPage = 10)

result.items.forEach { post ->
    println("Title: ${post["title"]?.jsonPrimitive?.contentOrNull}")
}
```

### 5. Update a Record

```kotlin
val updated = pb.collection("posts").update(
    id = "RECORD_ID",
    body = mapOf("title" to "Updated Title")
)
```

### 6. Delete a Record

```kotlin
pb.collection("posts").delete("RECORD_ID")
```

## Complete Example

Here's a complete example that demonstrates the basic workflow:

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun main() {
    val pb = BosBase("http://127.0.0.1:8090")
    
    try {
        // 1. Authenticate
        val authData = pb.collection("users").authWithPassword(
            identity = "test@example.com",
            password = "123456"
        )
        println("✅ Authenticated")
        
        // 2. Create a record
        val newPost = pb.collection("posts").create(
            body = mapOf(
                "title" to "My First Post",
                "content" to "This is the content of my post",
                "status" to "published"
            )
        )
        val postId = newPost["id"]?.jsonPrimitive?.contentOrNull
        println("✅ Created post: $postId")
        
        // 3. Get the record
        val post = pb.collection("posts").getOne(postId ?: "")
        println("✅ Retrieved post: ${post["title"]?.jsonPrimitive?.contentOrNull}")
        
        // 4. Update the record
        val updated = pb.collection("posts").update(
            id = postId ?: "",
            body = mapOf("title" to "Updated Title")
        )
        println("✅ Updated post")
        
        // 5. List records with filter
        val results = pb.collection("posts").getList(
            page = 1,
            perPage = 10,
            filter = "status = 'published'",
            sort = "-created"
        )
        println("✅ Found ${results.totalItems} published posts")
        
        // 6. Delete the record
        pb.collection("posts").delete(postId ?: "")
        println("✅ Deleted post")
        
    } catch (e: ClientResponseError) {
        println("❌ Error: ${e.status} - ${e.response}")
    }
}
```

## Using with Coroutines

The SDK works great with Kotlin coroutines:

```kotlin
import kotlinx.coroutines.runBlocking
import com.bosbase.sdk.BosBase

fun main() = runBlocking {
    val pb = BosBase("http://127.0.0.1:8090")
    
    // All SDK methods are synchronous but can be called from coroutines
    val posts = pb.collection("posts").getList(page = 1, perPage = 10)
    
    posts.items.forEach { post ->
        println(post["title"]?.jsonPrimitive?.contentOrNull)
    }
}
```

## Next Steps

- 📖 Read the [Authentication Guide](AUTHENTICATION.md) for detailed auth examples
- 📖 Read the [API Records Guide](API_RECORDS.md) for CRUD operations
- 📖 Read the [Collections Guide](COLLECTIONS.md) for collection management
- 📖 Read the [Files Guide](FILES.md) for file uploads
- 📖 Read the [Realtime Guide](REALTIME.md) for real-time updates

## Common Patterns

### Error Handling

```kotlin
import com.bosbase.sdk.ClientResponseError

try {
    val record = pb.collection("posts").getOne("INVALID_ID")
} catch (e: ClientResponseError) {
    when (e.status) {
        404 -> println("Record not found")
        403 -> println("Access forbidden")
        400 -> println("Bad request: ${e.response}")
        else -> println("Error: ${e.status}")
    }
}
```

### Filtering with Parameters

```kotlin
// Safe parameter binding
val searchTerm = "user's post"
val filter = pb.filter(
    "title ~ {:term} && views > {:minViews}",
    mapOf(
        "term" to searchTerm,
        "minViews" to 100
    )
)

val results = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    filter = filter
)
```

### File Upload

```kotlin
import com.bosbase.sdk.FileAttachment
import java.io.File

val file = File("path/to/image.jpg")
val attachment = FileAttachment.fromFile(file)

val record = pb.collection("posts").create(
    body = mapOf("title" to "Post with Image"),
    files = mapOf("image" to listOf(attachment))
)
```

### Realtime Subscriptions

```kotlin
val unsubscribe = pb.collection("posts").subscribe("*") { event ->
    val action = event["action"]?.toString()
    val record = event["record"]
    println("$action: $record")
}

// Later...
unsubscribe()
```

## Requirements

- **Kotlin**: 1.8.0+
- **Java**: 11+
- **Gradle**: 8.x (for building)

The SDK is compatible with:
- JVM applications (desktop/server)
- Android applications
- Any Kotlin/JVM project

## Getting Help

- 📚 Check the [full documentation](../README.md)
- 📖 Browse the [JavaScript SDK docs](../js-sdk/docs/) for additional examples
- 🐛 Report issues on GitHub

