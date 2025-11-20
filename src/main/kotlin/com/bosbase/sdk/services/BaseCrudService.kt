package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.FileAttachment
import com.bosbase.sdk.ResultList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

abstract class BaseCrudService(client: BosBase) : BaseService(client) {
    protected abstract val baseCrudPath: String

    open fun getList(
        page: Int = 1,
        perPage: Int = 30,
        skipTotal: Boolean = false,
        filter: String? = null,
        sort: String? = null,
        expand: String? = null,
        fields: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): ResultList<JsonObject> {
        val params = mutableMapOf<String, Any?>(
            "page" to page,
            "perPage" to perPage,
        )
        params["skipTotal"] = skipTotal
        if (filter != null) params["filter"] = filter
        if (sort != null) params["sort"] = sort
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)

        val data = client.send(baseCrudPath, query = params, headers = headers)
            ?: return ResultList(page, perPage, 0, emptyList(), null)
        return data.asResultList()
    }

    open fun getFullList(
        batch: Int = 200,
        filter: String? = null,
        sort: String? = null,
        expand: String? = null,
        fields: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): List<JsonObject> {
        val items = mutableListOf<JsonObject>()
        var page = 1
        while (true) {
            val result = getList(
                page = page,
                perPage = batch,
                filter = filter,
                sort = sort,
                expand = expand,
                fields = fields,
                query = query,
                headers = headers,
            )
            items.addAll(result.items)
            if (items.size >= result.totalItems || result.items.isEmpty()) break
            page += 1
        }
        return items
    }

    open fun getOne(
        id: String,
        expand: String? = null,
        fields: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)

        val data = client.send("${baseCrudPath}/${com.bosbase.sdk.encodePath(id)}", query = params, headers = headers)
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    open fun getFirstListItem(
        filter: String,
        expand: String? = null,
        fields: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val result = getList(
            page = 1,
            perPage = 1,
            skipTotal = true,
            filter = filter,
            expand = expand,
            fields = fields,
            query = query,
            headers = headers,
        )
        return result.items.firstOrNull() ?: throw com.bosbase.sdk.ClientResponseError(
            url = client.buildUrl(baseCrudPath).toString(),
            status = 404,
            response = mapOf("code" to 404, "message" to "The requested resource wasn't found.", "data" to emptyMap<String, Any>()),
        )
    }

    open fun create(
        body: Map<String, Any?>,
        files: Map<String, List<FileAttachment>>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val data = client.send(
            baseCrudPath,
            method = "POST",
            body = body,
            files = files,
            query = query,
            headers = headers,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    open fun update(
        id: String,
        body: Map<String, Any?>,
        files: Map<String, List<FileAttachment>>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val data = client.send(
            "$baseCrudPath/$id",
            method = "PATCH",
            body = body,
            files = files,
            query = query,
            headers = headers,
        )
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    open fun delete(
        id: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send(
            "$baseCrudPath/$id",
            method = "DELETE",
            query = query,
            headers = headers,
        )
    }
}

private fun JsonElement.asResultList(): ResultList<JsonObject> {
    val obj = this as? JsonObject ?: return ResultList(1, 0, 0, emptyList(), null)
    val page = obj["page"]?.jsonPrimitive?.intOrNull ?: 1
    val perPage = obj["perPage"]?.jsonPrimitive?.intOrNull ?: 0
    val total = obj["totalItems"]?.jsonPrimitive?.intOrNull
        ?: obj["total"]?.jsonPrimitive?.intOrNull
        ?: 0
    val items = obj["items"]?.jsonArray?.mapNotNull { it as? JsonObject } ?: emptyList()
    return ResultList(page, perPage, total, items, obj)
}
