package com.bosbase.sdk

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

internal fun toJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) ->
                if (k != null) {
                    put(k.toString(), toJsonElement(v))
                }
            }
        }
        is Iterable<*> -> buildJsonArray {
            value.forEach { add(toJsonElement(it)) }
        }
        is Array<*> -> buildJsonArray {
            value.forEach { add(toJsonElement(it)) }
        }
        is ByteArray -> JsonPrimitive(Base64.getEncoder().encodeToString(value))
        else -> JsonPrimitive(value.toString())
    }
}

internal fun jsonElementToMap(element: JsonElement?): Map<String, Any?> {
    if (element == null || element is JsonNull) return emptyMap()
    return when (element) {
        is JsonObject -> element.mapValues { (_, v) -> jsonElementToNative(v) }
        else -> emptyMap()
    }
}

private fun jsonElementToNative(element: JsonElement): Any? {
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            val bool = element.booleanOrNull
            if (bool != null) return bool

            val long = element.longOrNull
            if (long != null) return long

            val double = element.doubleOrNull
            if (double != null) return double

            element.content
        }
        is JsonArray -> element.map { jsonElementToNative(it) }
        is JsonObject -> element.mapValues { (_, v) -> jsonElementToNative(v) }
        else -> element.toString()
    }
}
