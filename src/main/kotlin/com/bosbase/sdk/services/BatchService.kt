package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.FileAttachment
import com.bosbase.sdk.encodePath

class BatchService(client: BosBase) : BaseService(client) {
    private val requests = mutableListOf<QueuedRequest>()
    private val collections = mutableMapOf<String, SubBatchService>()

    fun collection(collectionIdOrName: String): SubBatchService =
        collections.computeIfAbsent(collectionIdOrName) { SubBatchService(this, it) }

    fun queueRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: Map<String, Any?>? = null,
        files: Map<String, List<FileAttachment>>? = null,
    ) {
        val (jsonBody, attachments) = splitFiles(body)
        files?.forEach { (key, list) ->
            val target = attachments.getOrPut(key) { mutableListOf() }
            target.addAll(list)
        }
        requests.add(
            QueuedRequest(
                method = method,
                url = url,
                headers = headers.toMap(),
                body = jsonBody,
                files = attachments.mapValues { it.value.toList() },
            ),
        )
    }

    fun send(
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ): List<Any?> {
        val payload = mutableMapOf<String, Any?>()
        if (body != null) payload.putAll(body)

        val requestPayload = requests.map { req ->
            mapOf(
                "method" to req.method,
                "url" to req.url,
                "headers" to req.headers,
                "body" to req.body,
            )
        }
        payload["requests"] = requestPayload

        val flatFiles = mutableMapOf<String, MutableList<FileAttachment>>()
        requests.forEachIndexed { index, req ->
            req.files.forEach { (field, attachmentList) ->
                attachmentList.forEachIndexed { idx, attachment ->
                    val fieldKey = if (attachmentList.size > 1) "$field[$idx]" else field
                    flatFiles.getOrPut("requests.$index.$fieldKey") { mutableListOf() }.add(attachment)
                }
            }
        }

        val response = client.send(
            "/api/batch",
            method = "POST",
            body = payload,
            query = query,
            headers = headers,
            files = if (flatFiles.isEmpty()) null else flatFiles.mapValues { it.value.toList() },
        )

        requests.clear()
        return (response as? kotlinx.serialization.json.JsonArray)?.toList() ?: emptyList()
    }

    internal fun buildRelative(path: String, query: Map<String, Any?>?): String {
        val url = client.buildUrl(path, query)
        val queryPart = url.encodedQuery?.let { "?$it" } ?: ""
        return url.encodedPath + queryPart
    }
}

private data class QueuedRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: Map<String, Any?>,
    val files: Map<String, List<FileAttachment>>,
)

class SubBatchService(
    private val batch: BatchService,
    private val collectionIdOrName: String,
) {
    private fun collectionUrl(): String = "/api/collections/${encodePath(collectionIdOrName)}/records"

    fun create(
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        files: Map<String, List<FileAttachment>>? = null,
        headers: Map<String, String>? = null,
        expand: String? = null,
        fields: String? = null,
    ) {
        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)
        batch.queueRequest("POST", buildUrl(collectionUrl(), params), headers ?: emptyMap(), body, files)
    }

    fun upsert(
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        files: Map<String, List<FileAttachment>>? = null,
        headers: Map<String, String>? = null,
        expand: String? = null,
        fields: String? = null,
    ) {
        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)
        batch.queueRequest("PUT", buildUrl(collectionUrl(), params), headers ?: emptyMap(), body, files)
    }

    fun update(
        recordId: String,
        body: Map<String, Any?>? = null,
        query: Map<String, Any?>? = null,
        files: Map<String, List<FileAttachment>>? = null,
        headers: Map<String, String>? = null,
        expand: String? = null,
        fields: String? = null,
    ) {
        val params = mutableMapOf<String, Any?>()
        if (expand != null) params["expand"] = expand
        if (fields != null) params["fields"] = fields
        if (query != null) params.putAll(query)
        val url = "${collectionUrl()}/${encodePath(recordId)}"
        batch.queueRequest("PATCH", buildUrl(url, params), headers ?: emptyMap(), body, files)
    }

    fun delete(
        recordId: String,
        query: Map<String, Any?>? = null,
        headers: Map<String, String>? = null,
    ) {
        val url = "${collectionUrl()}/${encodePath(recordId)}"
        batch.queueRequest("DELETE", buildUrl(url, query), headers ?: emptyMap(), null, null)
    }

    private fun buildUrl(path: String, query: Map<String, Any?>?): String =
        batch.buildRelative(path, query)

}

private fun splitFiles(body: Map<String, Any?>?): Pair<Map<String, Any?>, MutableMap<String, MutableList<FileAttachment>>> {
    val jsonBody = mutableMapOf<String, Any?>()
    val files = mutableMapOf<String, MutableList<FileAttachment>>()
    if (body == null) return jsonBody to files

    fun addAttachment(key: String, attachment: FileAttachment) {
        files.getOrPut(key) { mutableListOf() }.add(attachment)
    }

    body.forEach { (key, value) ->
        when (value) {
            is FileAttachment -> addAttachment(key, value)
            is Iterable<*> -> {
                val items = value.toList()
                val attachments = items.mapNotNull { it as? FileAttachment }
                val regular = items.filterNot { it is FileAttachment }
                if (attachments.size == items.size && attachments.isNotEmpty()) {
                    attachments.forEach { addAttachment(key, it) }
                } else {
                    jsonBody[key] = regular
                    if (attachments.isNotEmpty()) {
                        val targetKey = if (key.startsWith("+") || key.endsWith("+")) key else "$key+"
                        attachments.forEach { addAttachment(targetKey, it) }
                    }
                }
            }
            is Array<*> -> {
                val asList = value.filterNotNull()
                val attachments = asList.mapNotNull { it as? FileAttachment }
                val regular = asList.filterNot { it is FileAttachment }
                if (attachments.size == asList.size && attachments.isNotEmpty()) {
                    attachments.forEach { addAttachment(key, it) }
                } else {
                    jsonBody[key] = regular
                    if (attachments.isNotEmpty()) {
                        val targetKey = if (key.startsWith("+") || key.endsWith("+")) key else "$key+"
                        attachments.forEach { addAttachment(targetKey, it) }
                    }
                }
            }
            null -> jsonBody[key] = null
            else -> jsonBody[key] = value
        }
    }

    return jsonBody to files
}
