# Collections - Kotlin SDK Documentation

## Overview

**Collections** represent your application data. Under the hood they are backed by plain SQLite tables that are generated automatically with the collection **name** and **fields** (columns).

A single entry of a collection is called a **record** (a single row in the SQL table).

> 📖 **Reference**: For detailed collection concepts, see the [JavaScript SDK Collections documentation](../js-sdk/docs/COLLECTIONS.md).

## Collection Types

### Base Collection

Default collection type for storing any application data.

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
// Authenticate as admin
pb.admins.authWithPassword("admin@example.com", "password")

val collection = pb.collections.createBase(
    name = "articles",
    overrides = mapOf(
        "fields" to listOf(
            mapOf("name" to "title", "type" to "text", "required" to true),
            mapOf("name" to "description", "type" to "text")
        )
    )
)
```

### View Collection

Read-only collection populated from a SQL SELECT statement.

```kotlin
val view = pb.collections.createView(
    name = "post_stats",
    viewQuery = """
        SELECT posts.id, posts.name, count(comments.id) as totalComments 
        FROM posts LEFT JOIN comments on comments.postId = posts.id 
        GROUP BY posts.id
    """.trimIndent()
)
```

### Auth Collection

Base collection with authentication fields (email, password, etc.).

```kotlin
val users = pb.collections.createAuth(
    name = "users",
    overrides = mapOf(
        "fields" to listOf(
            mapOf("name" to "name", "type" to "text", "required" to true)
        )
    )
)
```

## Collections API

### List Collections

```kotlin
// Paginated list
val result = pb.collections.getList(page = 1, perPage = 50)

// Get all collections
val all = pb.collections.getFullList()
```

### Get Collection

```kotlin
val collection = pb.collections.getOne("articles")
```

### Create Collection

```kotlin
// Using scaffolds
val base = pb.collections.createBase("articles")
val auth = pb.collections.createAuth("users")
val view = pb.collections.createView("stats", "SELECT * FROM posts")

// Manual creation
val collection = pb.collections.create(
    body = mapOf(
        "type" to "base",
        "name" to "articles",
        "fields" to listOf(
            mapOf("name" to "title", "type" to "text", "required" to true),
            mapOf(
                "name" to "created",
                "type" to "autodate",
                "required" to false,
                "onCreate" to true,
                "onUpdate" to false
            ),
            mapOf(
                "name" to "updated",
                "type" to "autodate",
                "required" to false,
                "onCreate" to true,
                "onUpdate" to true
            )
        )
    )
)
```

### Update Collection

```kotlin
val updated = pb.collections.update(
    idOrName = "articles",
    body = mapOf(
        "name" to "blog_articles",
        "fields" to listOf(
            // Updated fields
        )
    )
)
```

### Delete Collection

```kotlin
pb.collections.delete("articles")
```

### Truncate Collection

Delete all records in a collection without deleting the collection itself:

```kotlin
pb.collections.truncate("articles")
```

## Field Management

### Add Field

```kotlin
val newField = mapOf(
    "name" to "description",
    "type" to "text",
    "required" to false
)

