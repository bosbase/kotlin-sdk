package com.bosbase.sdk

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

typealias OnStoreChangeFunc = (token: String?, model: JsonObject?) -> Unit

private const val DEFAULT_COOKIE_KEY = "pb_auth"

/**
 * Base auth store with runtime memory storage and helper utilities (cookie import/export, listeners).
 */
open class BaseAuthStore(
    protected var baseToken: String? = null,
    protected var baseModel: JsonObject? = null,
) {
    protected val json = Json { ignoreUnknownKeys = true }
    private val onChangeCallbacks = mutableListOf<OnStoreChangeFunc>()

    open val token: String?
        get() = baseToken

    open val model: JsonObject?
        get() = baseModel

    open val isValid: Boolean
        get() = !isTokenExpired(token)

    open val isSuperuser: Boolean
        get() {
            val collectionId = model?.get("collectionId")?.jsonPrimitive?.contentOrNull
            return collectionId == "_superusers" || collectionId == "_pbc_2773867675"
        }

    open fun save(newToken: String, newModel: JsonObject?) {
        baseToken = newToken
        baseModel = newModel
        triggerChange()
    }

    open fun clear() {
        baseToken = null
        baseModel = null
        triggerChange()
    }

    fun onChange(callback: OnStoreChangeFunc, fireImmediately: Boolean = false): () -> Unit {
        onChangeCallbacks.add(callback)
        if (fireImmediately) {
            callback(token, model)
        }
        return {
            onChangeCallbacks.remove(callback)
        }
    }

    fun loadFromCookie(cookie: String, key: String = DEFAULT_COOKIE_KEY) {
        val rawValue = parseCookie(cookie)[key] ?: return
        try {
            val parsed = json.parseToJsonElement(rawValue) as? JsonObject
            val parsedToken = parsed?.get("token")?.jsonPrimitive?.content
            val parsedModel = parsed?.get("model") as? JsonObject ?: parsed?.get("record") as? JsonObject
            if (!parsedToken.isNullOrBlank()) {
                save(parsedToken, parsedModel)
            }
        } catch (_: Exception) {
        }
    }

    fun exportToCookie(options: CookieOptions = CookieOptions(), key: String = DEFAULT_COOKIE_KEY): String {
        val defaultExpires = getTokenExp(token)?.let { Date(TimeUnit.SECONDS.toMillis(it)) }
            ?: Date(0)
        val finalOptions = options.copy(expires = options.expires ?: defaultExpires)
        val payload = mapOf(
            "token" to token,
            "model" to model,
            "record" to model, // alias for backward compatibility
        )
        val serialized = json.encodeToString(payload)
        return serializeCookie(key, serialized, finalOptions)
    }

    protected fun triggerChange() {
        onChangeCallbacks.forEach { cb ->
            try {
                cb(token, model)
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * In-memory auth store (equivalent to JS BaseAuthStore runtime store).
 */
open class AuthStore(
    token: String? = null,
    model: JsonObject? = null,
) : BaseAuthStore(token, model)

/**
 * Persistent auth store using Java Preferences (similar to JS LocalAuthStore).
 * Works on JVM (desktop/server). Android callers can implement BaseAuthStore with SharedPreferences.
 */
class LocalAuthStore(
    private val namespace: String = "bosbase.auth",
) : BaseAuthStore() {
    private val prefs = java.util.prefs.Preferences.userRoot().node(namespace)

    override val token: String?
        get() = prefs.get("token", null)

    override val model: JsonObject?
        get() = prefs.get("model", null)?.let {
            runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        }

    override fun save(newToken: String, newModel: JsonObject?) {
        prefs.put("token", newToken)
        if (newModel != null) {
            prefs.put("model", Json.encodeToString(newModel))
        } else {
            prefs.remove("model")
        }
        triggerChange()
    }

    override fun clear() {
        prefs.remove("token")
        prefs.remove("model")
        triggerChange()
    }
}

/**
 * Async persistence helper similar to JS AsyncAuthStore.
 */
class AsyncAuthStore(
    private val saveFunc: (String) -> Unit,
    private val clearFunc: (() -> Unit)? = null,
    initial: (() -> String?)? = null,
) : BaseAuthStore() {
    private val executor = Executors.newSingleThreadExecutor()

    init {
        if (initial != null) {
            executor.execute {
                val payload = runCatching { initial() }.getOrNull()
                if (!payload.isNullOrBlank()) {
                    runCatching {
                        val parsed = Json.parseToJsonElement(payload) as? JsonObject
                        val parsedToken = parsed?.get("token")?.jsonPrimitive?.content
                        val parsedModel = parsed?.get("model") as? JsonObject ?: parsed?.get("record") as? JsonObject
                        if (!parsedToken.isNullOrBlank()) {
                            baseToken = parsedToken
                            baseModel = parsedModel
                            triggerChange()
                        }
                    }
                }
            }
        }
    }

    override fun save(newToken: String, newModel: JsonObject?) {
        baseToken = newToken
        baseModel = newModel
        triggerChange()
        executor.execute {
            val serialized = runCatching { Json.encodeToString(mapOf("token" to newToken, "model" to newModel)) }.getOrDefault("")
            runCatching { saveFunc(serialized) }
        }
    }

    override fun clear() {
        baseToken = null
        baseModel = null
        triggerChange()
        executor.execute {
            if (clearFunc != null) {
                runCatching { clearFunc?.invoke() }
            } else {
                runCatching { saveFunc("") }
            }
        }
    }
}

data class CookieOptions(
    val secure: Boolean = true,
    val sameSite: Boolean = true,
    val httpOnly: Boolean = true,
    val path: String = "/",
    val expires: Date? = null,
)

private fun parseCookie(cookie: String): Map<String, String> {
    return cookie.split(";")
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            key to value
        }
        .toMap()
}

private fun serializeCookie(key: String, value: String, options: CookieOptions): String {
    val parts = mutableListOf("$key=$value")
    if (options.expires != null) {
        parts.add("Expires=${options.expires}")
    }
    if (options.path.isNotBlank()) parts.add("Path=${options.path}")
    if (options.secure) parts.add("Secure")
    if (options.httpOnly) parts.add("HttpOnly")
    if (options.sameSite) parts.add("SameSite=Strict")
    return parts.joinToString("; ")
}

private fun isTokenExpired(token: String?): Boolean {
    val exp = getTokenExp(token) ?: return true
    val now = System.currentTimeMillis() / 1000
    return now >= exp
}

private fun getTokenExp(token: String?): Long? {
    if (token.isNullOrBlank()) return null
    val parts = token.split(".")
    if (parts.size < 2) return null
    return try {
        val payload = Base64.getUrlDecoder().decode(padBase64(parts[1]))
        val obj = Json.parseToJsonElement(String(payload)) as? JsonObject
        obj?.get("exp")?.jsonPrimitive?.longOrNull
    } catch (_: Exception) {
        null
    }
}

private fun padBase64(raw: String): String {
    val padding = (4 - raw.length % 4) % 4
    return raw + "=".repeat(padding)
}
