# Register Existing SQL Tables with the Kotlin SDK

Use the SQL table helpers to expose existing tables (or run SQL to create them) and automatically generate REST collections. Both calls are **superuser-only**.

- `registerSqlTables(tables: List<String>)` – map existing tables to collections without running SQL.
- `importSqlTables(tables: List<SqlTableDefinition>)` – optionally run SQL to create tables first, then register them. Returns a `JsonObject` with `created` and `skipped`.

## Requirements

- Authenticate with a `_superusers` token.
- Each table must contain a `TEXT` primary key column named `id`.
- Missing audit columns (`created`, `updated`, `createdBy`, `updatedBy`) are automatically added so the default API rules can be applied.
- Non-system columns are mapped by best effort (text, number, bool, date/time, JSON).

## Basic Usage

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")
pb.authStore.save(SUPERUSER_JWT, null) // must be a superuser token

val collections = pb.collections.registerSqlTables(
    listOf("projects", "accounts"),
)

println(collections.map { it["name"] })
```

## With Request Options

Standard request options (`headers`, `query`, cancellation keys) are supported.

```kotlin
val collections = pb.collections.registerSqlTables(
    tables = listOf("legacy_orders"),
    query = mapOf("q" to 1), // adds ?q=1
    headers = mapOf("x-trace-id" to "reg-123"),
)
```

## Create-or-register flow

`importSqlTables()` accepts `SqlTableDefinition(name, sql?)` items, runs the SQL (if provided), and registers collections. Existing collection names are reported under `skipped`.

```kotlin
import com.bosbase.sdk.services.SqlTableDefinition

val result = pb.collections.importSqlTables(
    listOf(
        SqlTableDefinition(
            name = "legacy_orders",
            sql = """
                CREATE TABLE IF NOT EXISTS legacy_orders (
                    id TEXT PRIMARY KEY,
                    customer_email TEXT NOT NULL
                );
            """.trimIndent(),
        ),
        SqlTableDefinition(name = "reporting_view"), // assumes table already exists
    ),
)

println(result["created"])
println(result["skipped"])
```

## What It Does

- Creates BosBase collection metadata for the provided tables.
- Generates REST endpoints for CRUD against those tables.
- Applies the standard default API rules (authenticated create; update/delete scoped to the creator).
- Ensures audit columns exist (`created`, `updated`, `createdBy`, `updatedBy`) and leaves all other existing SQL schema and data untouched; no further field mutations or table syncs are performed.
- Marks created collections with `externalTable: true` so you can distinguish them from regular BosBase-managed tables.

## Troubleshooting

- 400 error: ensure `id` exists as `TEXT PRIMARY KEY` and the table name is not system-reserved (no leading `_`).
- 401/403: confirm you are authenticated as a superuser.
- Default audit fields (`created`, `updated`, `createdBy`, `updatedBy`) are auto-added if they’re missing so the default owner rules validate successfully.
