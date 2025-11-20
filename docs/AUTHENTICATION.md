# Authentication - Kotlin SDK Documentation

## Overview

Authentication in BosBase is stateless and token-based. A client is considered authenticated as long as it sends a valid `Authorization: YOUR_AUTH_TOKEN` header with requests.

**Key Points:**
- **No sessions**: BosBase APIs are fully stateless (tokens are not stored in the database)
- **No logout endpoint**: To "logout", simply clear the token from your local state (`pb.authStore.clear()`)
- **Token generation**: Auth tokens are generated through auth collection Web APIs or programmatically
- **Admin users**: `_superusers` collection works like regular auth collections but with full access (API rules are ignored)
- **OAuth2 limitation**: OAuth2 is not supported for `_superusers` collection

> 📖 **Reference**: For detailed authentication concepts, see the [JavaScript SDK Authentication documentation](../js-sdk/docs/AUTHENTICATION.md).

## Authentication Methods

BosBase supports multiple authentication methods that can be configured individually for each auth collection:

1. **Password Authentication** - Email/username + password
2. **OTP Authentication** - One-time password via email
3. **OAuth2 Authentication** - Google, GitHub, Microsoft, etc.
4. **Multi-factor Authentication (MFA)** - Requires 2 different auth methods

## Authentication Store

The SDK maintains an `authStore` that automatically manages the authentication state:

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.LocalAuthStore

val pb = BosBase("http://localhost:8090")

// Check authentication status
println(pb.authStore.isValid)      // true/false
println(pb.authStore.token)         // current auth token
println(pb.authStore.model)          // authenticated user record (JsonObject)

// Clear authentication (logout)
pb.authStore.clear()
```

### Auth Store Types

The Kotlin SDK provides several auth store implementations:

#### LocalAuthStore (Default)

Uses Java Preferences for persistence (works on JVM desktop/server applications):

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.LocalAuthStore

val store = LocalAuthStore(namespace = "myapp.auth")
val pb = BosBase("http://localhost:8090", authStore = store)
```

#### AsyncAuthStore

For async storage implementations (useful for Android with SharedPreferences or custom storage):

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.AsyncAuthStore

// Example with custom async storage
val store = AsyncAuthStore(
    saveFunc = { serialized ->
        // Save to your async storage
        sharedPreferences.edit().putString("pb_auth", serialized).apply()
    },
    clearFunc = {
        // Clear from your async storage
        sharedPreferences.edit().remove("pb_auth").apply()
    },
    initial = {
        // Load from your async storage
        sharedPreferences.getString("pb_auth", null)
    }
)

val pb = BosBase("http://localhost:8090", authStore = store)
```

#### Custom Auth Store

You can extend `BaseAuthStore` to create your own custom implementation:

```kotlin
import com.bosbase.sdk.BaseAuthStore
import kotlinx.serialization.json.JsonObject

class CustomAuthStore : BaseAuthStore() {
    override fun save(newToken: String, newModel: JsonObject?) {
        super.save(newToken, newModel)
        // Your custom business logic here
    }
}

val pb = BosBase("http://localhost:8090", authStore = CustomAuthStore())
```

### Listening to Auth Changes

You can register callbacks to listen for authentication state changes:

```kotlin
// Register a listener
val removeListener = pb.authStore.onChange { token, model ->
    println("Auth state changed!")
    println("Token: $token")
    println("Model: ${model?.get("email")?.jsonPrimitive?.content}")
}

// Fire immediately on registration
val removeListener2 = pb.authStore.onChange({ token, model ->
    println("Current auth state: $token")
}, fireImmediately = true)

// Remove listener when done
removeListener()
```

## Password Authentication

Authenticate using email/username and password. The identity field can be configured in the collection options (default is email).

**Backend Endpoint:** `POST /api/collections/{collection}/auth-with-password`

### Basic Usage

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Authenticate with email and password
val authData = pb.collection("users").authWithPassword(
    identity = "test@example.com",
    password = "password123"
)

// Auth data is automatically stored in pb.authStore
println(pb.authStore.isValid)  // true
println(pb.authStore.token)     // JWT token
println(authData["record"]?.jsonObject?.get("id")?.jsonPrimitive?.content) // user record ID
```

### With Coroutines

```kotlin
import kotlinx.coroutines.runBlocking
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

runBlocking {
    try {
        val authData = pb.collection("users").authWithPassword(
            identity = "test@example.com",
            password = "password123"
        )
        println("Authenticated: ${authData["record"]?.jsonObject?.get("email")?.jsonPrimitive?.content}")
    } catch (e: ClientResponseError) {
        println("Authentication failed: ${e.status} - ${e.response}")
    }
}
```

