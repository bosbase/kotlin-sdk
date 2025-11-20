# Built-in Users Collection Guide - Kotlin SDK Documentation

This guide explains how to use the built-in `users` collection for authentication, registration, and API rules. **The `users` collection is automatically created when BosBase is initialized and does not need to be created manually.**

> 📖 **Reference**: For detailed users collection concepts, see the [JavaScript SDK Users Collection Guide](../js-sdk/docs/USERS_COLLECTION_GUIDE.md) and [AUTHENTICATION.md](AUTHENTICATION.md).

## Overview

The `users` collection is a **built-in auth collection** that is automatically created when BosBase starts. It has:

- **Collection ID**: `_pb_users_auth_`
- **Collection Name**: `users`
- **Type**: `auth` (authentication collection)
- **Purpose**: User accounts, authentication, and authorization

**Important**: 
- ✅ **DO NOT** create a new `users` collection manually
- ✅ **DO** use the existing built-in `users` collection
- ✅ The collection already has proper API rules configured
- ✅ It supports password, OAuth2, and OTP authentication

## Getting Users Collection Information

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Get the users collection details
val usersCollection = pb.collections.getOne("users")
// or by ID
val usersCollectionById = pb.collections.getOne("_pb_users_auth_")

val id = usersCollection["id"]?.jsonPrimitive?.contentOrNull
val name = usersCollection["name"]?.jsonPrimitive?.contentOrNull
val type = usersCollection["type"]?.jsonPrimitive?.contentOrNull
val fields = usersCollection["fields"]?.jsonArray

println("Collection ID: $id")
println("Collection Name: $name")
println("Collection Type: $type")
println("Fields: ${fields?.size ?: 0}")
```

## User Registration

### Register a New User

```kotlin
// Register a new user (public access)
val newUser = pb.collection("users").create(
    body = mapOf(
        "email" to "user@example.com",
        "password" to "password123",
        "passwordConfirm" to "password123",
        "name" to "John Doe"
    )
)

println("User registered: ${newUser["id"]?.jsonPrimitive?.contentOrNull}")
```

## User Login/Authentication

### Password Authentication

```kotlin
// Authenticate with email and password
val authData = pb.collection("users").authWithPassword(
    identity = "user@example.com",
    password = "password123"
)

println("Authenticated: ${pb.authStore.isValid}")
println("Token: ${pb.authStore.token?.take(20)}...")
```

### OAuth2 Authentication

```kotlin
// Authenticate with OAuth2
val oauthData = pb.collection("users").authWithOAuth2(
    provider = "google",
    urlCallback = "myapp://oauth-callback"
)

// Handle OAuth2 callback
val authData = pb.collection("users").authWithOAuth2Code(
    provider = "google",
    code = "authorization_code",
    codeVerifier = oauthData["codeVerifier"]?.jsonPrimitive?.contentOrNull ?: "",
    redirectUrl = "myapp://oauth-callback"
)
```

### OTP Authentication

```kotlin
// Request OTP
pb.collection("users").requestOTP(
    email = "user@example.com"
)

// Authenticate with OTP
val authData = pb.collection("users").authWithOTP(
    otpId = "otp_id_from_email",
    password = "otp_code_from_email"
)
```

## API Rules and Filters with Users

The built-in `users` collection has default API rules that allow:

- Users can only view/list themselves
- Anyone can register (public)
- Users can only update/delete themselves

### Check Current Rules

```kotlin
val rules = pb.collections.getRules("users")

val listRule = rules["listRule"]?.jsonPrimitive?.contentOrNull
val viewRule = rules["viewRule"]?.jsonPrimitive?.contentOrNull
val createRule = rules["createRule"]?.jsonPrimitive?.contentOrNull
val updateRule = rules["updateRule"]?.jsonPrimitive?.contentOrNull
val deleteRule = rules["deleteRule"]?.jsonPrimitive?.contentOrNull

println("List rule: $listRule")
println("View rule: $viewRule")
println("Create rule: $createRule")
println("Update rule: $updateRule")
println("Delete rule: $deleteRule")
```

### Update Rules (Admin Only)

```kotlin
// Update rules (requires superuser)
pb.admins.authWithPassword("admin@example.com", "password")

pb.collections.setRules(
    collectionIdOrName = "users",
    rules = mapOf(
        "listRule" to "id = @request.auth.id",
        "viewRule" to "id = @request.auth.id",
        "createRule" to "",  // Public registration
        "updateRule" to "id = @request.auth.id",
        "deleteRule" to "id = @request.auth.id"
    )
)
```

## Using Users with Other Collections

### Reference Users in Relations

```kotlin
// Create a post collection with user relation
pb.collections.createBase(
    name = "posts",
    overrides = mapOf(
        "fields" to listOf(
            mapOf("name" to "title", "type" to "text", "required" to true),
            mapOf(
                "name" to "author",
                "type" to "relation",
                "options" to mapOf(
                    "collectionId" to "users",  // Reference users collection
                    "maxSelect" to 1
                )
            )
        )
    )
)
```

### Create Post with User Reference

```kotlin
// Authenticate first
pb.collection("users").authWithPassword("user@example.com", "password")
val userId = pb.authStore.model?.get("id")?.jsonPrimitive?.contentOrNull

// Create post with author reference
val post = pb.collection("posts").create(
    body = mapOf(
        "title" to "My First Post",
        "author" to userId  // Reference authenticated user
    )
)
```

## Complete Examples

### User Registration and Login Flow

```kotlin
fun userRegistrationFlow(pb: BosBase) {
    // Register new user
    val newUser = pb.collection("users").create(
        body = mapOf(
            "email" to "newuser@example.com",
            "password" to "password123",
            "passwordConfirm" to "password123",
            "name" to "New User"
        )
    )
    
    println("User registered: ${newUser["email"]?.jsonPrimitive?.contentOrNull}")
    
    // Login
    val authData = pb.collection("users").authWithPassword(
        identity = "newuser@example.com",
        password = "password123"
    )
    
    println("Logged in: ${pb.authStore.isValid}")
    println("User ID: ${pb.authStore.model?.get("id")?.jsonPrimitive?.contentOrNull}")
}
```

### User Profile Management

```kotlin
fun updateUserProfile(pb: BosBase, name: String, avatar: File?) {
    // Must be authenticated
    if (!pb.authStore.isValid) {
        throw IllegalStateException("User must be authenticated")
    }
    
    val userId = pb.authStore.model?.get("id")?.jsonPrimitive?.contentOrNull ?: return
    
    val body = mutableMapOf<String, Any?>(
        "name" to name
    )
    
    if (avatar != null) {
        val attachment = FileAttachment.fromFile(avatar)
        pb.collection("users").update(
            id = userId,
            body = body,
            files = mapOf("avatar" to listOf(attachment))
        )
    } else {
        pb.collection("users").update(
            id = userId,
            body = body
        )
    }
    
    println("Profile updated")
}
```

