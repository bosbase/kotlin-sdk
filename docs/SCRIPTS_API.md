# Scripts API - Kotlin SDK

## Overview

`pb.scripts` provides superuser-only helpers for storing and managing function code snippets through the `/api/scripts` endpoints. The backend persists the content and bumps `version` whenever a script is updated.

**Table schema**
- `id` (uuidv7, auto-generated)
- `name` (primary key)
- `content` (script body)
- `description` (optional)
- `version` (starts at 1, increments on update)
- `created`, `updated` (ISO timestamps)

## Authentication

Authenticate as a superuser before calling any Scripts API method:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")
pb.collection("_superusers").authWithPassword("admin@example.com", "password")
```

## Creating a Script

`pb.scripts.create` creates the table if it does not exist, writes the script, and returns the stored row with `version = 1`.

```kotlin
val pythonCode = """
def main():
    print("Hello from functions!")


if __name__ == "__main__":
    main()
""".trimIndent()

val script = pb.scripts.create(
    name = "hello.py",
    content = pythonCode,
    description = "Hello from functions!",
)

println(script?.get("id"))
println(script?.get("version"))
```

## Reading Scripts

Fetch a single script by name or list all scripts:

```kotlin
val script = pb.scripts.get("hello.py")
println(script?.get("content"))

val allScripts = pb.scripts.list()
println(allScripts.map { it["name"] })
```

## Updating Scripts (auto-versioned)

Updates increment `version` by 1 automatically and refresh `updated`.

```kotlin
val updated = pb.scripts.update(
    name = "hello.py",
    content = """
def main():
    print("Hi from functions!")
""".trimIndent(),
    description = "Now returns both total and count",
)

println(updated?.get("version"))
```

You can update just the description if the code is unchanged:

```kotlin
pb.scripts.update(name = "hello.py", description = "Docs-only tweak")
```

## Executing Scripts

Run a stored script via the backend runner (`/api/scripts/{name}/execute`). Execution permission is controlled by `pb.scriptsPermissions`:
- `anonymous`: anyone can execute
- `user`: authenticated users (and superusers)
- `superuser`: only superusers (default when no permission entry exists)

```kotlin
val result = pb.scripts.execute("hello.py")
println(result?.get("output"))
```

## Managing Script Permissions

Use `pb.scriptsPermissions` to control who can call `/api/scripts/{name}/execute`.
Allowed `content` values: `"anonymous"`, `"user"`, `"superuser"`.

```kotlin
// create or update permissions (superuser required)
pb.scriptsPermissions.create(
    scriptName = "hello.py",
    content = "user",
)

val perm = pb.scriptsPermissions.get("hello.py")
println(perm?.get("content"))

pb.scriptsPermissions.update("hello.py", content = "anonymous")
pb.scriptsPermissions.delete("hello.py") // back to superuser-only execution
```

## Running Shell Commands

Run arbitrary shell commands in the functions directory (defaults to `EXECUTE_PATH` or `/pb/functions`). Superuser authentication is required.

```kotlin
val result = pb.scripts.command("""cat pyproject.toml""")
println(result?.get("output"))
```

Notes for `command`:
- Runs inside `EXECUTE_PATH` and inherits environment variables.
- Combined stdout/stderr is returned as `output`; non-zero exit codes surface as errors.

## Deleting Scripts

Remove a script by name. Returns `true` when a row was deleted.

```kotlin
val removed = pb.scripts.delete("hello.py")
println(removed)
```

## Notes

- Script CRUD and `scriptsPermissions` require superuser auth; `scripts.execute` obeys the stored permission level; `command` is superuser-only.
- `id` is generated as a UUIDv7 string on insert and backfilled automatically for older rows.
- Execution uses the directory from `EXECUTE_PATH` env/docker-compose (default `/pb/functions`) and expects a `.venv` there with Python available.
- Content is stored as plain text.
