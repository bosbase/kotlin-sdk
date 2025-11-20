# AI Development Guide - Kotlin SDK Documentation

This guide provides a comprehensive, fast reference for AI systems to quickly develop applications using the BosBase Kotlin SDK. All examples are production-ready and follow best practices.

> 📖 **Reference**: For detailed AI development concepts, see the [JavaScript SDK AI Development Guide](../js-sdk/docs/AI_DEVELOPMENT_GUIDE.md).

## Table of Contents

1. [Authentication](#authentication)
2. [Initialize Collections](#initialize-collections)
3. [Define Collection Fields](#define-collection-fields)
4. [Add Data to Collections](#add-data-to-collections)
5. [Modify Collection Data](#modify-collection-data)
6. [Delete Data from Collections](#delete-data-from-collections)
7. [Query Collection Contents](#query-collection-contents)
8. [Add and Delete Fields from Collections](#add-and-delete-fields-from-collections)
9. [Query Collection Field Information](#query-collection-field-information)
10. [Upload Files](#upload-files)
11. [Query Logs](#query-logs)
12. [Send Emails](#send-emails)

## Authentication

### Initialize Client

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
```

### Password Authentication

```kotlin
// Authenticate with email/username and password
val authData = pb.collection("users").authWithPassword(
    identity = "user@example.com",
    password = "password123"
)

// Auth data is automatically stored
println("Is valid: ${pb.authStore.isValid}")  // true
println("Token: ${pb.authStore.token?.take(20)}...")  // JWT token
println("User ID: ${pb.authStore.model?.get("id")?.jsonPrimitive?.contentOrNull}")
```

### OAuth2 Authentication

```kotlin
// Get OAuth2 providers
val methods = pb.collection("users").listAuthMethods()
val oauth2 = methods["oauth2"]?.jsonObject
val providers = oauth2?.get("providers")?.jsonArray
println("Available providers: ${providers?.size ?: 0}")

// Authenticate with OAuth2
val oauthData = pb.collection("users").authWithOAuth2(
    provider = "google",
    urlCallback = "myapp://oauth-callback"
)

// Handle OAuth2 callback
val authData = pb.collection("users").authWithOAuth2Code(
    provider = "google",
    code = "authorization_code",
    codeVerifier = oauthData["codeVerifier"]?.jsonPrimitive?.contentOrNull ?: "",
    redirectUrl = "myapp://oauth-callback"
)
```

### Check Authentication Status

```kotlin
if (pb.authStore.isValid) {
    val email = pb.authStore.model?.get("email")?.jsonPrimitive?.contentOrNull
    println("Authenticated as: $email")
} else {
    println("Not authenticated")
}
```

### Logout

```kotlin
pb.authStore.clear()
```

## Initialize Collections

### Create Base Collection

```kotlin
val collection = pb.collections.create(
    body = mapOf(
        "type" to "base",
        "name" to "articles",
        "fields" to listOf(
            mapOf("name" to "title", "type" to "text", "required" to true),
            mapOf("name" to "content", "type" to "text")
        )
    )
)
```

### Create Auth Collection

```kotlin
val usersCollection = pb.collections.createAuth(
    name = "users",
    overrides = mapOf(
        "fields" to listOf(
            mapOf("name" to "name", "type" to "text", "required" to true)
        )
    )
)
```

### Create View Collection

```kotlin
val viewCollection = pb.collections.createView(
    name = "post_stats",
    viewQuery = """
        SELECT posts.id, posts.name, count(comments.id) as totalComments 
        FROM posts 
        LEFT JOIN comments on comments.postId = posts.id 
        GROUP BY posts.id
    """.trimIndent()
)
```

## Define Collection Fields

```kotlin
val collection = pb.collections.getOne("articles")
val fields = (collection["fields"]?.jsonArray?.toMutableList() ?: mutableListOf()).toMutableList()

// Add new field
fields.add(
    mapOf(
        "name" to "description",
        "type" to "text",
        "required" to false,
        "max" to 500
    )
)

pb.collections.update(
    idOrName = "articles",
    body = mapOf("fields" to fields)
)
```

## Add Data to Collections

```kotlin
// Create a record
val newPost = pb.collection("articles").create(
    body = mapOf(
        "title" to "My Article",
        "content" to "Article content here",
        "description" to "Article description"
    )
)

println("Created record ID: ${newPost["id"]?.jsonPrimitive?.contentOrNull}")
```

## Modify Collection Data

```kotlin
// Update a record
val updated = pb.collection("articles").update(
    id = "RECORD_ID",
    body = mapOf(
        "title" to "Updated Title",
        "content" to "Updated content"
    )
)
```

## Delete Data from Collections

```kotlin
// Delete a record
pb.collection("articles").delete("RECORD_ID")
```

## Query Collection Contents

```kotlin
// List records with pagination
val result = pb.collection("articles").getList(
    page = 1,
    perPage = 50,
    filter = "status = \"published\"",
    sort = "-created"
)

result.items.forEach { article ->
    val title = article["title"]?.jsonPrimitive?.contentOrNull
    println("Article: $title")
}

// Get single record
val article = pb.collection("articles").getOne(
    id = "RECORD_ID",
    expand = "author"
)
```

## Add and Delete Fields from Collections

```kotlin
// Add field
pb.collections.addField(
    collectionIdOrName = "articles",
    field = mapOf(
        "name" to "tags",
        "type" to "select",
        "options" to mapOf(
            "values" to listOf("tech", "news", "tutorial")
        )
    )
)

// Delete field
pb.collections.removeField(
    collectionIdOrName = "articles",
    fieldName = "description"
)
```

## Query Collection Field Information

```kotlin
// Get collection schema
val schema = pb.collections.getSchema("articles")

val fields = schema?.get("fields")?.jsonArray
fields?.forEach { field ->
    val fieldObj = field.jsonObject
    val name = fieldObj["name"]?.jsonPrimitive?.contentOrNull
    val type = fieldObj["type"]?.jsonPrimitive?.contentOrNull
    println("Field: $name ($type)")
}
```

## Upload Files

```kotlin
import com.bosbase.sdk.FileAttachment
import java.io.File

val file = File("path/to/image.jpg")
val attachment = FileAttachment.fromFile(file)

val record = pb.collection("articles").create(
    body = mapOf("title" to "Article with Image"),
    files = mapOf("image" to listOf(attachment))
)
```

## Query Logs

```kotlin
// Query logs (requires superuser)
pb.admins.authWithPassword("admin@example.com", "password")

val logs = pb.logs.getList(
    page = 1,
    perPage = 50,
    filter = "data.status >= 400",
    sort = "-created"
)

logs?.get("items")?.jsonArray?.forEach { log ->
    val data = log.jsonObject["data"]?.jsonObject
    val status = data?.get("status")?.jsonPrimitive?.intOrNull
    val url = data?.get("url")?.jsonPrimitive?.contentOrNull
    println("$status $url")
}
```

## Send Emails

```kotlin
// Test email (requires superuser)
pb.admins.authWithPassword("admin@example.com", "password")

pb.settings.testEmail(
    email = "test@example.com",
    template = "verification",
    collectionIdOrName = "users"
)
```

