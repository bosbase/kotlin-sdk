package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.JsonObject

class LangChaingoService(client: BosBase) : BaseService(client) {
    private val basePath = "/api/langchaingo"

    fun completions(
        payload: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("$basePath/completions", method = "POST", body = payload, query = query, headers = headers) as? JsonObject

    fun rag(
        payload: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("$basePath/rag", method = "POST", body = payload, query = query, headers = headers) as? JsonObject

    fun queryDocuments(
        payload: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("$basePath/documents/query", method = "POST", body = payload, query = query, headers = headers) as? JsonObject

    fun sql(
        payload: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("$basePath/sql", method = "POST", body = payload, query = query, headers = headers) as? JsonObject
}
