package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class CacheService(client: BosBase) : BaseService(client) {
    fun list(query: Map<String, Any?>? = null, headers: Map<String, String>? = null): List<JsonObject> {
        val data = client.send("/api/cache", query = query, headers = headers)

        // Server returns either an array or an object with `items` (aligned to JS SDK)
        return when (data) {
            is JsonArray -> data.mapNotNull { it as? JsonObject }
            is JsonObject -> (data["items"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            else -> emptyList()
        }
    }

    fun create(
        name: String,
        sizeBytes: Int? = null,
        defaultTTLSeconds: Int? = null,
        readTimeoutMs: Int? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val payload = mutableMapOf<String, Any?>("name" to name)
        if (sizeBytes != null) payload["sizeBytes"] = sizeBytes
        if (defaultTTLSeconds != null) payload["defaultTTLSeconds"] = defaultTTLSeconds
        if (readTimeoutMs != null) payload["readTimeoutMs"] = readTimeoutMs
        if (body != null) payload.putAll(body)
        return client.send("/api/cache", method = "POST", body = payload, query = query, headers = headers) as? JsonObject
    }

    fun update(
        name: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("/api/cache/${encodePath(name)}", method = "PATCH", body = body, query = query, headers = headers) as? JsonObject

    fun delete(name: String, query: Map<String, Any?>? = null, headers: Map<String, String>? = null) {
        client.send("/api/cache/${encodePath(name)}", method = "DELETE", query = query, headers = headers)
    }

    fun setEntry(
        cache: String,
        key: String,
        value: Any,
        ttlSeconds: Int? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val payload = mutableMapOf<String, Any?>("value" to value)
        if (ttlSeconds != null) payload["ttlSeconds"] = ttlSeconds
        if (body != null) payload.putAll(body)
        return client.send(
            "/api/cache/${encodePath(cache)}/entries/${encodePath(key)}",
            method = "PUT",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun getEntry(
        cache: String,
        key: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send(
            "/api/cache/${encodePath(cache)}/entries/${encodePath(key)}",
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun renewEntry(
        cache: String,
        key: String,
        ttlSeconds: Int? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val payload = mutableMapOf<String, Any?>()
        if (ttlSeconds != null) payload["ttlSeconds"] = ttlSeconds
        if (body != null) payload.putAll(body)
        return client.send(
            "/api/cache/${encodePath(cache)}/entries/${encodePath(key)}",
            method = "PATCH",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun deleteEntry(cache: String, key: String, query: Map<String, Any?>? = null, headers: Map<String, String>? = null) {
        client.send(
            "/api/cache/${encodePath(cache)}/entries/${encodePath(key)}",
            method = "DELETE",
            query = query,
            headers = headers,
        )
    }
}
