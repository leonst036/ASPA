package com.aspa.plugin.pterodactyl

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class PterodactylService(
    private val enabled: Boolean,
    url: String?,
    private val apiToken: String?,
    private val serverId: String?,
    private val logger: Logger
) {
    private val url: String? = if (url != null && url.endsWith("/")) {
        url.substring(0, url.length - 1)
    } else {
        url
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun isEnabled(): Boolean =
        enabled &&
            !url.isNullOrEmpty() &&
            !apiToken.isNullOrEmpty() &&
            !serverId.isNullOrEmpty()

    fun isConfigured(): Boolean =
        !url.isNullOrEmpty() &&
            !apiToken.isNullOrEmpty() &&
            !serverId.isNullOrEmpty()

    private fun buildRequest(path: String): HttpRequest.Builder {
        return HttpRequest.newBuilder()
            .uri(URI.create("$url/api/client/servers/$serverId$path"))
            .header("Authorization", "Bearer $apiToken")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
    }

    fun fetchServerResources(): CompletableFuture<String> {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(
                "{\"error\": \"Pterodactyl integration is disabled or not configured.\"}"
            )
        }

        val request = buildRequest("/resources").GET().build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200) {
                    logger.warning(
                        "Pterodactyl fetch resources failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException("Pterodactyl API error (Status: ${response.statusCode()})")
                }
                response.body()
            }
    }

    fun sendPowerSignal(signal: String): CompletableFuture<Void> {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(IllegalStateException("Pterodactyl integration is disabled."))
        }

        val jsonBody = "{\"signal\":\"$signal\"}"
        val request = buildRequest("/power")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning(
                        "Pterodactyl send power signal failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException(
                        "Pterodactyl power action failed (Status: ${response.statusCode()})"
                    )
                }
            }
    }

    fun sendCommand(command: String): CompletableFuture<Void> {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(IllegalStateException("Pterodactyl integration is disabled."))
        }

        val escapedCommand = command.replace("\"", "\\\"")
        val jsonBody = "{\"command\":\"$escapedCommand\"}"

        val request = buildRequest("/command")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning(
                        "Pterodactyl send command failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException(
                        "Pterodactyl send command failed (Status: ${response.statusCode()})"
                    )
                }
            }
    }

    fun listBackups(): CompletableFuture<String> {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture("[]")
        }

        val request = buildRequest("/backups").GET().build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200) {
                    logger.warning(
                        "Pterodactyl list backups failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException("Pterodactyl API error (Status: ${response.statusCode()})")
                }
                response.body()
            }
    }

    fun createBackup(): CompletableFuture<String> {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(IllegalStateException("Pterodactyl integration is disabled."))
        }

        val request = buildRequest("/backups")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning(
                        "Pterodactyl create backup failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException(
                        "Pterodactyl create backup failed (Status: ${response.statusCode()})"
                    )
                }
                response.body()
            }
    }

    fun deleteBackup(backupUuid: String): CompletableFuture<Void> {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(IllegalStateException("Pterodactyl integration is disabled."))
        }

        val request = buildRequest("/backups/$backupUuid").DELETE().build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning(
                        "Pterodactyl delete backup failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException(
                        "Pterodactyl delete backup failed (Status: ${response.statusCode()})"
                    )
                }
            }
    }

    fun getBackupDownloadUrl(backupUuid: String): CompletableFuture<String> {
        if (!isEnabled()) {
            return CompletableFuture.failedFuture(IllegalStateException("Pterodactyl integration is disabled."))
        }

        val request = buildRequest("/backups/$backupUuid/download")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200) {
                    logger.warning(
                        "Pterodactyl fetch download link failed with status code ${response.statusCode()}: ${response.body()}"
                    )
                    throw RuntimeException(
                        "Pterodactyl fetch download link failed (Status: ${response.statusCode()})"
                    )
                }
                response.body()
            }
    }
}
