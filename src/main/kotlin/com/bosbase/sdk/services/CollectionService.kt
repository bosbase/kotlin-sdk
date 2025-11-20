package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CollectionService(client: BosBase) : BaseCrudService(client) {
    override val baseCrudPath: String = "/api/collections"

    fun getScaffolds(headers: Map<String, String>? = null): JsonObject {
        val data = client.send("$baseCrudPath/meta/scaffolds", headers = headers)
        return (data as? JsonObject) ?: JsonObject(emptyMap())
    }

    fun createFromScaffold(
        type: String,
        name: String,
        overrides: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val scaffolds = getScaffolds(headers)
        val scaffold = scaffolds[type]?.jsonObject
            ?: throw IllegalArgumentException("Scaffold for type \"$type\" not found")

        val merged = mutableMapOf<String, Any?>()
        scaffold.forEach { (k, v) -> merged[k] = v }
        merged["name"] = name
        if (overrides != null) {
            deepMerge(merged, overrides)
        }

        return create(merged, headers = headers)
    }

    fun createBase(
        name: String,
        overrides: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject = createFromScaffold("base", name, overrides, headers)

    fun createAuth(
        name: String,
        overrides: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject = createFromScaffold("auth", name, overrides, headers)

    fun createView(
        name: String,
        viewQuery: String? = null,
        overrides: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val scaffoldOverrides = mutableMapOf<String, Any?>()
        if (overrides != null) scaffoldOverrides.putAll(overrides)
        if (viewQuery != null) scaffoldOverrides["viewQuery"] = viewQuery
        return createFromScaffold("view", name, scaffoldOverrides, headers)
    }

    fun truncate(
        collectionIdOrName: String,
        headers: Map<String, String>? = null,
    ) {
        val encoded =
            URLEncoder.encode(collectionIdOrName, StandardCharsets.UTF_8.toString())
        client.send("$baseCrudPath/$encoded/truncate", method = "DELETE", headers = headers)
    }

    fun deleteCollection(collectionIdOrName: String, headers: Map<String, String>? = null): Boolean {
        delete(collectionIdOrName, headers = headers)
        return true
    }

    fun exportCollections(
        filterCollections: ((JsonObject) -> Boolean)? = null,
        headers: Map<String, String>? = null,
    ): List<JsonObject> {
        val collections = getFullList(headers = headers)
        val filtered = filterCollections?.let { fn -> collections.filter(fn) } ?: collections
        return filtered.map { coll ->
            val map = coll.toMutableAnyMap()
            map.remove("created")
            map.remove("updated")
            val oauth = coll["oauth2"] as? JsonObject
            if (oauth != null) {
                val oauthMap = oauth.toMutableAnyMap()
                oauthMap.remove("providers")
                map["oauth2"] = oauthMap
            }
            (toJsonElement(map) as? JsonObject) ?: coll
        }
    }

    fun normalizeForImport(collections: List<JsonObject>): List<JsonObject> {
        val seenIds = mutableSetOf<String>()
        return collections
            .filter { coll ->
                val id = coll["id"]?.jsonPrimitive?.contentOrNull
                if (id != null && seenIds.contains(id)) {
                    false
                } else {
                    if (id != null) seenIds.add(id)
                    true
                }
            }
            .map { normalizeCollection(it) }
    }

    fun import(collections: List<JsonObject>, deleteMissing: Boolean = false, headers: Map<String, String>? = null): Boolean {
        client.send(
            "$baseCrudPath/import",
            method = "PUT",
            body = mapOf(
                "collections" to collections,
                "deleteMissing" to deleteMissing,
            ),
            headers = headers,
        )
        return true
    }

    fun addField(
        collectionIdOrName: String,
        field: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val name = field["name"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Field name is required")
        val type = field["type"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Field type is required")

        val collection = getOne(collectionIdOrName, headers = headers)
        val fields = (collection["fields"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.toMutableList()
            ?: mutableListOf()
        if (fields.any { it["name"]?.jsonPrimitive?.contentOrNull == name }) {
            throw IllegalArgumentException("Field with name \"$name\" already exists")
        }

        val newField = toJsonElement(
            mutableMapOf<String, Any?>(
                "id" to (field["id"] ?: ""),
                "name" to name,
                "type" to type,
                "system" to (field["system"] ?: false),
                "hidden" to (field["hidden"] ?: false),
                "presentable" to (field["presentable"] ?: false),
                "required" to (field["required"] ?: false),
            ).apply { putAll(field) },
        ) as? JsonObject ?: JsonObject(emptyMap())
        fields.add(newField)

        val updated = collection.toMutableAnyMap()
        updated["fields"] = fields
        return update(collectionIdOrName, updated, headers = headers)
    }

    fun updateField(
        collectionIdOrName: String,
        fieldName: String,
        updates: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val collection = getOne(collectionIdOrName, headers = headers)
        val fields = (collection["fields"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.toMutableList()
            ?: throw IllegalArgumentException("Fields list is missing")

        val idx = fields.indexOfFirst { it["name"]?.jsonPrimitive?.contentOrNull == fieldName }
        if (idx == -1) throw IllegalArgumentException("Field with name \"$fieldName\" not found")

        val current = fields[idx]
        if (current["system"]?.jsonPrimitive?.booleanOrNull == true && (updates["type"] != null || updates["name"] != null)) {
            throw IllegalArgumentException("Cannot modify system fields")
        }
        val newName = updates["name"]?.toString()
        if (!newName.isNullOrBlank() && newName != fieldName) {
            if (fields.any { it["name"]?.jsonPrimitive?.contentOrNull == newName }) {
                throw IllegalArgumentException("Field with name \"$newName\" already exists")
            }
        }

        val merged = current.toMutableAnyMap()
        merged.putAll(updates)
        fields[idx] = (toJsonElement(merged) as? JsonObject) ?: current

        val updated = collection.toMutableAnyMap()
        updated["fields"] = fields
        return update(collectionIdOrName, updated, headers = headers)
    }

    fun removeField(
        collectionIdOrName: String,
        fieldName: String,
        headers: Map<String, String>? = null,
    ): JsonObject {
        val collection = getOne(collectionIdOrName, headers = headers)
        val fields = (collection["fields"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.toMutableList()
            ?: mutableListOf()

        val idx = fields.indexOfFirst { it["name"]?.jsonPrimitive?.contentOrNull == fieldName }
        if (idx == -1) throw IllegalArgumentException("Field with name \"$fieldName\" not found")
        if (fields[idx]["system"]?.jsonPrimitive?.booleanOrNull == true) {
            throw IllegalArgumentException("Cannot remove system fields")
        }
        fields.removeAt(idx)

        val indexes = (collection["indexes"] as? JsonArray)
            ?.mapNotNull { it as? JsonPrimitive }
            ?.mapNotNull { it.contentOrNull }
            ?.filterNot { it.contains("($fieldName)") || it.contains("($fieldName,") || it.contains(", $fieldName)") }
            ?: emptyList()

        val updated = collection.toMutableAnyMap()
        updated["fields"] = fields
        updated["indexes"] = indexes
        return update(collectionIdOrName, updated, headers = headers)
    }

    fun getField(
        collectionIdOrName: String,
        fieldName: String,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val collection = getOne(collectionIdOrName, headers = headers)
        val fields = (collection["fields"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        return fields.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == fieldName }
    }

    fun addIndex(
        collectionIdOrName: String,
        columns: List<String>,
        unique: Boolean = false,
        indexName: String? = null,
        headers: Map<String, String>? = null,
    ): JsonObject {
        if (columns.isEmpty()) {
            throw IllegalArgumentException("At least one column must be specified")
        }

        val collection = getOne(collectionIdOrName, headers = headers)
        val fieldNames = (collection["fields"] as? JsonArray)
            ?.mapNotNull { field -> (field as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
            ?: emptyList()

        columns.forEach { column ->
            if (column != "id" && !fieldNames.contains(column)) {
                throw IllegalArgumentException("Field \"$column\" does not exist in the collection")
            }
        }

        val collectionName = collection["name"]?.jsonPrimitive?.contentOrNull ?: collectionIdOrName
        val idxName = indexName ?: "idx_${collectionName}_${columns.joinToString("_")}"
        val columnsStr = columns.joinToString(", ") { "`$it`" }
        val definition = if (unique) {
            "CREATE UNIQUE INDEX `$idxName` ON `$collectionName` ($columnsStr)"
        } else {
            "CREATE INDEX `$idxName` ON `$collectionName` ($columnsStr)"
        }

        val indexes = collection.toIndexList()
        if (indexes.contains(definition)) {
            throw IllegalArgumentException("Index already exists")
        }
        indexes.add(definition)

        val updated = collection.toMutableAnyMap()
        updated["indexes"] = indexes
        return update(collectionIdOrName, updated, headers = headers)
    }

    fun removeIndex(
        collectionIdOrName: String,
        columns: List<String>,
        headers: Map<String, String>? = null,
    ): JsonObject {
        if (columns.isEmpty()) {
            throw IllegalArgumentException("At least one column must be specified")
        }

        val collection = getOne(collectionIdOrName, headers = headers)
        val indexes = collection.toIndexList()
        val initialSize = indexes.size

        indexes.removeAll { idx ->
            columns.all { column ->
                val backticked = "`$column`"
                idx.contains(backticked) ||
                    idx.contains("($column)") ||
                    idx.contains("($column,") ||
                    idx.contains(", $column)")
            }
        }

        if (indexes.size == initialSize) {
            throw IllegalArgumentException("Index not found")
        }

        val updated = collection.toMutableAnyMap()
        updated["indexes"] = indexes
        return update(collectionIdOrName, updated, headers = headers)
    }

    fun getIndexes(
        collectionIdOrName: String,
        headers: Map<String, String>? = null,
    ): List<String> {
        val collection = getOne(collectionIdOrName, headers = headers)
        return collection.toIndexList()
    }

    fun getSchema(
        collectionIdOrName: String,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val encoded = URLEncoder.encode(collectionIdOrName, StandardCharsets.UTF_8.toString())
        return client.send("$baseCrudPath/$encoded/schema", headers = headers) as? JsonObject
    }

    fun getAllSchemas(headers: Map<String, String>? = null): JsonObject? {
        return client.send("$baseCrudPath/schemas", headers = headers) as? JsonObject
    }

    private fun deepMerge(target: MutableMap<String, Any?>, source: Map<String, Any?>) {
        for ((key, value) in source) {
            val current = target[key]
            if (current is Map<*, *> && value is Map<*, *>) {
                val mergedChild = mutableMapOf<String, Any?>()
                current.forEach { (k, v) -> if (k != null) mergedChild[k.toString()] = v }
                deepMerge(mergedChild, value.filterKeys { it != null }.mapKeys { it.key.toString() })
                target[key] = mergedChild
            } else {
                target[key] = value
            }
        }
    }

    private fun normalizeCollection(collection: JsonObject): JsonObject {
        val map = collection.toMutableAnyMap()
        map.remove("created")
        map.remove("updated")

        val fields = (collection["fields"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        val seenFieldIds = mutableSetOf<String>()
        val dedupedFields = fields.filter { field ->
            val id = field["id"]?.jsonPrimitive?.contentOrNull
            if (id != null && seenFieldIds.contains(id)) {
                false
            } else {
                if (id != null) seenFieldIds.add(id)
                true
            }
        }
        map["fields"] = dedupedFields

        return (toJsonElement(map) as? JsonObject) ?: collection
    }

    private fun JsonObject.toMutableAnyMap(): MutableMap<String, Any?> =
        this.entries.associate { it.key to it.value }.toMutableMap()

    private fun JsonObject.toIndexList(): MutableList<String> =
        (this["indexes"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toMutableList()
            ?: mutableListOf()
}