### Response Format

The `authWithPassword` method returns a `JsonObject` with the following structure:

```kotlin
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "record": {
        "id": "record_id",
        "email": "test@example.com",
        // ... other user fields
    }
}
```

### Error Handling with MFA

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError

val pb = BosBase("http://localhost:8090")

try {
    val authData = pb.collection("users").authWithPassword(
        identity = "test@example.com",
        password = "pass123"
    )
} catch (e: ClientResponseError) {
    // Check for MFA requirement
    val mfaId = e.response?.get("mfaId")?.jsonPrimitive?.content
    if (mfaId != null) {
        // Handle MFA flow (see Multi-factor Authentication section)
        println("MFA required: $mfaId")
    } else {
        println("Authentication failed: ${e.status}")
    }
}
```

## OTP Authentication

One-time password authentication via email.

**Backend Endpoints:**
- `POST /api/collections/{collection}/request-otp` - Request OTP
- `POST /api/collections/{collection}/auth-with-otp` - Authenticate with OTP

### Request OTP

```kotlin
val pb = BosBase("http://localhost:8090")

// Request OTP email
pb.collection("users").requestOTP(
    email = "test@example.com"
)
```

### Authenticate with OTP

```kotlin
// After receiving the OTP via email
val authData = pb.collection("users").authWithOTP(
    otpId = "otp_id_from_email",
    password = "otp_code_from_email"
)
```

## OAuth2 Authentication

Authenticate using OAuth2 providers (Google, GitHub, Microsoft, etc.).

**Backend Endpoint:** `POST /api/collections/{collection}/auth-with-oauth2`

### Basic OAuth2 Flow

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://localhost:8090")

// Start OAuth2 flow with callback function
val authData = pb.collection("users").authWithOAuth2(
    provider = "google",
    urlCallback = { oauthUrl ->
        // Open the OAuth URL in browser/WebView
        // The SDK will automatically handle the redirect via realtime subscription
        openUrl(oauthUrl)  // Your function to open URL
    }
)

// The method returns after authentication completes
println("Authenticated: ${authData["record"]?.jsonObject?.get("email")?.jsonPrimitive?.content}")

// Alternative: Manual OAuth2 flow with code
val authMethods = pb.collection("users").listAuthMethods()
// Get provider info, then redirect user, then:
val authData2 = pb.collection("users").authWithOAuth2Code(
    provider = "google",
    code = "authorization_code",
    codeVerifier = "code_verifier_from_auth_methods",
    redirectURL = "myapp://oauth-callback"
)
```

## Token Refresh

Refresh the authentication token to extend the session:

```kotlin
val pb = BosBase("http://localhost:8090")

// Refresh the current token
val refreshed = pb.collection("users").authRefresh()

println("New token: ${refreshed["token"]?.jsonPrimitive?.content}")
```

### Auto-Refresh

The SDK supports automatic token refresh before expiration:

```kotlin
val pb = BosBase("http://localhost:8090")

// Register auto-refresh (for superuser/admin authentication)
pb.registerAutoRefresh(
    thresholdSeconds = 60, // Refresh 60 seconds before expiration
    refreshFunc = {
        // Refresh function
        pb.admins.authRefresh()
    },
    reauthenticateFunc = {
        // Re-authenticate function (if refresh fails)
        pb.admins.authWithPassword("admin@example.com", "password")
    }
)
```

## Email Verification

### Request Verification Email

```kotlin
val pb = BosBase("http://localhost:8090")

pb.collection("users").requestVerification(
    email = "test@example.com"
)
```

### Confirm Verification

```kotlin
val pb = BosBase("http://localhost:8090")

pb.collection("users").confirmVerification(
    token = "token_from_email"
)
```

## Password Reset

### Request Password Reset

```kotlin
val pb = BosBase("http://localhost:8090")

pb.collection("users").requestPasswordReset(
    email = "test@example.com"
)
```

### Confirm Password Reset

```kotlin
val pb = BosBase("http://localhost:8090")

pb.collection("users").confirmPasswordReset(
    token = "token_from_email",
    password = "new_password",
    passwordConfirm = "new_password"
)
```

## Email Change

### Request Email Change

```kotlin
val pb = BosBase("http://localhost:8090")

// Must be authenticated
pb.collection("users").requestEmailChange(
    newEmail = "newemail@example.com"
)
```

