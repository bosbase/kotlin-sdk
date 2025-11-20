# Vector Database API - Kotlin SDK Documentation

Vector database operations for semantic search, RAG (Retrieval-Augmented Generation), and AI applications.

> **Note**: Vector operations are currently implemented using sqlite-vec but are designed with abstraction in mind to support future vector database providers.

> 📖 **Reference**: For detailed Vector API concepts, see the [JavaScript SDK Vector documentation](../js-sdk/docs/VECTOR_API.md).

## Overview

The Vector API provides a unified interface for working with vector embeddings, enabling you to:
- Store and search vector embeddings
- Perform similarity search
- Build RAG applications
- Create recommendation systems
- Enable semantic search capabilities

## Getting Started

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Authenticate as superuser (vectors require superuser auth)
pb.admins.authWithPassword("admin@example.com", "password")
```

## Collection Management

### Create Collection

```kotlin
// Create a vector collection with specified dimension and distance metric
pb.vectors.createCollection(
    name = "documents",
    config = mapOf(
        "dimension" to 384,      // Vector dimension (default: 384)
        "distance" to "cosine"   // Distance metric: 'cosine' (default), 'l2', 'dot'
    )
)

// Minimal example (uses defaults)
pb.vectors.createCollection(
    name = "documents",
    config = emptyMap()
)
```

### List Collections

```kotlin
val collections = pb.vectors.listCollections()

collections?.forEach { collection ->
    val name = collection["name"]?.jsonPrimitive?.contentOrNull
    val count = collection["count"]?.jsonPrimitive?.intOrNull
    println("$name: $count vectors")
}
```

### Delete Collection

```kotlin
// Delete a vector collection and all its data
pb.vectors.deleteCollection("documents")

// ⚠️ Warning: This permanently deletes the collection and all vectors in it!
```

## Vector Document Operations

### Insert Document

```kotlin
// Insert a single vector document
pb.vectors.insert(
    collection = "documents",
    document = mapOf(
        "id" to "doc1",
        "vector" to listOf(0.1, 0.2, 0.3, /* ... */),  // Vector embedding
        "metadata" to mapOf("title" to "Document 1"),
        "content" to "Document content here"
    )
)
```

### Batch Insert

```kotlin
// Insert multiple vector documents in a batch
pb.vectors.batchInsert(
    collection = "documents",
    options = mapOf(
        "documents" to listOf(
            mapOf(
                "id" to "doc1",
                "vector" to listOf(0.1, 0.2, 0.3),
                "content" to "Content 1"
            ),
            mapOf(
                "id" to "doc2",
                "vector" to listOf(0.4, 0.5, 0.6),
                "content" to "Content 2"
            )
        )
    )
)
```

### Get Document

```kotlin
// Get a vector document by ID
val document = pb.vectors.get(
    collection = "documents",
    documentId = "doc1"
)

val content = document?.get("content")?.jsonPrimitive?.contentOrNull
println(content)
```

### Update Document

```kotlin
// Update an existing vector document
pb.vectors.update(
    collection = "documents",
    documentId = "doc1",
    document = mapOf(
        "metadata" to mapOf("title" to "Updated Title")
    )
)
```

### Delete Document

```kotlin
// Delete a vector document
pb.vectors.delete(
    collection = "documents",
    documentId = "doc1"
)
```

### List Documents

```kotlin
// List vector documents (with pagination)
val result = pb.vectors.list(
    collection = "documents",
    page = 1,
    perPage = 50
)

val items = result?.get("items")?.jsonArray
items?.forEach { doc ->
    val id = doc.jsonObject["id"]?.jsonPrimitive?.contentOrNull
    println("Document: $id")
}
```

### Search Vectors

```kotlin
// Search for similar vectors
val searchResult = pb.vectors.search(
    collection = "documents",
    options = mapOf(
        "queryVector" to listOf(0.1, 0.2, 0.3, /* ... */),  // Query vector
        "limit" to 10,                                       // Max results (default: 10, max: 100)
        "minScore" to 0.7,                                   // Minimum similarity score
        "includeDistance" to true                            // Include distance in results
    )
)

val results = searchResult?.get("results")?.jsonArray
results?.forEach { result ->
    val document = result.jsonObject["document"]?.jsonObject
    val score = result.jsonObject["score"]?.jsonPrimitive?.doubleOrNull
    val distance = result.jsonObject["distance"]?.jsonPrimitive?.doubleOrNull
    
    println("Score: $score, Distance: $distance")
    println("Document: ${document?.get("id")?.jsonPrimitive?.contentOrNull}")
}
```

## Complete Example

```kotlin
fun vectorExample(pb: BosBase) {
    // Authenticate as superuser
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Create vector collection
    pb.vectors.createCollection(
        name = "documents",
        config = mapOf(
            "dimension" to 384,
            "distance" to "cosine"
        )
    )
    
    // Insert document
    pb.vectors.insert(
        collection = "documents",
        document = mapOf(
            "id" to "doc1",
            "vector" to (1..384).map { Math.random().toFloat() }.toList(),
            "content" to "This is a test document",
            "metadata" to mapOf("title" to "Test Document")
        )
    )
    
    // Search
    val queryVector = (1..384).map { Math.random().toFloat() }.toList()
    val searchResult = pb.vectors.search(
        collection = "documents",
        options = mapOf(
            "queryVector" to queryVector,
            "limit" to 5
        )
    )
    
    val results = searchResult?.get("results")?.jsonArray
    results?.forEach { result ->
        val score = result.jsonObject["score"]?.jsonPrimitive?.doubleOrNull
        println("Similarity score: $score")
    }
}
```

