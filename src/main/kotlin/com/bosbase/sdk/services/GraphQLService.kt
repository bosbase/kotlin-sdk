package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.JsonObject

class GraphQLService(client: BosBase) : BaseService(client) {
    fun query(
        query: String,
        variables: Map<String, Any?>? = null,
        operationName: String? = null,
        queryParams: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val payload = mutableMapOf<String, Any?>(
            "query" to query,
            "variables" to (variables ?: emptyMap<String, Any?>()),
        )
        if (operationName != null) {
            payload["operationName"] = operationName
        }

        val response = client.send(
            "/api/graphql",
            method = "POST",
            headers = headers,
            query = queryParams,
            body = payload,
        )
        return response as? JsonObject
    }
}
