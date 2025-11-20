# API Records - Kotlin SDK Documentation

## Overview

The Records API provides comprehensive CRUD (Create, Read, Update, Delete) operations for collection records, along with powerful search, filtering, and authentication capabilities.

**Key Features:**
- Paginated list and search with filtering and sorting
- Single record retrieval with expand support
- Create, update, and delete operations
- Batch operations for multiple records
- Authentication methods (password, OAuth2, OTP)
- Email verification and password reset
- Relation expansion up to 6 levels deep
- Field selection and excerpt modifiers

**Backend Endpoints:**
- `GET /api/collections/{collection}/records` - List records
- `GET /api/collections/{collection}/records/{id}` - View record
- `POST /api/collections/{collection}/records` - Create record
- `PATCH /api/collections/{collection}/records/{id}` - Update record
- `DELETE /api/collections/{collection}/records/{id}` - Delete record
- `POST /api/batch` - Batch operations

> 📖 **Reference**: For detailed API concepts, see the [JavaScript SDK Records documentation](../js-sdk/docs/API_RECORDS.md).

## CRUD Operations

### List/Search Records

Returns a paginated records list with support for sorting, filtering, and expansion.

```kotlin
import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

val pb = BosBase("http://127.0.0.1:8090")

// Basic list with pagination
val result = pb.collection("posts").getList(page = 1, perPage = 50)

println(result.page)        // 1
println(result.perPage)     // 50
println(result.totalItems)  // 150
println(result.totalPages)  // 3
result.items.forEach { post ->
    println(post["title"]?.jsonPrimitive?.contentOrNull)
}
```

#### Advanced List with Filtering and Sorting

```kotlin
// Filter and sort
val result = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    filter = "created >= '2022-01-01 00:00:00' && status = 'published'",
    sort = "-created,title",  // DESC by created, ASC by title
    expand = "author,categories"
)

// Using filter helper for safe parameter binding
val searchTerm = "javascript"
val result2 = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    filter = pb.filter("title ~ {:term} && views > {:minViews}", mapOf(
        "term" to searchTerm,
        "minViews" to 100
    )),
    sort = "-views"
)
```

#### Get Full List

Fetch all records at once (useful for small collections):

```kotlin
// Get all records
val allPosts = pb.collection("posts").getFullList(
    sort = "-created",
    filter = "status = 'published'"
)

// With batch size for large collections
val allPosts = pb.collection("posts").getFullList(
    batch = 200,
    sort = "-created"
)
```

#### Get First Matching Record

Get only the first record that matches a filter:

```kotlin
val post = pb.collection("posts").getFirstListItem(
    filter = "slug = 'my-post-slug'",
    expand = "author,categories.tags"
)

println(post["title"]?.jsonPrimitive?.contentOrNull)
```

### View Record

Retrieve a single record by ID:

```kotlin
// Basic retrieval
val record = pb.collection("posts").getOne("RECORD_ID")

// With expand
val record = pb.collection("posts").getOne(
    id = "RECORD_ID",
    expand = "author,categories.tags"
)

// With field selection
val record = pb.collection("posts").getOne(
    id = "RECORD_ID",
    fields = "id,title,content,author.email"
)
```

### Create Record

Create a new record:

```kotlin
// Basic create
val newPost = pb.collection("posts").create(
    body = mapOf(
        "title" to "Hello Kotlin!",
        "content" to "This is my first post",
        "status" to "published"
    )
)

println("Created record ID: ${newPost["id"]?.jsonPrimitive?.contentOrNull}")
```

#### Create with File Upload

```kotlin
import com.bosbase.sdk.FileAttachment
import java.io.File

// Create record with file attachment
val file = File("path/to/image.jpg")
val attachment = FileAttachment.fromFile(file)

val newPost = pb.collection("posts").create(
    body = mapOf(
        "title" to "Post with Image",
        "content" to "Content here"
    ),
    files = mapOf(
        "image" to listOf(attachment)
    )
)
```

#### Create with Multiple Files

```kotlin
val attachments = listOf(
    FileAttachment.fromFile(File("image1.jpg")),
    FileAttachment.fromFile(File("image2.jpg"))
)

val newPost = pb.collection("posts").create(
    body = mapOf("title" to "Post with Multiple Images"),
    files = mapOf("images" to attachments)
)
```

### Update Record

Update an existing record:

```kotlin
// Basic update
val updated = pb.collection("posts").update(
    id = "RECORD_ID",
    body = mapOf(
        "title" to "Updated Title",
        "status" to "archived"
    )
)

println("Updated: ${updated["title"]?.jsonPrimitive?.contentOrNull}")
```

#### Update with File Replacement

```kotlin
val updated = pb.collection("posts").update(
    id = "RECORD_ID",
    body = mapOf("title" to "New Title"),
    files = mapOf(
        "image" to listOf(FileAttachment.fromFile(File("new-image.jpg")))
    )
)
```

### Delete Record

Delete a record:

```kotlin
pb.collection("posts").delete("RECORD_ID")
```

## Filtering

### Filter Syntax

BosBase uses a custom filter syntax similar to SQL WHERE clauses:

