package com.bosbase.sdk.services

import com.bosbase.sdk.BosBase
import com.bosbase.sdk.toJsonElement
import kotlinx.serialization.json.JsonObject

class SettingsService(client: BosBase) : BaseService(client) {
    fun getAll(headers: Map<String, String>? = null): JsonObject? {
        return client.send("/api/settings", method = "GET", headers = headers) as? JsonObject
    }

    fun getCategory(category: String, headers: Map<String, String>? = null): JsonObject? {
        val all = getAll(headers) ?: return null
        return all[category] as? JsonObject
    }

    fun update(
        body: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send("/api/settings", method = "PATCH", body = body, headers = headers) as? JsonObject
    }

    fun testS3(
        filesystem: String = "storage",
        headers: Map<String, String>? = null,
    ): Boolean {
        client.send(
            "/api/settings/test/s3",
            method = "POST",
            body = mapOf("filesystem" to filesystem),
            headers = headers,
        )
        return true
    }

    fun testEmail(
        email: String,
        template: String,
        collectionIdOrName: String? = null,
        headers: Map<String, String>? = null,
    ): Boolean {
        val payload = mutableMapOf<String, Any?>(
            "email" to email,
            "template" to template,
        )
        if (!collectionIdOrName.isNullOrBlank()) {
            payload["collection"] = collectionIdOrName
        }
        client.send(
            "/api/settings/test/email",
            method = "POST",
            body = payload,
            headers = headers,
        )
        return true
    }

    fun generateAppleClientSecret(
        clientId: String,
        teamId: String,
        keyId: String,
        privateKey: String,
        duration: Int,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        return client.send(
            "/api/settings/apple/generate-client-secret",
            method = "POST",
            body = mapOf(
                "clientId" to clientId,
                "teamId" to teamId,
                "keyId" to keyId,
                "privateKey" to privateKey,
                "duration" to duration,
            ),
            headers = headers,
        ) as? JsonObject
    }

    fun updateMeta(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("meta" to config), headers)

    fun updateTrustedProxy(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("trustedProxy" to config), headers)

    fun updateRateLimits(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("rateLimits" to config), headers)

    fun updateBatchSettings(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("batch" to config), headers)

    fun updateSMTP(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("smtp" to config), headers)

    fun getApplicationSettings(headers: Map<String, String>? = null): JsonObject? {
        val all = getAll(headers) ?: return null
        val result = mutableMapOf<String, Any?>()
        result["meta"] = all["meta"]
        result["trustedProxy"] = all["trustedProxy"]
        result["rateLimits"] = all["rateLimits"]
        result["batch"] = all["batch"]
        return toJsonElement(result) as? JsonObject
    }

    fun updateApplicationSettings(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(config, headers)

    fun getMailSettings(headers: Map<String, String>? = null): JsonObject? {
        val all = getAll(headers) ?: return null
        val result = mutableMapOf<String, Any?>()
        result["meta"] = all["meta"]
        result["smtp"] = all["smtp"]
        return toJsonElement(result) as? JsonObject
    }

    fun updateMailSettings(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(config, headers)

    fun updateS3(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("s3" to config), headers)

    fun getStorageS3(headers: Map<String, String>? = null): JsonObject? = getCategory("s3", headers)

    fun updateStorageS3(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateS3(config, headers)

    fun testStorageS3(headers: Map<String, String>? = null): Boolean = testS3("storage", headers)

    fun updateBackups(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("backups" to config), headers)

    fun getBackupSettings(headers: Map<String, String>? = null): JsonObject? = getCategory("backups", headers)

    fun updateBackupSettings(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateBackups(config, headers)

    fun setAutoBackupSchedule(
        cron: String,
        cronMaxKeep: Int? = null,
        headers: Map<String, String>? = null,
    ): JsonObject? {
        val cfg = mutableMapOf<String, Any?>("cron" to cron)
        if (cronMaxKeep != null) cfg["cronMaxKeep"] = cronMaxKeep
        return updateBackups(cfg, headers)
    }

    fun disableAutoBackup(headers: Map<String, String>? = null): JsonObject? =
        updateBackups(mapOf("cron" to ""), headers)

    fun testBackupsS3(headers: Map<String, String>? = null): Boolean = testS3("backups", headers)

    fun updateBatch(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("batch" to config), headers)

    fun updateLogs(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = update(mapOf("logs" to config), headers)

    fun getLogSettings(headers: Map<String, String>? = null): JsonObject? = getCategory("logs", headers)

    fun updateLogSettings(
        config: Map<String, Any?>,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateLogs(config, headers)

    fun setLogRetentionDays(
        maxDays: Int,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateLogs(mapOf("maxDays" to maxDays), headers)

    fun setMinLogLevel(
        minLevel: Int,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateLogs(mapOf("minLevel" to minLevel), headers)

    fun setLogIPAddresses(
        enabled: Boolean,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateLogs(mapOf("logIP" to enabled), headers)

    fun setLogAuthIds(
        enabled: Boolean,
        headers: Map<String, String>? = null,
    ): JsonObject? = updateLogs(mapOf("logAuthId" to enabled), headers)
}
