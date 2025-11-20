package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonObject

class BackupService(client: BosBase) : BaseService(client) {
    fun getFullList(headers: Map<String, String>? = null): List<JsonObject> {
        val data = client.send("/api/backups", method = "GET", headers = headers)
        return (data as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    fun create(name: String, headers: Map<String, String>? = null): Boolean {
        client.send("/api/backups", method = "POST", body = mapOf("name" to name), headers = headers)
        return true
    }

    fun upload(body: Map<String, Any?>, headers: Map<String, String>? = null): Boolean {
        client.send("/api/backups/upload", method = "POST", body = body, headers = headers)
        return true
    }

    fun upload(body: Map<String, Any?>, files: Map<String, List<com.bosbase.sdk.FileAttachment>>, headers: Map<String, String>? = null): Boolean {
        client.send("/api/backups/upload", method = "POST", body = body, files = files, headers = headers)
        return true
    }

    fun delete(key: String, headers: Map<String, String>? = null): Boolean {
        client.send("/api/backups/${encodePath(key)}", method = "DELETE", headers = headers)
        return true
    }

    fun restore(key: String, headers: Map<String, String>? = null): Boolean {
        client.send("/api/backups/${encodePath(key)}/restore", method = "POST", headers = headers)
        return true
    }

    fun getDownloadURL(token: String, key: String): String {
        return client.buildUrl(
            "/api/backups/${encodePath(key)}",
            mapOf("token" to token),
        ).toString()
    }
}
