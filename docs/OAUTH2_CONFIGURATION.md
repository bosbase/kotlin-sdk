# OAuth2 Configuration Guide - Kotlin SDK Documentation

This guide explains how to configure OAuth2 authentication providers for auth collections using the BosBase Kotlin SDK.

> 📖 **Reference**: For detailed OAuth2 concepts, see the [JavaScript SDK OAuth2 Configuration documentation](../js-sdk/docs/OAUTH2_CONFIGURATION.md) and [AUTHENTICATION.md](AUTHENTICATION.md).

## Overview

OAuth2 allows users to authenticate with your application using third-party providers like Google, GitHub, Facebook, etc. Before you can use OAuth2 authentication, you need to:

1. **Create an OAuth2 app** in the provider's dashboard
2. **Obtain Client ID and Client Secret** from the provider
3. **Register a redirect URL** (typically: `https://yourdomain.com/api/oauth2-redirect`)
4. **Configure the provider** in your BosBase auth collection using the SDK

## Prerequisites

- An auth collection in your BosBase instance
- OAuth2 app credentials (Client ID and Client Secret) from your chosen provider
- Admin/superuser authentication to configure collections

## Supported Providers

The following OAuth2 providers are supported:

- **google** - Google OAuth2
- **github** - GitHub OAuth2
- **gitlab** - GitLab OAuth2
- **discord** - Discord OAuth2
- **facebook** - Facebook OAuth2
- **microsoft** - Microsoft OAuth2
- **apple** - Apple Sign In
- **twitter** - Twitter OAuth2
- **spotify** - Spotify OAuth2
- And more...

## Basic Usage

### 1. Enable OAuth2 for a Collection

First, enable OAuth2 authentication for your auth collection:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("https://your-instance.com")

// Authenticate as admin
pb.admins.authWithPassword("admin@example.com", "password")

// Get collection and update OAuth2 settings
val collection = pb.collections.getOne("users")
val oauth2Config = collection["oauth2"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

// Enable OAuth2
oauth2Config["enabled"] = true

pb.collections.update(
    idOrName = "users",
    body = mapOf("oauth2" to oauth2Config)
)
```

### 2. Configure OAuth2 Provider

Add a provider configuration to your collection:

```kotlin
// Configure Google OAuth2 provider
val collection = pb.collections.getOne("users")
val oauth2Config = collection["oauth2"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
val providers = (oauth2Config["providers"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()

providers["google"] = mapOf(
    "clientId" to "your-google-client-id",
    "clientSecret" to "your-google-client-secret",
    "authURL" to "https://accounts.google.com/o/oauth2/v2/auth",
    "tokenURL" to "https://oauth2.googleapis.com/token",
    "userInfoURL" to "https://www.googleapis.com/oauth2/v2/userinfo",
    "displayName" to "Google",
    "pkce" to true
)

oauth2Config["providers"] = providers

pb.collections.update(
    idOrName = "users",
    body = mapOf("oauth2" to oauth2Config)
)
```

### 3. Authenticate with OAuth2

```kotlin
// Start OAuth2 flow
val oauthData = pb.collection("users").authWithOAuth2(
    provider = "google",
    urlCallback = "myapp://oauth-callback"
)

// Redirect user to oauthData["authURL"]
// After user authorizes, handle the callback with the code
val authData = pb.collection("users").authWithOAuth2Code(
    provider = "google",
    code = "authorization_code",
    codeVerifier = oauthData["codeVerifier"]?.jsonPrimitive?.contentOrNull ?: "",
    redirectUrl = "myapp://oauth-callback"
)
```

## Examples

### Complete OAuth2 Setup

```kotlin
fun setupOAuth2(pb: BosBase, providerName: String, clientId: String, clientSecret: String) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    // Get collection
    val collection = pb.collections.getOne("users")
    val oauth2Config = collection["oauth2"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
    val providers = (oauth2Config["providers"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
    
    // Configure provider based on provider name
    when (providerName) {
        "google" -> {
            providers["google"] = mapOf(
                "clientId" to clientId,
                "clientSecret" to clientSecret,
                "authURL" to "https://accounts.google.com/o/oauth2/v2/auth",
                "tokenURL" to "https://oauth2.googleapis.com/token",
                "userInfoURL" to "https://www.googleapis.com/oauth2/v2/userinfo",
                "displayName" to "Google",
                "pkce" to true
            )
        }
        "github" -> {
            providers["github"] = mapOf(
                "clientId" to clientId,
                "clientSecret" to clientSecret,
                "authURL" to "https://github.com/login/oauth/authorize",
                "tokenURL" to "https://github.com/login/oauth/access_token",
                "userInfoURL" to "https://api.github.com/user",
                "displayName" to "GitHub"
            )
        }
        // Add more providers as needed
    }
    
    oauth2Config["enabled"] = true
    oauth2Config["providers"] = providers
    
    pb.collections.update(
        idOrName = "users",
        body = mapOf("oauth2" to oauth2Config)
    )
    
    println("OAuth2 provider '$providerName' configured successfully")
}
```