```kotlin
// Comparison operators
"status = 'published'"
"views > 100"
"created >= '2022-01-01 00:00:00'"

// Logical operators
"status = 'published' && views > 100"
"status = 'draft' || status = 'archived'"

// String operators
"title ~ 'javascript'"  // contains
"title !~ 'test'"        // not contains
"title ? 'test'"         // like (case-insensitive)
"title !? 'test'"        // not like

// Null checks
"description != null"
"deleted = null"
```

### Safe Filter Parameter Binding

Use the `filter()` helper method to safely bind parameters and prevent injection:

```kotlin
val searchTerm = "user's post"  // Contains apostrophe
val minViews = 100

// Safe: automatically escapes special characters
val filter = pb.filter(
    "title ~ {:term} && views > {:minViews}",
    mapOf(
        "term" to searchTerm,
        "minViews" to minViews
    )
)
// Result: "title ~ 'user\'s post' && views > 100"

val result = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    filter = filter
)
```

### Supported Parameter Types

The filter helper supports:
- `String` (auto-escaped with single quotes)
- `Number` (Int, Long, Double, etc.)
- `Boolean` (true/false)
- `Date` (converted to ISO format)
- `null`
- Other types (converted via JSON.stringify)

## Sorting

Sort records using the `sort` parameter:

```kotlin
// Single field (ascending)
sort = "created"

// Single field (descending)
sort = "-created"

// Multiple fields
sort = "-created,title,-views"  // DESC by created, ASC by title, DESC by views
```

## Expansion (Relations)

Expand related records up to 6 levels deep:

```kotlin
// Single relation
val post = pb.collection("posts").getOne(
    id = "RECORD_ID",
    expand = "author"
)

// Multiple relations
val post = pb.collection("posts").getOne(
    id = "RECORD_ID",
    expand = "author,categories"
)

// Nested relations
val post = pb.collection("posts").getOne(
    id = "RECORD_ID",
    expand = "author,categories.tags"
)

// Access expanded data
val authorName = post["expand"]?.jsonObject
    ?.get("author")?.jsonObject
    ?.get("name")?.jsonPrimitive?.contentOrNull
```

## Field Selection

Select specific fields to reduce payload size:

```kotlin
// Select specific fields
val record = pb.collection("posts").getOne(
    id = "RECORD_ID",
    fields = "id,title,content,author.email"
)

// In list queries
val result = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    fields = "id,title,created"
)
```

## Batch Operations

Perform multiple operations in a single transaction:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Create a batch
val batch = pb.createBatch()

// Register operations
batch.collection("posts").create(mapOf("title" to "Post 1"))
batch.collection("posts").create(mapOf("title" to "Post 2"))
batch.collection("posts").update("RECORD_ID", mapOf("title" to "Updated"))
batch.collection("posts").delete("ANOTHER_RECORD_ID")

// Execute batch
val results = batch.send()

// Results contain responses for each operation
results.forEach { result ->
    println("Operation result: $result")
}
```

## Error Handling

All record operations throw `ClientResponseError` on failure:

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

## Examples

### Complete CRUD Example

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun main() {
    val pb = BosBase("http://localhost:8090")
    
    try {
        // Create
        val newPost = pb.collection("posts").create(
            body = mapOf(
                "title" to "My First Post",
                "content" to "This is the content",
                "status" to "published"
            )
        )
        val postId = newPost["id"]?.jsonPrimitive?.contentOrNull
        println("Created post: $postId")
        
        // Read
        val post = pb.collection("posts").getOne(
            id = postId ?: "",
            expand = "author"
        )
        println("Title: ${post["title"]?.jsonPrimitive?.contentOrNull}")
        
        // Update
        val updated = pb.collection("posts").update(
            id = postId ?: "",
            body = mapOf("title" to "Updated Title")
        )
        println("Updated: ${updated["title"]?.jsonPrimitive?.contentOrNull}")
        
        // List with filter
        val results = pb.collection("posts").getList(
            page = 1,
            perPage = 10,
            filter = "status = 'published'",
            sort = "-created"
        )
        println("Found ${results.totalItems} published posts")
        
        // Delete
        pb.collection("posts").delete(postId ?: "")
        println("Deleted post")
        
    } catch (e: ClientResponseError) {
        println("Error: ${e.status} - ${e.response}")
    }
}
```

### Search with Pagination

```kotlin
fun searchPosts(pb: BosBase, query: String, page: Int = 1): ResultList {
    return pb.collection("posts").getList(
        page = page,
        perPage = 20,
        filter = pb.filter("title ~ {:query} || content ~ {:query}", mapOf("query" to query)),
        sort = "-created"
    )
}

// Usage
val results = searchPosts(pb, "kotlin", page = 1)
results.items.forEach { post ->
    println(post["title"]?.jsonPrimitive?.contentOrNull)
}
```

### File Upload Example

```kotlin
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

### Working with Relations

```kotlin
// Get post with author and categories
val post = pb.collection("posts").getOne(
    id = "POST_ID",
    expand = "author,categories"
)

// Access expanded author
val author = post["expand"]?.jsonObject?.get("author")?.jsonObject
val authorName = author?.get("name")?.jsonPrimitive?.contentOrNull
val authorEmail = author?.get("email")?.jsonPrimitive?.contentOrNull

// Access expanded categories (array)
val categories = post["expand"]?.jsonObject?.get("categories")?.jsonArray
categories?.forEach { category ->
    val name = category.jsonObject["name"]?.jsonPrimitive?.contentOrNull
    println("Category: $name")
}
```

