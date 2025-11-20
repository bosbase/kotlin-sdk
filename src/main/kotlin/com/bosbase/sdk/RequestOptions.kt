package com.bosbase.sdk

/**
 * Options passed to the beforeSend hook so callers can mutate outgoing requests.
 */
data class RequestOptions(
    var method: String = "GET",
    var headers: MutableMap<String, String> = mutableMapOf(),
    var query: MutableMap<String, Any?> = mutableMapOf(),
    var body: Any? = null,
    var files: Map<String, List<FileAttachment>>? = null,
    var timeoutSeconds: Long? = null,
    var requestKey: String? = null,
    var autoCancel: Boolean = true,
)
