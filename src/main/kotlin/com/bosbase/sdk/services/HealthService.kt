package com.bosbase.sdk.services

import kotlinx.serialization.json.JsonObject

class HealthService(client: com.bosbase.sdk.BosBase) : BaseService(client) {
    fun check(headers: Map<String, String>? = null): JsonObject? {
        val data = client.send("/api/health", method = "GET", headers = headers)
        return data as? JsonObject
    }
}
