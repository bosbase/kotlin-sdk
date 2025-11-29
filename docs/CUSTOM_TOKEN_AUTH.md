# Custom Token Binding and Login - Kotlin SDK

The Kotlin SDK now supports binding a custom token to an auth record (`users` or `_superusers`) and signing in with that token. The server stores hashed bindings in `_token_bindings` (created automatically on first bind).

## API endpoints
- `POST /api/collections/{collection}/bind-token`
- `POST /api/collections/{collection}/unbind-token`
- `POST /api/collections/{collection}/auth-with-token`

## Binding a token
```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// bind for a regular user
pb.collection("users").bindCustomToken(
    email = "user@example.com",
    password = "user-password",
    token = "my-app-token",
)

// bind for a superuser
pb.collection("_superusers").bindCustomToken(
    email = "admin@example.com",
    password = "admin-password",
    token = "admin-app-token",
)
```

## Unbinding a token
```kotlin
// stop accepting the token for the user
pb.collection("users").unbindCustomToken(
    email = "user@example.com",
    password = "user-password",
    token = "my-app-token",
)

// stop accepting the token for a superuser
pb.collection("_superusers").unbindCustomToken(
    email = "admin@example.com",
    password = "admin-password",
    token = "admin-app-token",
)
```

## Logging in with a token
```kotlin
val auth = pb.collection("users").authWithToken("my-app-token")

println(auth["token"])   // BosBase auth token
println(auth["record"])  // authenticated record

// superuser token login
val superAuth = pb.collection("_superusers").authWithToken("admin-app-token")
println(superAuth["token"])
println(superAuth["record"])
```

Notes:
- Binding and unbinding require a valid email and password for the target account.
- The same token value can be used for either `users` or `_superusers`; the collection is enforced during login.
- MFA and existing auth rules still apply when authenticating with a token.
