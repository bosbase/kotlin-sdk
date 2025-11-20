package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.encodePath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class CronService(client: BosBase) : BaseService(client) {
    fun getFullList(query: Map<String, Any?>? = null, headers: Map<String, String>? = null): List<JsonObject> {
        val data = client.send("/api/crons", query = query, headers = headers)
        return (data as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    fun run(
        jobId: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        client.send(
            "/api/crons/${encodePath(jobId)}",
            method = "POST",
            body = body,
            query = query,
            headers = headers,
        )
    }
}
