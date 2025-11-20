package com.bosbase.sdk

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class ResultList<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val items: List<T>,
    val raw: JsonObject? = null,
)