### Confirm Email Change

```kotlin
val pb = BosBase("http://localhost:8090")

pb.collection("users").confirmEmailChange(
    token = "token_from_email",
    password = "current_password"  // Optional: only required if password change is configured
)
```

## External Auth Providers

> **Note**: External auth management methods (`listExternalAuths`, `unlinkExternalAuth`) are currently not implemented in the Kotlin SDK. If you need this functionality, you can access the underlying records directly via the `_externalAuths` collection or implement custom endpoints in the backend.

### Accessing External Auths (Workaround)

```kotlin
val pb = BosBase("http://localhost:8090")

// Authenticate as admin to access _externalAuths collection
pb.admins.authWithPassword("admin@example.com", "password")

// List external auths for a user record
val externalAuths = pb.collection("_externalAuths").getList(
    filter = "userId = 'user_record_id'"
)

// Delete/unlink external auth (if you have the external auth record ID)
pb.collection("_externalAuths").delete("external_auth_record_id")
```

## Admin Authentication

The `_superusers` collection works like a regular auth collection but with full access:

```kotlin
val pb = BosBase("http://localhost:8090")

// Authenticate as admin
val adminAuth = pb.admins.authWithPassword(
    identity = "admin@example.com",
    password = "admin_password"
)

// Now all requests will have admin privileges
val collections = pb.collections.getList()
```

## Cookie Support

The SDK provides helper methods for cookie-based authentication (useful for server-side rendering):

### Load from Cookie

```kotlin
val pb = BosBase("http://localhost:8090")

// Load auth state from cookie string
pb.authStore.loadFromCookie(
    cookie = "pb_auth=eyJ0b2tlbiI6...",
    key = "pb_auth"
)
```

### Export to Cookie

```kotlin
import com.bosbase.sdk.CookieOptions
import java.util.Date

val pb = BosBase("http://localhost:8090")

// Export auth state as cookie string
val cookieString = pb.authStore.exportToCookie(
    options = CookieOptions(
        secure = true,
        httpOnly = true,
        sameSite = true,
        path = "/",
        expires = Date(System.currentTimeMillis() + 86400000) // 1 day
    ),
    key = "pb_auth"
)

// Use cookieString in HTTP response headers
```

## Error Handling

All authentication methods throw `ClientResponseError` on failure:

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError

val pb = BosBase("http://localhost:8090")

try {
    val authData = pb.collection("users").authWithPassword(
        identity = "test@example.com",
        password = "wrong_password"
    )
} catch (e: ClientResponseError) {
    println("Error URL: ${e.url}")
    println("Status Code: ${e.status}")
    println("Response: ${e.response}")
    println("Is Abort: ${e.isAbort}")
    println("Original Error: ${e.originalError}")
}
```

## Examples

### Complete Authentication Flow

```kotlin
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.ClientResponseError
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val pb = BosBase("http://localhost:8090")
    
    try {
        // 1. Authenticate
        val authData = pb.collection("users").authWithPassword(
            identity = "user@example.com",
            password = "password123"
        )
        
        println("Authenticated as: ${authData["record"]?.jsonObject?.get("email")?.jsonPrimitive?.content}")
        
        // 2. Check auth status
        println("Is valid: ${pb.authStore.isValid}")
        println("Token: ${pb.authStore.token?.take(20)}...")
        
        // 3. Use authenticated client
        val records = pb.collection("posts").getList(page = 1, perPage = 10)
        println("Fetched ${records.items.size} records")
        
        // 4. Logout
        pb.authStore.clear()
        println("Logged out")
        
    } catch (e: ClientResponseError) {
        println("Error: ${e.status} - ${e.response}")
    }
}
```

### Android Example with SharedPreferences

```kotlin
import android.content.Context
import com.bosbase.sdk.BosBase
import com.bosbase.sdk.AsyncAuthStore

class BosBaseManager(context: Context) {
    private val prefs = context.getSharedPreferences("bosbase_auth", Context.MODE_PRIVATE)
    
    private val authStore = AsyncAuthStore(
        saveFunc = { serialized ->
            prefs.edit().putString("auth_data", serialized).apply()
        },
        clearFunc = {
            prefs.edit().remove("auth_data").apply()
        },
        initial = {
            prefs.getString("auth_data", null)
        }
    )
    
    val client = BosBase(
        baseUrl = "https://your-app.bosbase.com",
        authStore = authStore
    )
}
```

