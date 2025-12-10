package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonObject

class ScriptPermissionsService(private val clientRef: BosBase) : BaseService(clientRef) {
    private val basePath = "/api/script-permissions"

    fun create(
        scriptName: String,
        content: String,
        scriptId: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()

        val name = scriptName.trim()
        val permission = content.trim()
        if (name.isEmpty()) throw IllegalArgumentException("scriptName is required")
        if (permission.isEmpty()) throw IllegalArgumentException("content is required")

        val payload = mutableMapOf<String, Any?>(
            "script_name" to name,
            "content" to permission,
        )
        if (!scriptId.isNullOrBlank()) {
            payload["script_id"] = scriptId.trim()
        }

        return clientRef.send(basePath, method = "POST", body = payload, query = query, headers = headers) as? JsonObject
    }

    fun get(
        scriptName: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()
        val name = scriptName.trim()
        if (name.isEmpty()) throw IllegalArgumentException("scriptName is required")
        return clientRef.send("$basePath/${encodePath(name)}", query = query, headers = headers) as? JsonObject
    }

    fun update(
        scriptName: String,
        content: String? = null,
        scriptId: String? = null,
        newScriptName: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        requireSuperuser()
        val name = scriptName.trim()
        if (name.isEmpty()) throw IllegalArgumentException("scriptName is required")

        val payload = mutableMapOf<String, Any?>()
        if (!scriptId.isNullOrBlank()) payload["script_id"] = scriptId.trim()
        if (!newScriptName.isNullOrBlank()) payload["script_name"] = newScriptName.trim()
        if (content != null) payload["content"] = content.trim()

        return clientRef.send(
            "$basePath/${encodePath(name)}",
            method = "PATCH",
            body = payload,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun delete(
        scriptName: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): Boolean {
        requireSuperuser()
        val name = scriptName.trim()
        if (name.isEmpty()) throw IllegalArgumentException("scriptName is required")
        clientRef.send("$basePath/${encodePath(name)}", method = "DELETE", query = query, headers = headers)
        return true
    }

    private fun requireSuperuser() {
        if (!clientRef.authStore.isSuperuser) {
            throw IllegalStateException("Superuser authentication is required to manage script permissions")
        }
    }
}
