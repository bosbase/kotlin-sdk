package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class VectorService(client: BosBase) : BaseService(client) {
    private val basePath = "/api/vectors"

    private fun collectionPath(collection: String): String {
        if (collection.isBlank()) {
            throw IllegalArgumentException("collection must be provided for vector document operations")
        }
        return "$basePath/${encodePath(collection)}"
    }

    fun insert(
        document: Map<String, Any?>,
        collection: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send(collectionPath(collection), method = "POST", body = document, query = query, headers = headers) as? JsonObject

    fun batchInsert(
        options: Map<String, Any?>,
        collection: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("${collectionPath(collection)}/documents/batch", method = "POST", body = options, query = query, headers = headers) as? JsonObject

    fun update(
        documentId: String,
        document: Map<String, Any?>,
        collection: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("${collectionPath(collection)}/${encodePath(documentId)}", method = "PATCH", body = document, query = query, headers = headers) as? JsonObject

    fun delete(
        documentId: String,
        collection: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send("${collectionPath(collection)}/${encodePath(documentId)}", method = "DELETE", body = body, query = query, headers = headers)
    }

    fun search(
        options: Map<String, Any?>,
        collection: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("${collectionPath(collection)}/documents/search", method = "POST", body = options, query = query, headers = headers) as? JsonObject

    fun get(
        documentId: String,
        collection: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? =
        client.send("${collectionPath(collection)}/${encodePath(documentId)}", query = query, headers = headers) as? JsonObject

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

    fun createCollection(
        name: String,
        config: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send("$basePath/collections/${encodePath(name)}", method = "POST", body = config, query = query, headers = headers)
    }

    fun updateCollection(
        name: String,
        config: Map<String, Any?>,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send("$basePath/collections/${encodePath(name)}", method = "PATCH", body = config, query = query, headers = headers)
    }

    fun deleteCollection(
        name: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send("$basePath/collections/${encodePath(name)}", method = "DELETE", body = body, query = query, headers = headers)
    }

    fun listCollections(query: Map<String, Any?>? = null, headers: Map<String, String>? = null): List<JsonObject> {
        val data = client.send("$basePath/collections", query = query, headers = headers)
        return (data as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }
}
