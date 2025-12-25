package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class FileService(client: BosBase) : BaseService(client) {

    fun getUrl(
        record: JsonObject,
        filename: String,
        query: Map<String, Any?>? = null,
    ): String {
        return getURL(record, filename, query = query)
    }

    fun getURL(
        record: JsonObject,
        filename: String,
        thumb: String? = null,
        token: String? = null,
        download: Boolean? = null,
        query: Map<String, Any?>? = null,
    ): String {
        if (filename.isBlank()) return ""
        val recordId = record["id"]?.jsonPrimitive?.contentOrNull ?: return ""
        val collection =
            record["collectionId"]?.jsonPrimitive?.contentOrNull
                ?: record["collectionName"]?.jsonPrimitive?.contentOrNull
                ?: return ""

        val parts = listOf(
            "api",
            "files",
            urlEncode(collection),
            urlEncode(recordId),
            urlEncode(filename),
        )

        val params = mutableMapOf<String, Any?>()
        if (thumb != null) params["thumb"] = thumb
        if (token != null) params["token"] = token
        if (download == true) params["download"] = "1"
        if (query != null) params.putAll(query)

        val url = client.buildUrl(parts.joinToString("/"), params)
        return url.toString()
    }

    fun getToken(headers: Map<String, String>? = null): String {
        val data = client.send("/api/files/token", method = "POST", headers = headers)
        return (data as? JsonObject)?.get("token")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
