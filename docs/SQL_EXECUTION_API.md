# SQL Execution API - Kotlin SDK

## Overview

The SQL Execution API lets superusers run ad-hoc SQL statements against the BosBase database and retrieve the results. Use it for controlled maintenance or diagnostics tasks—never expose it to untrusted users.

**Key Points**
- Superuser authentication is required for every request.
- Supports both read and write statements.
- Returns column names, rows, and `rowsAffected` for writes.
- Respects the SDK's request hooks, headers, and cancellation options.

**Endpoint**
- `POST /api/sql/execute`
- Body: `{ "query": "<your SQL statement>" }`

## Authentication

Authenticate as a superuser before calling `pb.sql.execute`:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")
pb.collection("_superusers").authWithPassword("admin@example.com", "password")
```

## Executing a SELECT

```kotlin
val result = pb.sql.execute("SELECT id, text FROM demo1 ORDER BY id LIMIT 5")

println(result.columns) // ["id", "text"]
println(result.rows)
// [
//   ["84nmscqy84lsi1t", "test"],
//   ...
// ]
```

## Executing a Write Statement

```kotlin
val update = pb.sql.execute(
    "UPDATE demo1 SET text='updated via api' WHERE id='84nmscqy84lsi1t'",
)

println(update.rowsAffected) // 1
println(update.columns)      // ["rows_affected"]
println(update.rows)         // [["1"]]
```

## Inserts and Deletes

```kotlin
// Insert
val insert = pb.sql.execute(
    "INSERT INTO demo1 (id, text) VALUES ('new-id', 'hello from SQL API')",
)
println(insert.rowsAffected) // 1

// Delete
val removed = pb.sql.execute("DELETE FROM demo1 WHERE id='new-id'")
println(removed.rowsAffected) // 1
```

## Response Shape

```jsonc
{
  "columns": ["col1", "col2"], // omitted when empty
  "rows": [["v1", "v2"]],      // omitted when empty
  "rowsAffected": 3            // only present for write operations
}
```

The Kotlin SDK maps this to `SQLExecuteResponse`:

```kotlin
data class SQLExecuteResponse(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowsAffected: Long?
)
```

## Error Handling

- The SDK rejects empty queries before sending a request.
- Database or syntax errors are returned as `ClientResponseError` instances.
- You can pass standard options (`headers`, `query`, `requestKey`, etc.) to `pb.sql.execute()` when you need custom behavior.

## Safety Tips

- Never pass user-controlled SQL into this API.
- Prefer explicit statements over multi-statement payloads.
- Audit who has superuser credentials and rotate them regularly.
