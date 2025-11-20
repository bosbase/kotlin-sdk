package com.bosbase.sdk

/**
 * Normalized HTTP error used by the Kotlin SDK. Mirrors the shape of the JS / Dart errors.
 */
class ClientResponseError(
    val url: String,
    val status: Int? = null,
    val response: Map<String, Any?> = emptyMap(),
    val isAbort: Boolean = false,
    val originalError: Throwable? = null,
) : RuntimeException(buildMessage(url, status, response), originalError) {

    companion object {
        private fun buildMessage(url: String, status: Int?, response: Map<String, Any?>): String {
            val statusText = status?.toString() ?: "n/a"
            val message = response["message"]?.toString() ?: "HTTP $statusText"
            return "$message ($url)"
        }
    }
}
