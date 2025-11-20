# Collections Extra - Kotlin SDK Documentation

This document provides comprehensive documentation for working with Collections and Fields in the BosBase Kotlin SDK. This documentation is designed to be AI-readable and includes practical examples for all operations.

> 📖 **Reference**: For detailed collection concepts, see the [JavaScript SDK Collections Extra documentation](../js-sdk/docs/COLLECTIONS_EXTRA.md) and [COLLECTIONS.md](COLLECTIONS.md).

## Overview

**Collections** represent your application data. Under the hood they are backed by plain SQLite tables that are generated automatically with the collection **name** and **fields** (columns).

A single entry of a collection is called a **record** (a single row in the SQL table).

You can manage your **collections** from the Dashboard, or with the Kotlin SDK using the `collections` service.

Similarly, you can manage your **records** from the Dashboard, or with the Kotlin SDK using the `collection(name)` method which returns a `RecordService` instance.

## Collection Types

Currently there are 3 collection types: **Base**, **View** and **Auth**.

### Base Collection

**Base collection** is the default collection type and it could be used to store any application data (articles, products, posts, etc.).

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
pb.admins.authWithPassword("admin@example.com", "password")

// Create a base collection
val collection = pb.collections.createBase(
    name = "articles",
    overrides = mapOf(
        "fields" to listOf(
            mapOf(
                "name" to "title",
                "type" to "text",
                "required" to true,
                "min" to 6,
                "max" to 100
            ),
            mapOf(
                "name" to "description",
                "type" to "text"
            )
        ),
        "listRule" to "@request.auth.id != \"\" || status = \"public\"",
        "viewRule" to "@request.auth.id != \"\" || status = \"public\""
    )
)
```

### View Collection

**View collection** is a read-only collection type where the data is populated from a plain SQL `SELECT` statement.

```kotlin
// Create a view collection
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

**Note**: View collections don't receive realtime events because they don't have create/update/delete operations.

### Auth Collection

**Auth collection** has everything from the **Base collection** but with some additional special fields to help you manage your app users and also provide various authentication options.

```kotlin
// Create an auth collection
val usersCollection = pb.collections.createAuth(
    name = "users",
    overrides = mapOf(
        "fields" to listOf(
            mapOf(
                "name" to "name",
                "type" to "text",
                "required" to true
            ),
            mapOf(
                "name" to "role",
                "type" to "select",
                "options" to mapOf(
                    "values" to listOf("employee", "staff", "admin")
                )
            )
        )
    )
)
```

## Field Types

See [COLLECTIONS.md](COLLECTIONS.md) for detailed field type documentation.

## Examples

### Complete Collection Setup

```kotlin
fun setupCollections(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Create base collection
    val articles = pb.collections.createBase(
        name = "articles",
        overrides = mapOf(
            "fields" to listOf(
                mapOf("name" to "title", "type" to "text", "required" to true),
                mapOf("name" to "content", "type" to "editor", "required" to true),
                mapOf("name" to "status", "type" to "select", "options" to mapOf(
                    "values" to listOf("draft", "published", "archived")
                )),
                mapOf("name" to "author", "type" to "relation", "options" to mapOf(
                    "collectionId" to "users", "maxSelect" to 1
                ))
            )
        )
    )
    
    println("Created collection: ${articles["name"]?.jsonPrimitive?.contentOrNull}")
}
```

