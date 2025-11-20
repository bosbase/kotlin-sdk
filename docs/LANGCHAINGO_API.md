# LangChaingo API - Kotlin SDK Documentation

BosBase exposes the `/api/langchaingo` endpoints so you can run LangChainGo powered workflows without leaving the platform. The Kotlin SDK wraps these endpoints with the `pb.langchaingo` service.

The service exposes four high-level methods:

| Method | HTTP Endpoint | Description |
| --- | --- | --- |
| `pb.langchaingo.completions()` | `POST /api/langchaingo/completions` | Runs a chat/completion call using the configured LLM provider. |
| `pb.langchaingo.rag()` | `POST /api/langchaingo/rag` | Runs a retrieval-augmented generation pass over an `llmDocuments` collection. |
| `pb.langchaingo.queryDocuments()` | `POST /api/langchaingo/documents/query` | Asks an OpenAI-backed chain to answer questions over `llmDocuments` and optionally return matched sources. |
| `pb.langchaingo.sql()` | `POST /api/langchaingo/sql` | Lets OpenAI draft and execute SQL against your BosBase database, then returns the results. |

> 📖 **Reference**: For detailed LangChaingo concepts, see the [JavaScript SDK LangChaingo documentation](../js-sdk/docs/LANGCHAINGO_API.md).

## Text + Chat Completions

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

val completion = pb.langchaingo.completions(
    payload = mapOf(
        "model" to mapOf(
            "provider" to "openai",
            "model" to "gpt-4o-mini"
        ),
        "messages" to listOf(
            mapOf("role" to "system", "content" to "Answer in one sentence."),
            mapOf("role" to "user", "content" to "Explain Rayleigh scattering.")
        ),
        "temperature" to 0.2
    )
)

val content = completion?.get("content")?.jsonPrimitive?.contentOrNull
println(content)
```

## Retrieval-Augmented Generation (RAG)

Pair the LangChaingo endpoints with the `/api/llm-documents` store to build RAG workflows. The backend automatically uses the chromem-go collection configured for the target LLM collection.

```kotlin
val answer = pb.langchaingo.rag(
    payload = mapOf(
        "collection" to "knowledge-base",
        "question" to "Why is the sky blue?",
        "topK" to 4,
        "returnSources" to true,
        "filters" to mapOf(
            "where" to mapOf("topic" to "physics")
        )
    )
)

val answerText = answer?.get("answer")?.jsonPrimitive?.contentOrNull
println(answerText)

val sources = answer?.get("sources")?.jsonArray
sources?.forEach { source ->
    val score = source.jsonObject["score"]?.jsonPrimitive?.doubleOrNull
    val metadata = source.jsonObject["metadata"]?.jsonObject
    val title = metadata?.get("title")?.jsonPrimitive?.contentOrNull
    println("${String.format("%.3f", score)} $title")
}
```

## Custom Prompt Template

Set `promptTemplate` when you want to control how the retrieved context is stuffed into the answer prompt:

```kotlin
pb.langchaingo.rag(
    payload = mapOf(
        "collection" to "knowledge-base",
        "question" to "Summarize the explanation below in 2 sentences.",
        "promptTemplate" to "Context:\n{{.context}}\n\nQuestion: {{.question}}\nSummary:"
    )
)
```

### LLM Document Queries

> **Note**: This interface is only available to superusers.

When you want to pose a question to a specific `llmDocuments` collection and have LangChaingo+OpenAI synthesize an answer, use `queryDocuments`. It mirrors the RAG arguments but takes a `query` field:

```kotlin
val response = pb.langchaingo.queryDocuments(
    payload = mapOf(
        "collection" to "knowledge-base",
        "query" to "List three bullet points about Rayleigh scattering.",
        "topK" to 3,
        "returnSources" to true
    )
)

val answer = response?.get("answer")?.jsonPrimitive?.contentOrNull
println(answer)

val sources = response?.get("sources")?.jsonArray
sources?.forEach { source ->
    val score = source.jsonObject["score"]?.jsonPrimitive?.doubleOrNull
    val metadata = source.jsonObject["metadata"]?.jsonObject
    val title = metadata?.get("title")?.jsonPrimitive?.contentOrNull
    println("${String.format("%.3f", score)} $title")
}
```

### SQL Generation + Execution

> **Important Notes**:
> - This interface is only available to superusers. Requests authenticated with regular `users` tokens return a `401 Unauthorized`.
> - It is recommended to execute query statements (SELECT) only.
> - **Do not use this interface for adding or modifying table structures.** Collection interfaces should be used instead for managing database schema.
> - Directly using this interface for initializing table structures and adding or modifying database tables will cause errors that prevent the automatic generation of APIs.

Superuser tokens (`_superusers` records) can ask LangChaingo to have OpenAI propose a SQL statement, execute it, and return both the generated SQL and execution output.

```kotlin
val result = pb.langchaingo.sql(
    payload = mapOf(
        "query" to "Add a demo project row if it doesn't exist, then list the 5 most recent projects.",
        "tables" to listOf("projects"), // optional hint to limit which tables the model sees
        "topK" to 5
    )
)

val sql = result?.get("sql")?.jsonPrimitive?.contentOrNull
val answer = result?.get("answer")?.jsonPrimitive?.contentOrNull
val columns = result?.get("columns")?.jsonArray?.map { it.jsonPrimitive.content }
val rows = result?.get("rows")?.jsonArray?.map { row ->
    row.jsonArray.map { it.jsonPrimitive.content }
}

println(sql)    // Generated SQL
println(answer) // Model's summary of the execution
println(columns)
println(rows)
```

Use `tables` to restrict which table definitions and sample rows are passed to the model, and `topK` to control how many rows the model should target when building queries. You can also pass the optional `model` block described above to override the default OpenAI model or key for this call.

## Complete Example

```kotlin
fun langchaingoExample(pb: BosBase) {
    // Authenticate as superuser
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Simple completion
    val completion = pb.langchaingo.completions(
        payload = mapOf(
            "messages" to listOf(
                mapOf("role" to "user", "content" to "Hello!")
            )
        )
    )
    
    println(completion?.get("content")?.jsonPrimitive?.contentOrNull)
    
    // RAG query
    val ragResult = pb.langchaingo.rag(
        payload = mapOf(
            "collection" to "knowledge-base",
            "question" to "What is Kotlin?",
            "topK" to 3
        )
    )
    
    println(ragResult?.get("answer")?.jsonPrimitive?.contentOrNull)
    
    // Document query
    val docQueryResult = pb.langchaingo.queryDocuments(
        payload = mapOf(
            "collection" to "knowledge-base",
            "query" to "Explain Kotlin coroutines.",
            "topK" to 3
        )
    )
    
    println(docQueryResult?.get("answer")?.jsonPrimitive?.contentOrNull)
    
    // SQL query
    val sqlResult = pb.langchaingo.sql(
        payload = mapOf(
            "query" to "List all projects",
            "tables" to listOf("projects"),
            "topK" to 10
        )
    )
    
    println(sqlResult?.get("sql")?.jsonPrimitive?.contentOrNull)
    println(sqlResult?.get("answer")?.jsonPrimitive?.contentOrNull)
}
```

