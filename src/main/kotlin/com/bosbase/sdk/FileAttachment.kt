package com.bosbase.sdk

import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.nio.file.Files

/**
 * Simple wrapper for multipart file uploads.
 */
data class FileAttachment(
    val filename: String,
    val bytes: ByteArray,
    val contentType: String = "application/octet-stream",
) {
    val mediaType = contentType.toMediaType()

    companion object {
        fun fromFile(file: File, contentType: String? = null): FileAttachment {
            val detected = contentType ?: Files.probeContentType(file.toPath()) ?: "application/octet-stream"
            return FileAttachment(file.name, file.readBytes(), detected)
        }
    }
}
