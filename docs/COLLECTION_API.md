# Collection API - Kotlin SDK Documentation

## Overview

The Collection API provides endpoints for managing collections (Base, Auth, and View types). All operations require superuser authentication and allow you to create, read, update, and delete collections along with their schemas and configurations.

**Key Features:**
- List and search collections
- View collection details
- Create collections (base, auth, view)
- Update collection schemas and rules
- Delete collections
- Truncate collections (delete all records)
- Import collections in bulk
- Get collection scaffolds (templates)

**Backend Endpoints:**
- `GET /api/collections` - List collections
- `GET /api/collections/{collection}` - View collection
- `POST /api/collections` - Create collection
- `PATCH /api/collections/{collection}` - Update collection
- `DELETE /api/collections/{collection}` - Delete collection
- `DELETE /api/collections/{collection}/truncate` - Truncate collection
- `PUT /api/collections/import` - Import collections
- `GET /api/collections/meta/scaffolds` - Get scaffolds

**Note**: All Collection API operations require superuser authentication.

> 📖 **Reference**: For detailed collection concepts, see the [JavaScript SDK Collection API documentation](../js-sdk/docs/COLLECTION_API.md) and [COLLECTIONS.md](COLLECTIONS.md).

## Authentication

All Collection API operations require superuser authentication:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Authenticate as superuser
pb.admins.authWithPassword("admin@example.com", "password")
```

## List Collections

Returns a paginated list of collections with support for filtering and sorting.

```kotlin
// Basic list
val result = pb.collections.getList(page = 1, perPage = 30)

val page = result.page
val perPage = result.perPage
val totalItems = result.totalItems
val items = result.items

println("Page: $page, Per Page: $perPage, Total: $totalItems")
```

### Advanced Filtering and Sorting

```kotlin
// Filter by type
val authCollections = pb.collections.getList(
    page = 1,
    perPage = 100,
    filter = "type = \"auth\""
)

// Filter by name pattern
val matchingCollections = pb.collections.getList(
    page = 1,
    perPage = 100,
    filter = "name ~ \"user\""
)

// Sort by creation date
val sortedCollections = pb.collections.getList(
    page = 1,
    perPage = 100,
    sort = "-created"
)
```

## Get Collection

Retrieve a single collection by ID or name.

```kotlin
// Get collection by name
val collection = pb.collections.getOne("articles")

// Get collection by ID
val collectionById = pb.collections.getOne("_pbc_base_123")

val name = collection["name"]?.jsonPrimitive?.contentOrNull
val type = collection["type"]?.jsonPrimitive?.contentOrNull
val fields = collection["fields"]?.jsonArray

println("Collection: $name ($type)")
```

## Create Collection

### Create Base Collection

```kotlin
val collection = pb.collections.create(
    body = mapOf(
        "type" to "base",
        "name" to "articles",
        "fields" to listOf(
            mapOf("name" to "title", "type" to "text", "required" to true),
            mapOf("name" to "description", "type" to "text")
        )
    )
)
```

### Create Auth Collection

```kotlin
val usersCollection = pb.collections.create(
    body = mapOf(
        "type" to "auth",
        "name" to "users",
        "fields" to listOf(
            mapOf("name" to "name", "type" to "text", "required" to true)
        )
    )
)
```

### Create View Collection

```kotlin
val viewCollection = pb.collections.create(
    body = mapOf(
        "type" to "view",
        "name" to "post_stats",
        "viewQuery" to """
            SELECT posts.id, posts.name, count(comments.id) as totalComments 
            FROM posts 
            LEFT JOIN comments on comments.postId = posts.id 
            GROUP BY posts.id
        """.trimIndent()
    )
)
```

## Update Collection

```kotlin
// Update collection
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

## Manage Indexes

BosBase keeps collection indexes as SQL statements. The Kotlin SDK exposes helpers so you can add, remove, or inspect them without rebuilding the entire payload.

```kotlin
// Create a unique slug index with a custom name
pb.collections.addIndex(
    collectionIdOrName = "posts",
    columns = listOf("slug"),
    unique = true,
    indexName = "idx_posts_slug_unique"
)

// Composite non-unique index (name auto-generated)
pb.collections.addIndex("posts", listOf("status", "published"))

// Drop the slug index
pb.collections.removeIndex("posts", listOf("slug"))

// Print existing indexes
val indexes = pb.collections.getIndexes("posts")
indexes.forEach { println(it) }
```

- Every column must already exist on the collection (system columns like `id` are always available).
- Set `unique = true` to emit `CREATE UNIQUE INDEX`.
- Passing `indexName` overrides the auto-generated name (`idx_{collection}_{column1}_{column2}`).
- `removeIndex` deletes any index that contains all provided columns, making it work for both single and composite indexes.

## Delete Collection

```kotlin
// Delete a collection
pb.collections.delete("articles")
```

## Truncate Collection

Delete all records in a collection without deleting the collection itself.

```kotlin
// Truncate collection (delete all records)
pb.collections.truncate("articles")
```

## Import Collections

Import multiple collections at once.

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
    collections = collections.map { it.jsonObject },
    deleteMissing = false  // Set to true to delete collections not in import
)
```

## Get Scaffolds

Get scaffolded collection models for quick creation.

```kotlin
val scaffolds = pb.collections.getScaffolds()

// Access scaffold types
val baseScaffold = scaffolds["base"]?.jsonObject
val authScaffold = scaffolds["auth"]?.jsonObject
val viewScaffold = scaffolds["view"]?.jsonObject
```

## Field Management

See [COLLECTIONS.md](COLLECTIONS.md) for detailed field management operations.

## API Rules Management

See [COLLECTIONS.md](COLLECTIONS.md) and [API_RULES_AND_FILTERS.md](API_RULES_AND_FILTERS.md) for API rules management.
