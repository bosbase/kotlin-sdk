# Working with Relations - Kotlin SDK Documentation

## Overview

Relations allow you to link records between collections. BosBase supports both single and multiple relations, and provides powerful features for expanding related records and working with back-relations.

**Key Features:**
- Single and multiple relations
- Expand related records without additional requests
- Nested relation expansion (up to 6 levels)
- Back-relations for reverse lookups
- Field modifiers for append/prepend/remove operations

**Relation Field Types:**
- **Single Relation**: Links to one record (MaxSelect <= 1)
- **Multiple Relation**: Links to multiple records (MaxSelect > 1)

**Backend Behavior:**
- Relations are stored as record IDs or arrays of IDs
- Expand only includes relations the client can view (satisfies View API Rule)
- Back-relations use format: `collectionName_via_fieldName`
- Back-relation expand limited to 1000 records per field

> 📖 **Reference**: For detailed relations concepts, see the [JavaScript SDK Relations documentation](../js-sdk/docs/RELATIONS.md).

## Setting Up Relations

### Creating a Relation Field

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
pb.admins.authWithPassword("admin@example.com", "password")

val collection = pb.collections.getOne("posts")
val fields = (collection["fields"]?.jsonArray?.toMutableList() ?: mutableListOf()).toMutableList()

// Single relation field
fields.add(
    mapOf(
        "name" to "user",
        "type" to "relation",
        "options" to mapOf(
            "collectionId" to "users",  // ID of related collection
            "maxSelect" to 1           // Single relation
        ),
        "required" to true
    )
)

// Multiple relation field
fields.add(
    mapOf(
        "name" to "tags",
        "type" to "relation",
        "options" to mapOf(
            "collectionId" to "tags",
            "maxSelect" to 10,          // Multiple relation (max 10)
            "minSelect" to 1            // Minimum 1 required
        ),
        "cascadeDelete" to false       // Don't delete post when tags deleted
    )
)

pb.collections.update("posts", body = mapOf("fields" to fields))
```

## Creating Records with Relations

### Single Relation

```kotlin
// Create a post with a single user relation
val post = pb.collection("posts").create(
    body = mapOf(
        "title" to "My Post",
        "user" to "USER_ID"  // Single relation ID
    )
)
```

### Multiple Relations

```kotlin
// Create a post with multiple tags
val post = pb.collection("posts").create(
    body = mapOf(
        "title" to "My Post",
        "tags" to listOf("TAG_ID1", "TAG_ID2", "TAG_ID3")  // Array of IDs
    )
)
```

### Mixed Relations

```kotlin
// Create a comment with both single and multiple relations
val comment = pb.collection("comments").create(
    body = mapOf(
        "message" to "Great post!",
        "post" to "POST_ID",        // Single relation
        "user" to "USER_ID",        // Single relation
        "tags" to listOf("TAG1", "TAG2")  // Multiple relation
    )
)
```

## Updating Relations

### Replace All Relations

```kotlin
// Replace all tags
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("tags" to listOf("NEW_TAG1", "NEW_TAG2"))
)
```

### Append Relations (Using + Modifier)

```kotlin
// Append tags to existing ones
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("tags+" to "NEW_TAG_ID")  // Append single tag
)

// Append multiple tags
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("tags+" to listOf("TAG_ID1", "TAG_ID2"))  // Append multiple tags
)
```

### Prepend Relations (Using + Prefix)

```kotlin
// Prepend tags (tags will appear first)
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("+tags" to "PRIORITY_TAG")  // Prepend single tag
)

// Prepend multiple tags
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("+tags" to listOf("TAG1", "TAG2"))  // Prepend multiple tags
)
```

### Remove Relations (Using - Modifier)

```kotlin
// Remove single tag
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("tags-" to "TAG_ID_TO_REMOVE")
)

// Remove multiple tags
pb.collection("posts").update(
    id = "POST_ID",
    body = mapOf("tags-" to listOf("TAG1", "TAG2"))
)
```

## Expanding Relations

### Single Relation Expansion

```kotlin
// Get post with author expanded
val post = pb.collection("posts").getOne(
    id = "POST_ID",
    expand = "author"
)

