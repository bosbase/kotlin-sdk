package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class ScriptService(private val clientRef: BosBase) : BaseService(clientRef) {
    private val basePath = "/api/scripts"

    fun create(
        name: String,
        content: String,
        description: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmedName = name.trim()
        val trimmedContent = content.trim()
        if (trimmedName.isEmpty()) throw IllegalArgumentException("script name is required")
        if (trimmedContent.isEmpty()) throw IllegalArgumentException("script content is required")

        val payload = mutableMapOf<String, Any?>(
            "name" to trimmedName,
            "content" to trimmedContent,
        )
        if (!description.isNullOrBlank()) {
            payload["description"] = description
        }

        return clientRef.send(basePath, method = "POST", body = payload, query = query, headers = headers) as? JsonObject
    }

    fun command(
        command: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmed = command.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("command is required")

        return clientRef.send("$basePath/command", method = "POST", body = mapOf("command" to trimmed), query = query, headers = headers) as? JsonObject
    }

    fun get(
        name: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        return clientRef.send("$basePath/${encodePath(trimmed)}", query = query, headers = headers) as? JsonObject
    }

    fun list(
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): List<JsonObject> {
        requireSuperuser()
        val data = clientRef.send(basePath, query = query, headers = headers)
        return (data as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    fun update(
        name: String,
        content: String? = null,
        description: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) throw IllegalArgumentException("script name is required")
        if (content == null && description == null) {
            throw IllegalArgumentException("at least one of content or description must be provided")
        }

        val payload = mutableMapOf<String, Any?>()
        content?.let { payload["content"] = it }
        description?.let { payload["description"] = it }

        return clientRef.send(
            "$basePath/${encodePath(trimmedName)}",
            method = "PATCH",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun execute(
        name: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")

        return clientRef.send(
            "$basePath/${encodePath(trimmed)}/execute",
            method = "POST",
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun delete(
        name: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): Boolean {
        requireSuperuser()

        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("script name is required")
        clientRef.send("$basePath/${encodePath(trimmed)}", method = "DELETE", query = query, headers = headers)
        return true
    }

    private fun requireSuperuser() {
        if (!clientRef.authStore.isSuperuser) {
            throw IllegalStateException("Superuser authentication is required to manage scripts")
        }
    }
}
