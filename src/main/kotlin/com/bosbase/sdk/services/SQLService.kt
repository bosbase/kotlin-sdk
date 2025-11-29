package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class SQLExecuteResponse(
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val rowsAffected: Long? = null,
)

/**
 * Superuser-only SQL execution helpers.
 */
class SQLService(client: BosBase) : BaseService(client) {
    /**
     * Execute a SQL statement and return the result.
     *
     * @throws IllegalArgumentException if the query is blank.
     * @throws com.bosbase.sdk.ClientResponseError on HTTP or server errors.
     */
    fun execute(
        query: String,
        queryParams: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): SQLExecuteResponse {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("query is required")
        }

        val payload = mapOf("query" to trimmed)
        val response = client.send(
            "/api/sql/execute",
            method = "POST",
            body = payload,
            query = queryParams,
            headers = headers,
        )

        return parseResponse(response)
    }

    private fun parseResponse(data: Any?): SQLExecuteResponse {
        val obj = data as? JsonObject ?: return SQLExecuteResponse()

        val columns = obj["columns"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val rows = obj["rows"]
            ?.jsonArray
            ?.mapNotNull { row ->
                (row as? JsonArray)
                    ?.map { it.jsonPrimitive.contentOrNull ?: "" }
            }
            ?: emptyList()

        val rowsAffected = obj["rowsAffected"]
            ?.jsonPrimitive
            ?.longOrNull

        return SQLExecuteResponse(
            columns = columns,
            rows = rows,
            rowsAffected = rowsAffected,
        )
    }
}
