# API Rules and Filters - Kotlin SDK Documentation

## Overview

API Rules are your collection access controls and data filters. They control who can perform actions on your collections and what data they can access.

Each collection has 5 rules, corresponding to specific API actions:
- `listRule` - Controls who can list records
- `viewRule` - Controls who can view individual records
- `createRule` - Controls who can create records
- `updateRule` - Controls who can update records
- `deleteRule` - Controls who can delete records

Auth collections have an additional `manageRule` that allows one user to fully manage another user's data.

> 📖 **Reference**: For detailed API rules concepts, see the [JavaScript SDK API Rules documentation](../js-sdk/docs/API_RULES_AND_FILTERS.md).

## Rule Values

Each rule can be set to:

- **`null` (locked)** - Only authorized superusers can perform the action (default)
- **Empty string `""`** - Anyone can perform the action (superusers, authenticated users, and guests)
- **Non-empty string** - Only users that satisfy the filter expression can perform the action

## Important Notes

1. **Rules act as filters**: API Rules also act as record filters. For example, setting `listRule` to `status = "active"` will only return active records.
2. **HTTP Status Codes**: 
   - `200` with empty items for unsatisfied `listRule`
   - `400` for unsatisfied `createRule`
   - `404` for unsatisfied `viewRule`, `updateRule`, `deleteRule`
   - `403` for locked rules when not a superuser
3. **Superuser bypass**: API Rules are ignored when the action is performed by an authorized superuser.

## Setting Rules via SDK

### Kotlin SDK

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")
pb.admins.authWithPassword("admin@example.com", "password")

// Create collection with rules
pb.collections.createBase(
    name = "articles",
    overrides = mapOf(
        "fields" to listOf(
            mapOf("name" to "title", "type" to "text", "required" to true),
            mapOf("name" to "status", "type" to "select", "options" to mapOf(
                "values" to listOf("draft", "published")
            )),
            mapOf("name" to "author", "type" to "relation", "options" to mapOf(
                "collectionId" to "users", "maxSelect" to 1
            ))
        ),
        "listRule" to "@request.auth.id != \"\" || status = \"published\"",
        "viewRule" to "@request.auth.id != \"\" || status = \"published\"",
        "createRule" to "@request.auth.id != \"\"",
        "updateRule" to "author = @request.auth.id || @request.auth.role = \"admin\"",
        "deleteRule" to "author = @request.auth.id || @request.auth.role = \"admin\""
    )
)

// Update rules
pb.collections.update(
    idOrName = "articles",
    body = mapOf(
        "listRule" to "@request.auth.id != \"\" && (status = \"published\" || status = \"draft\")"
    )
)

// Remove rule (set to empty string for public access)
pb.collections.update(
    idOrName = "articles",
    body = mapOf("listRule" to "")  // Anyone can list
)

// Lock rule (set to null for superuser only)
pb.collections.update(
    idOrName = "articles",
    body = mapOf("deleteRule" to null)  // Only superusers can delete
)
```

## Filter Syntax

The syntax follows: `OPERAND OPERATOR OPERAND`

### Operators

**Comparison Operators:**
- `=` - Equal
- `!=` - NOT equal
- `>` - Greater than
- `>=` - Greater than or equal
- `<` - Less than
- `<=` - Less than or equal

**String Operators:**
- `~` - Like/Contains (auto-wraps right operand in `%` for wildcard match)
- `!~` - NOT Like/Contains

**Array Operators (Any/At least one of):**
- `?=` - Any Equal
- `?!=` - Any NOT equal
- `?>` - Any Greater than
- `?>=` - Any Greater than or equal
- `?<` - Any Less than
- `?<=` - Any Less than or equal
- `?~` - Any Like/Contains
- `?!~` - Any NOT Like/Contains

**Logical Operators:**
- `&&` - AND
- `||` - OR
- `()` - Grouping
- `//` - Single line comments

## Special Identifiers

### @request.*

Access current request data:

