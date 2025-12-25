package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class LLMDocumentService(client: BosBase) : BaseService(client) {
    private val basePath = "/api/llm-documents"

    private fun collectionPath(collection: String): String = "$basePath/${encodePath(collection)}"

    fun listCollections(query: Map<String, Any?>? = null, headers: Map<String, String>? = null): List<JsonObject> {
        val data = client.send("$basePath/collections", query = query, headers = headers)
        return (data as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    fun createCollection(
        name: String,
        metadata: Map<String, String>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send(
            "$basePath/collections/${encodePath(name)}",
            method = "POST",
            body = mapOf("metadata" to metadata),
            query = query,
            headers = headers,
        )
    }

    fun deleteCollection(name: String, query: Map<String, Any?>? = null, headers: Map<String, String>? = null) {
        client.send("$basePath/collections/${encodePath(name)}", method = "DELETE", query = query, headers = headers)
    }

    fun insert(
        collection: String,
        document: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send(collectionPath(collection), method = "POST", body = document, query = query, headers = headers) as? JsonObject
    }

    fun get(
        collection: String,
        documentId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send("${collectionPath(collection)}/${encodePath(documentId)}", query = query, headers = headers) as? JsonObject
    }

    fun getOne(
        collection: String,
        documentId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? = get(collection, documentId, query, headers)

    fun update(
        collection: String,
        documentId: String,
        document: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send(
            "${collectionPath(collection)}/${encodePath(documentId)}",
            method = "PATCH",
            body = document,
            query = query,
            headers = headers,
        ) as? JsonObject
    }

    fun delete(
        collection: String,
        documentId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send("${collectionPath(collection)}/${encodePath(documentId)}", method = "DELETE", query = query, headers = headers)
    }

    fun list(
        collection: String,
        page: Int? = null,
        perPage: Int? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val params = mutableMapOf<String, Any?>()
        if (page != null) params["page"] = page
        if (perPage != null) params["perPage"] = perPage
        if (query != null) params.putAll(query)
        return client.send(collectionPath(collection), query = params, headers = headers) as? JsonObject
    }

    fun query(
        collection: String,
        options: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send(
            "${collectionPath(collection)}/documents/query",
            method = "POST",
            body = options,
            query = query,
            headers = headers,
        ) as? JsonObject
    }
}