// Access expanded author
val author = post["expand"]?.jsonObject?.get("author")?.jsonObject
val authorName = author?.get("name")?.jsonPrimitive?.contentOrNull
val authorEmail = author?.get("email")?.jsonPrimitive?.contentOrNull
```

### Multiple Relation Expansion

```kotlin
// Expand multiple relations
val post = pb.collection("posts").getOne(
    id = "POST_ID",
    expand = "author,categories"
)

// Access expanded categories (array)
val categories = post["expand"]?.jsonObject?.get("categories")?.jsonArray
categories?.forEach { category ->
    val name = category.jsonObject["name"]?.jsonPrimitive?.contentOrNull
    println("Category: $name")
}
```

### Nested Relation Expansion

```kotlin
// Expand nested relations (up to 6 levels)
val post = pb.collection("posts").getOne(
    id = "POST_ID",
    expand = "author,categories.tags"
)

// Access nested expanded data
val category = post["expand"]?.jsonObject?.get("categories")?.jsonArray?.firstOrNull()
val tags = category?.jsonObject?.get("expand")?.jsonObject?.get("tags")?.jsonArray
```

### Expansion in List Queries

```kotlin
// List posts with expanded relations
val result = pb.collection("posts").getList(
    page = 1,
    perPage = 50,
    expand = "author,categories"
)

result.items.forEach { post ->
    val author = post["expand"]?.jsonObject?.get("author")?.jsonObject
    println("Author: ${author?.get("name")?.jsonPrimitive?.contentOrNull}")
}
```

## Back-Relations

Back-relations allow you to access reverse relationships. Use the format: `collectionName_via_fieldName`

```kotlin
// Get user with all their posts (back-relation)
val user = pb.collection("users").getOne(
    id = "USER_ID",
    expand = "posts_via_author"  // Back-relation: posts where author = user
)

// Access back-relation
val posts = user["expand"]?.jsonObject?.get("posts_via_author")?.jsonArray
posts?.forEach { post ->
    val title = post.jsonObject["title"]?.jsonPrimitive?.contentOrNull
    println("Post: $title")
}
```

## Complete Examples

### Blog Post with Author and Tags

```kotlin
fun createBlogPost(pb: BosBase, title: String, content: String, authorId: String, tagIds: List<String>) {
    // Create post with relations
    val post = pb.collection("posts").create(
        body = mapOf(
            "title" to title,
            "content" to content,
            "author" to authorId,      // Single relation
            "tags" to tagIds           // Multiple relation
        )
    )
    
    println("Created post: ${post["id"]?.jsonPrimitive?.contentOrNull}")
    
    // Get post with relations expanded
    val fullPost = pb.collection("posts").getOne(
        id = post["id"]?.jsonPrimitive?.contentOrNull ?: "",
        expand = "author,tags"
    )
    
    // Access expanded data
    val author = fullPost["expand"]?.jsonObject?.get("author")?.jsonObject
    println("Author: ${author?.get("name")?.jsonPrimitive?.contentOrNull}")
    
    val tags = fullPost["expand"]?.jsonObject?.get("tags")?.jsonArray
    tags?.forEach { tag ->
        println("Tag: ${tag.jsonObject["name"]?.jsonPrimitive?.contentOrNull}")
    }
}
```

### Managing Tags on Posts

```kotlin
fun managePostTags(pb: BosBase, postId: String, newTagId: String, tagToRemove: String) {
    // Add new tag, remove old tag
    pb.collection("posts").update(
        id = postId,
        body = mapOf(
            "tags+" to newTagId,      // Append new tag
            "tags-" to tagToRemove    // Remove old tag
        )
    )
}
```

### User Profile with All Posts

```kotlin
fun getUserProfile(pb: BosBase, userId: String) {
    // Get user with all their posts (back-relation)
    val user = pb.collection("users").getOne(
        id = userId,
        expand = "posts_via_author"
    )
    
    println("User: ${user["name"]?.jsonPrimitive?.contentOrNull}")
    
    // Access back-relation
    val posts = user["expand"]?.jsonObject?.get("posts_via_author")?.jsonArray
    println("Posts count: ${posts?.size ?: 0}")
    
    posts?.forEach { post ->
        val title = post.jsonObject["title"]?.jsonPrimitive?.contentOrNull
        println("  - $title")
    }
}
```

