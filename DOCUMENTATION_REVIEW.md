# Kotlin SDK Documentation Review Report

This document lists discrepancies between the Kotlin SDK documentation and the actual implementation.

## Critical Issues (Methods Documented But Not Implemented)

### 1. API Rules Management Methods (COLLECTIONS.md)

**Documented but missing:**
- `pb.collections.setListRule(collectionIdOrName, rule)`
- `pb.collections.setViewRule(collectionIdOrName, rule)`
- `pb.collections.setCreateRule(collectionIdOrName, rule)`
- `pb.collections.setUpdateRule(collectionIdOrName, rule)`
- `pb.collections.setDeleteRule(collectionIdOrName, rule)`
- `pb.collections.setRules(collectionIdOrName, rules)`
- `pb.collections.getRules(collectionIdOrName)`
- `pb.collections.setManageRule(collectionIdOrName, rule)`
- `pb.collections.setAuthRule(collectionIdOrName, rule)`

**Actual Implementation:**
Rules must be set via collection update using `pb.collections.update()`:
```kotlin
val collection = pb.collections.getOne("articles")
val updated = pb.collections.update("articles", body = mapOf(
    "listRule" to "@request.auth.id != ''",
    "viewRule" to "@request.auth.id != ''",
    // etc.
))
```

**Backend Support:** Rules are stored as part of the collection model and can be updated via PATCH `/api/collections/{collection}`.

### 2. Index Management Methods (COLLECTIONS.md)

**Documented but missing:**
- `pb.collections.addIndex(collectionIdOrName, columns, unique, indexName)`
- `pb.collections.removeIndex(collectionIdOrName, columns)`
- `pb.collections.getIndexes(collectionIdOrName)`

**Actual Implementation:**
Indexes must be managed via collection update using `pb.collections.update()`:
```kotlin
val collection = pb.collections.getOne("articles")
val indexes = (collection["indexes"] as? JsonArray)?.toMutableList() ?: mutableListOf()
indexes.add("CREATE INDEX idx_title ON articles(title)")
val updated = pb.collections.update("articles", body = mapOf(
    "indexes" to indexes
))
```

**Backend Support:** Indexes are stored as part of the collection model and can be updated via PATCH `/api/collections/{collection}`.

### 3. External Auth Methods (AUTHENTICATION.md)

**Documented but missing:**
- `pb.collection("users").listExternalAuths(recordId)`
- `pb.collection("users").unlinkExternalAuth(recordId, provider)`

**Backend Support:** Need to verify if these endpoints exist in the Go backend. They may need to be implemented or the documentation should be removed.

### 4. Schema Query Method Name (SCHEMA_QUERY_API.md)

**Documentation shows:**
```kotlin
val schemas = pb.collections.getSchemas()
```

**Actual Implementation:**
```kotlin
val schemas = pb.collections.getAllSchemas()
```

The method is named `getAllSchemas()`, not `getSchemas()`.

## Parameter Name Mismatches

### 1. confirmPasswordReset (AUTHENTICATION.md)

**Documentation shows:**
```kotlin
pb.collection("users").confirmPasswordReset(
    resetToken = "token_from_email",
    newPassword = "new_password",
    newPasswordConfirm = "new_password"
)
```

**Actual Implementation:**
```kotlin
fun confirmPasswordReset(
    token: String,
    password: String,
    passwordConfirm: String,
    ...
)
```

The parameter is `token`, not `resetToken`.

### 2. confirmVerification (AUTHENTICATION.md)

**Documentation shows:**
```kotlin
pb.collection("users").confirmVerification(
    verificationToken = "token_from_email"
)
```

**Actual Implementation:**
```kotlin
fun confirmVerification(
    token: String,
    ...
)
```

The parameter is `token`, not `verificationToken`.

### 3. confirmEmailChange (AUTHENTICATION.md)

**Documentation shows:**
```kotlin
pb.collection("users").confirmEmailChange(
    emailChangeToken = "token_from_email",
    userPassword = "current_password"
)
```

**Actual Implementation:**
```kotlin
fun confirmEmailChange(
    token: String,
    password: String? = null,
    ...
)
```

The parameters are `token` and optional `password`, not `emailChangeToken` and `userPassword`.

### 4. authWithOAuth2 (AUTHENTICATION.md)

**Documentation shows:**
```kotlin
val oauthData = pb.collection("users").authWithOAuth2(
    provider = "google",
    urlCallback = "myapp://oauth-callback"
)
// Redirect user to oauthData["authURL"]
```

**Actual Implementation:**
```kotlin
fun authWithOAuth2(
    provider: String,
    urlCallback: (String) -> Unit,  // Function, not string
    ...
): JsonObject
```

The `urlCallback` parameter is a function that receives the OAuth URL, not a string URL to return. The method is synchronous and uses realtime subscriptions.

### 5. authWithOTP (AUTHENTICATION.md)

**Documentation may reference:** `authWithOTP` but implementation uses `authWithOtp` (lowercase 'p' in 'otp').

**Actual Implementation:**
```kotlin
fun authWithOtp(
    otpId: String,
    otp: String,
    ...
)
```

## Summary

### High Priority Fixes Needed:
1. Remove or update API Rules Management section in COLLECTIONS.md to reflect that rules must be set via collection update
2. Remove or update Index Management section in COLLECTIONS.md to reflect that indexes must be managed via collection update
3. Fix method name `getSchemas()` → `getAllSchemas()` in SCHEMA_QUERY_API.md
4. Fix parameter names in AUTHENTICATION.md for `confirmPasswordReset`, `confirmVerification`, and `confirmEmailChange`
5. Fix `authWithOAuth2` documentation to show it uses a callback function, not a return value
6. Verify and fix/remove `listExternalAuths` and `unlinkExternalAuth` documentation

### Verified Working Features:
- ✅ Authentication methods (password, OTP, OAuth2, refresh)
- ✅ CRUD operations (getList, getOne, create, update, delete)
- ✅ Collection management (create, update, delete, truncate)
- ✅ Field management (addField, updateField, removeField, getField)
- ✅ Schema query (getSchema, getAllSchemas)
- ✅ Vector API operations
- ✅ LLM Documents API operations
- ✅ File uploads
- ✅ Realtime subscriptions
- ✅ Backups, Crons, Logs, Cache, Health APIs

