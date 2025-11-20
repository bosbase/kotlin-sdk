# LLM Document API - Kotlin SDK Documentation

The `LLMDocumentService` wraps the `/api/llm-documents` endpoints that are backed by the embedded chromem-go vector store (persisted in rqlite). Each document contains text content, optional metadata and an embedding vector that can be queried with semantic search.

> 📖 **Reference**: For detailed LLM Documents concepts, see the [JavaScript SDK LLM Documents documentation](../js-sdk/docs/LLM_DOCUMENTS.md).

## Getting Started

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Authenticate as superuser
pb.admins.authWithPassword("admin@example.com", "password")

// Create a logical namespace for your documents
pb.llmDocuments.createCollection(
    name = "knowledge-base",
    metadata = mapOf("domain" to "internal")
)
```

## Insert Documents

```kotlin
val doc = pb.llmDocuments.insert(
    collection = "knowledge-base",
    document = mapOf(
        "content" to "Leaves are green because chlorophyll absorbs red and blue light.",
        "metadata" to mapOf("topic" to "biology")
    )
)

pb.llmDocuments.insert(
    collection = "knowledge-base",
    document = mapOf(
        "id" to "sky",
        "content" to "The sky is blue because of Rayleigh scattering.",
        "metadata" to mapOf("topic" to "physics")
    )
)
```

## Query Documents

```kotlin
val result = pb.llmDocuments.query(
    collection = "knowledge-base",
    options = mapOf(
        "queryText" to "Why is the sky blue?",
        "limit" to 3,
        "where" to mapOf("topic" to "physics")
    )
)

val results = result?.get("results")?.jsonArray
results?.forEach { match ->
    val id = match.jsonObject["id"]?.jsonPrimitive?.contentOrNull
    val similarity = match.jsonObject["similarity"]?.jsonPrimitive?.doubleOrNull
    println("$id: $similarity")
}
```

## Manage Documents

```kotlin
// Update a document
pb.llmDocuments.update(
    collection = "knowledge-base",
    documentId = "sky",
    document = mapOf(
        "metadata" to mapOf("topic" to "physics", "reviewed" to "true")
    )
)

// List documents with pagination
val page = pb.llmDocuments.list(
    collection = "knowledge-base",
    page = 1,
    perPage = 25
)

// Delete unwanted entries
pb.llmDocuments.delete(
    collection = "knowledge-base",
    documentId = "sky"
)
```

## Collection Management

```kotlin
// List all collections
val collections = pb.llmDocuments.listCollections()
collections.forEach { collection ->
    val name = collection["name"]?.jsonPrimitive?.contentOrNull
    println("Collection: $name")
}

// Delete a collection
pb.llmDocuments.deleteCollection("knowledge-base")
```

## Complete Example

```kotlin
fun llmDocumentsExample(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Create collection
    pb.llmDocuments.createCollection(
        name = "knowledge-base",
        metadata = mapOf("domain" to "internal")
    )
    
    // Insert documents
    pb.llmDocuments.insert(
        collection = "knowledge-base",
        document = mapOf(
            "content" to "Kotlin is a programming language developed by JetBrains.",
            "metadata" to mapOf("topic" to "programming", "language" to "kotlin")
        )
    )
    
    // Query documents
    val result = pb.llmDocuments.query(
        collection = "knowledge-base",
        options = mapOf(
            "queryText" to "What is Kotlin?",
            "limit" to 5
        )
    )
    
    val results = result?.get("results")?.jsonArray
    results?.forEach { match ->
        val content = match.jsonObject["content"]?.jsonPrimitive?.contentOrNull
        val similarity = match.jsonObject["similarity"]?.jsonPrimitive?.doubleOrNull
        println("Similarity: $similarity")
        println("Content: $content")
    }
}
```