pb.collections.addField(
    collectionIdOrName = "articles",
    field = newField
)
```

### Update Field

```kotlin
pb.collections.updateField(
    collectionIdOrName = "articles",
    fieldName = "description",
    updates = mapOf(
        "required" to true,
        "max" to 500
    )
)
```

### Remove Field

```kotlin
pb.collections.removeField(
    collectionIdOrName = "articles",
    fieldName = "description"
)
```

### Get Field

```kotlin
val field = pb.collections.getField(
    collectionIdOrName = "articles",
    fieldName = "title"
)
```

## Index Management

Indexes are managed by updating the collection's `indexes` field:

```kotlin
// Get current collection
val collection = pb.collections.getOne("articles")
val currentIndexes = (collection["indexes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toMutableList() ?: mutableListOf()

// Add single column index
currentIndexes.add("CREATE INDEX idx_title ON articles(title)")

// Add multi-column index
currentIndexes.add("CREATE INDEX idx_title_created ON articles(title, created)")

// Add unique index
currentIndexes.add("CREATE UNIQUE INDEX idx_slug ON articles(slug)")

// Update collection with new indexes
pb.collections.update("articles", body = mapOf(
    "indexes" to currentIndexes
))

// Get current indexes
val indexes = (collection["indexes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
```

### Remove Index

```kotlin
val collection = pb.collections.getOne("articles")
val indexes = (collection["indexes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toMutableList() ?: mutableListOf()

// Remove specific index
indexes.removeAll { it.contains("idx_title") }

pb.collections.update("articles", body = mapOf(
    "indexes" to indexes
))
```

## API Rules Management

API rules are managed by updating the collection's rule fields:

```kotlin
// Get current collection
val collection = pb.collections.getOne("articles")

// Update rules via collection update
pb.collections.update("articles", body = mapOf(
    "listRule" to "@request.auth.id != ''",      // List rule
    "viewRule" to "@request.auth.id != ''",      // View rule
    "createRule" to "@request.auth.id != ''",    // Create rule
    "updateRule" to "@request.auth.id = author.id",  // Update rule
    "deleteRule" to "@request.auth.id = author.id || @request.auth.isSuperuser = true"  // Delete rule
))

// Get current rules
val listRule = collection["listRule"]?.jsonPrimitive?.contentOrNull
val viewRule = collection["viewRule"]?.jsonPrimitive?.contentOrNull
val createRule = collection["createRule"]?.jsonPrimitive?.contentOrNull
val updateRule = collection["updateRule"]?.jsonPrimitive?.contentOrNull
val deleteRule = collection["deleteRule"]?.jsonPrimitive?.contentOrNull

println("List rule: $listRule")
println("View rule: $viewRule")
```

### Setting Individual Rules

```kotlin
// Set only list rule
pb.collections.update("articles", body = mapOf(
    "listRule" to "status = 'published' || @request.auth.id != ''"
))

// Set only view rule
pb.collections.update("articles", body = mapOf(
    "viewRule" to "@request.auth.id != ''"
))

// Clear a rule (allow anyone)
pb.collections.update("articles", body = mapOf(
    "listRule" to ""
))
```

### Auth Collection Rules

For auth collections, you can also set manage and auth rules:

```kotlin
pb.collections.update("users", body = mapOf(
    "manageRule" to "@request.auth.id = id || @request.auth.isSuperuser = true",
    "authRule" to "verified = true"
))
```

## Collection Import

Import multiple collections at once:

```kotlin
val collections = listOf(
    mapOf(
        "type" to "base",
        "name" to "articles",
        "fields" to listOf(/* ... */)
    ),
    mapOf(
        "type" to "auth",
        "name" to "users",
        "fields" to listOf(/* ... */)
    )
)

pb.collections.import(
    collections = collections,
    deleteMissing = false  // Set to true to delete collections not in import
)
```

## Collection Scaffolds

Get scaffolded collection models:

```kotlin
val scaffolds = pb.collections.getScaffolds()

// Access scaffold types
val baseScaffold = scaffolds["base"]
val authScaffold = scaffolds["auth"]
val viewScaffold = scaffolds["view"]
```

## Examples

### Complete Collection Setup

```kotlin
import com.bosbase.sdk.BosBase

fun setupBlogCollections(pb: BosBase) {
    // Authenticate as admin
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Create articles collection
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
                    "collectionId" to "users",
                    "maxSelect" to 1
                ))
            )
        )
    )
    
    // Set API rules
    pb.collections.update("articles", body = mapOf(
        "listRule" to "status = 'published' || @request.auth.id != ''",
        "viewRule" to "status = 'published' || @request.auth.id = author.id",
        "createRule" to "@request.auth.id != ''",
        "updateRule" to "@request.auth.id = author.id",
        "deleteRule" to "@request.auth.id = author.id || @request.auth.isSuperuser = true"
    ))
    
    // Add index for performance
    val collection = pb.collections.getOne("articles")
    val indexes = ((collection["indexes"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toMutableList() ?: mutableListOf()).apply {
        add("CREATE INDEX idx_status_created ON articles(status, created)")
    }
    pb.collections.update("articles", body = mapOf(
        "indexes" to indexes
    ))
    
    println("Blog collections setup complete!")
}
```

### Dynamic Field Addition

```kotlin
fun addTagFieldToArticles(pb: BosBase) {
    pb.collections.addField(
        collectionIdOrName = "articles",
        field = mapOf(
            "name" to "tags",
            "type" to "select",
            "options" to mapOf(
                "maxSelect" to 5,
                "values" to listOf("kotlin", "android", "backend", "frontend")
            )
        )
    )
}
```

### Collection Migration

```kotlin
fun migrateCollection(pb: BosBase, oldName: String, newName: String) {
    // Get existing collection
    val collection = pb.collections.getOne(oldName)
    
    // Create new collection with updated name
    val newCollection = pb.collections.create(
        body = (collection.jsonObject.toMutableMap() + ("name" to newName)).toMap()
    )
    
    // Copy data (if needed)
    // ... data migration logic ...
    
    // Delete old collection
    pb.collections.delete(oldName)
}
```

