package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.JsonObject

class LogService(client: BosBase) : BaseService(client) {
    fun getList(
        page: Int = 1,
        perPage: Int = 30,
        filter: String? = null,
        sort: String? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val params = mutableMapOf<String, Any?>("page" to page, "perPage" to perPage)
        if (filter != null) params["filter"] = filter
        if (sort != null) params["sort"] = sort
        if (query != null) params.putAll(query)
        return client.send("/api/logs", query = params, headers = headers) as? JsonObject
    }

    fun getOne(
        logId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send("/api/logs/$logId", query = query, headers = headers) as? JsonObject
    }

    fun getStats(
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): List<Any?> {
        val data = client.send("/api/logs/stats", query = query, headers = headers)
        return (data as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()
    }
}
