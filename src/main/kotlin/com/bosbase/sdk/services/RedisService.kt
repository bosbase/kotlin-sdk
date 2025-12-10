package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonObject

class RedisService(client: BosBase) : BaseService(client) {
    private val basePath = "/api/redis/keys"

    fun listKeys(
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send(basePath, query = query, headers = headers) as? JsonObject

    fun createKey(
        key: String,
        value: Any?,
        ttlSeconds: Int? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        if (key.isBlank()) throw IllegalArgumentException("key is required")
        val payload = mutableMapOf<String, Any?>(
            "key" to key,
            "value" to value,
        )
        if (ttlSeconds != null) {
            payload["ttlSeconds"] = ttlSeconds
        }
        if (body != null) {
            payload.putAll(body)
        }

        return client.send(basePath, method = "POST", body = payload, query = query, headers = headers) as? JsonObject
    }

    fun getKey(
        key: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        if (key.isBlank()) throw IllegalArgumentException("key is required")
        return client.send("$basePath/${encodePath(key)}", query = query, headers = headers) as? JsonObject
    }

    fun updateKey(
        key: String,
        value: Any?,
        ttlSeconds: Int? = null,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        if (key.isBlank()) throw IllegalArgumentException("key is required")
        val payload = mutableMapOf<String, Any?>("value" to value)
        if (ttlSeconds != null) {
            payload["ttlSeconds"] = ttlSeconds
        }
        if (body != null) {
            payload.putAll(body)
        }

        return client.send(
            "$basePath/${encodePath(key)}",
            method = "PUT",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun deleteKey(
        key: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): Boolean {
        if (key.isBlank()) throw IllegalArgumentException("key is required")
        client.send("$basePath/${encodePath(key)}", method = "DELETE", query = query, headers = headers)
        return true
    }
}
