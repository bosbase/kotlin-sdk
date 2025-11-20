# API Rules Documentation - Kotlin SDK

API Rules are collection access controls and data filters that determine who can perform actions on your collections and what data they can access.

> 📖 **Reference**: For detailed API rules documentation, see [API_RULES_AND_FILTERS.md](API_RULES_AND_FILTERS.md) and the [JavaScript SDK API Rules documentation](../js-sdk/docs/api-rules.md).

## Overview

Each collection has 5 standard API rules, corresponding to specific API actions:

- **`listRule`** - Controls read/list access
- **`viewRule`** - Controls read/view access  
- **`createRule`** - Controls create access
- **`updateRule`** - Controls update access
- **`deleteRule`** - Controls delete access

Auth collections have two additional rules:

- **`manageRule`** - Admin-like permissions for managing auth records
- **`authRule`** - Additional constraints applied during authentication

## Rule Values

Each rule can be set to one of three values:

### 1. `null` (Locked)
Only authorized superusers can perform the action.

```kotlin
pb.collections.setListRule("products", null)
```

### 2. `""` (Empty String - Public)
Anyone (superusers, authorized users, and guests) can perform the action.

```kotlin
pb.collections.setListRule("products", "")
```

### 3. Non-empty String (Filter Expression)
Only users satisfying the filter expression can perform the action.

```kotlin
pb.collections.setListRule("products", "@request.auth.id != \"\"")
```

## Setting Rules

### Individual Rules

```kotlin
// Set list rule
pb.collections.setListRule("products", "@request.auth.id != \"\"")

// Set view rule
pb.collections.setViewRule("products", "@request.auth.id != \"\"")

// Set create rule
pb.collections.setCreateRule("products", "@request.auth.id != \"\"")

// Set update rule
pb.collections.setUpdateRule("products", "@request.auth.id != \"\" && author.id ?= @request.auth.id")

// Set delete rule
pb.collections.setDeleteRule("products", null)  // Only superusers
```

### Bulk Rule Updates

```kotlin
pb.collections.setRules("products", mapOf(
    "listRule" to "@request.auth.id != \"\"",
    "viewRule" to "@request.auth.id != \"\"",
    "createRule" to "@request.auth.id != \"\"",
    "updateRule" to "@request.auth.id != \"\" && author.id ?= @request.auth.id",
    "deleteRule" to null  // Only superusers
))
```

### Getting Rules

```kotlin
val rules = pb.collections.getRules("products")
println(rules["listRule"]?.jsonPrimitive?.contentOrNull)
println(rules["viewRule"]?.jsonPrimitive?.contentOrNull)
```

## Common Examples

### Allow Only Registered Users

```kotlin
pb.collections.setListRule("products", "@request.auth.id != \"\"")
```

### Filter by Status

```kotlin
pb.collections.setListRule("products", "status = \"active\"")
```

### Owner-Based Update/Delete

```kotlin
// Users can only update/delete their own records
pb.collections.setUpdateRule("posts", "@request.auth.id != \"\" && author.id = @request.auth.id")
pb.collections.setDeleteRule("posts", "@request.auth.id != \"\" && author.id = @request.auth.id")
```

## Best Practices

1. **Start with locked rules** (null) for security, then gradually open access as needed
2. **Use relation checks** for owner-based access patterns
3. **Combine multiple conditions** using `&&` and `||` for complex scenarios
4. **Test rules thoroughly** before deploying to production
5. **Use empty string (`""`)** only when you truly want public access

## Error Responses

API Rules also act as data filters. When a request doesn't satisfy a rule:

- **listRule** - Returns `200` with empty items (filters out records)
- **createRule** - Returns `400` Bad Request
- **viewRule** - Returns `404` Not Found
- **updateRule** - Returns `404` Not Found
- **deleteRule** - Returns `404` Not Found
- **All rules** - Return `403` Forbidden if locked (null) and user is not superuser

## Notes

- **Superusers bypass all rules** - Rules are ignored when the action is performed by an authorized superuser
- **Rules are evaluated server-side** - Client-side validation is not enough
- **Comments are supported** - Use `//` for single-line comments in rules