**@request.context** - The context where the rule is used
```kotlin
"@request.context != \"oauth2\""
```

**@request.method** - HTTP request method
```kotlin
"@request.method = \"PATCH\""
```

**@request.headers.*** - Request headers (normalized to lowercase, `-` replaced with `_`)
```kotlin
"@request.headers.x_token = \"test\""
```

**@request.query.*** - Query parameters
```kotlin
"@request.query.page = \"1\""
```

**@request.auth.*** - Current authenticated user
```kotlin
"@request.auth.id != \"\""  // User is authenticated
"@request.auth.email = \"admin@example.com\""
"@request.auth.role = \"admin\""
```

**@request.body.*** - Submitted body parameters
```kotlin
"@request.body.title != \"\""
"@request.body.status:isset = false"  // Prevent changing status
```

### @collection.*

Target other collections that aren't directly related:
```kotlin
"@collection.permissions.user ?= @request.auth.id && @collection.permissions.resource = id"
```

## Setting Rules Programmatically

### Individual Rules

```kotlin
// Set list rule
pb.collections.setListRule("articles", "@request.auth.id != \"\"")

// Set view rule
pb.collections.setViewRule("articles", "@request.auth.id != \"\"")

// Set create rule
pb.collections.setCreateRule("articles", "@request.auth.id != \"\"")

// Set update rule
pb.collections.setUpdateRule("articles", "@request.auth.id != \"\" && author.id ?= @request.auth.id")

// Set delete rule
pb.collections.setDeleteRule("articles", null)  // Only superusers
```

### Bulk Rule Updates

```kotlin
// Set all rules at once
pb.collections.setRules(
    collectionIdOrName = "articles",
    rules = mapOf(
        "listRule" to "@request.auth.id != \"\"",
        "viewRule" to "@request.auth.id != \"\"",
        "createRule" to "@request.auth.id != \"\"",
        "updateRule" to "@request.auth.id != \"\" && author.id ?= @request.auth.id",
        "deleteRule" to null  // Only superusers
    )
)
```

### Getting Rules

```kotlin
val rules = pb.collections.getRules("articles")
println("List rule: ${rules["listRule"]?.jsonPrimitive?.contentOrNull}")
println("View rule: ${rules["viewRule"]?.jsonPrimitive?.contentOrNull}")
```

## Examples

### Allow Only Registered Users

```kotlin
pb.collections.setListRule(
    collectionIdOrName = "products",
    rule = "@request.auth.id != \"\""
)
```

### Filter by Status

```kotlin
pb.collections.setListRule(
    collectionIdOrName = "products",
    rule = "status = \"active\""
)
```

### Combine Conditions

```kotlin
pb.collections.setListRule(
    collectionIdOrName = "products",
    rule = "@request.auth.id != \"\" && (status = \"active\" || status = \"pending\")"
)
```

### Owner-Based Access

```kotlin
// Users can only update/delete their own records
pb.collections.setUpdateRule(
    collectionIdOrName = "posts",
    rule = "@request.auth.id != \"\" && author.id = @request.auth.id"
)

pb.collections.setDeleteRule(
    collectionIdOrName = "posts",
    rule = "@request.auth.id != \"\" && author.id = @request.auth.id"
)
```

### Prevent Field Modification

```kotlin
// Prevent changing role field
pb.collections.setUpdateRule(
    collectionIdOrName = "users",
    rule = "@request.auth.id != \"\" && @request.body.role:isset = false"
)
```

## Safe Filter Parameter Binding

Use the `filter()` helper when constructing filter strings with user input:

```kotlin
val searchTerm = "user's post"
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
```

## Error Handling

```kotlin
import com.bosbase.sdk.ClientResponseError

try {
    pb.collections.setRules("articles", rules)
} catch (e: ClientResponseError) {
    when (e.status) {
        403 -> println("Access forbidden - superuser required")
        400 -> println("Invalid rule syntax: ${e.response}")
        else -> println("Error: ${e.status}")
    }
}
```

