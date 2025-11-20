package com.bosbase.sdk

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun encodePath(segment: String): String =
    URLEncoder.encode(segment, StandardCharsets.UTF_8.toString())
