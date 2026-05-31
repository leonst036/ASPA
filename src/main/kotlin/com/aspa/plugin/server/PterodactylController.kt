package com.aspa.plugin.server

import com.aspa.plugin.pterodactyl.PterodactylService
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.Context
import java.util.ArrayList
import java.util.HashMap

class PterodactylController(
    private val pterodactylService: PterodactylService
) {
    fun register(app: Javalin) {
        app.get("/api/v1/pterodactyl/status") { getStatus(it) }
        app.post("/api/v1/pterodactyl/power") { sendPower(it) }
        app.post("/api/v1/pterodactyl/command") { sendCommand(it) }
        app.get("/api/v1/pterodactyl/backups") { listBackups(it) }
        app.post("/api/v1/pterodactyl/backups") { createBackup(it) }
        app.delete("/api/v1/pterodactyl/backups/{uuid}") { deleteBackup(it) }
        app.get("/api/v1/pterodactyl/backups/{uuid}/download") { getBackupDownloadUrl(it) }
    }

    private fun getStatus(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        val response = HashMap<String, Any>()
        response["enabled"] = pterodactylService.isEnabled()
        response["configured"] = pterodactylService.isConfigured()

        if (!pterodactylService.isEnabled()) {
            ctx.json(response)
            return
        }

        ctx.future {
            pterodactylService.fetchServerResources()
                .thenAccept { resourcesJson ->
                    try {
                        val mapper = ObjectMapper()
                        val root = mapper.readTree(resourcesJson)
                        val attrs = root.get("attributes")
                        if (attrs != null) {
                            val flatRes = HashMap<String, Any>()
                            flatRes["state"] = attrs.get("current_state").asText()
                            flatRes["isSuspended"] = attrs.get("is_suspended").asBoolean()

                            val res = attrs.get("resources")
                            if (res != null) {
                                flatRes["ramBytes"] = res.get("memory_bytes").asLong()
                                flatRes["cpuAbsolute"] = res.get("cpu_absolute").asDouble()
                                flatRes["diskBytes"] = res.get("disk_bytes").asLong()
                                flatRes["networkRxBytes"] = res.get("network_rx_bytes").asLong()
                                flatRes["networkTxBytes"] = res.get("network_tx_bytes").asLong()
                            }
                            response["resources"] = flatRes
                        }
                        ctx.json(response)
                    } catch (e: Exception) {
                        ctx.status(500).json(ErrorResponse("Failed to parse resources: ${e.message}"))
                    }
                }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Failed to connect to Pterodactyl: ${ex.message}"))
                    null
                }
        }
    }

    private fun sendPower(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        @Suppress("UNCHECKED_CAST")
        val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
        val signal = body?.get("signal")
        if (signal == null) {
            ctx.status(400).json(ErrorResponse("Missing power signal in body"))
            return
        }

        ctx.future {
            pterodactylService.sendPowerSignal(signal)
                .thenAccept { ctx.status(204) }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Power control failed: ${ex.message}"))
                    null
                }
        }
    }

    private fun sendCommand(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        @Suppress("UNCHECKED_CAST")
        val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
        val command = body?.get("command")
        if (command == null) {
            ctx.status(400).json(ErrorResponse("Missing command in body"))
            return
        }

        ctx.future {
            pterodactylService.sendCommand(command)
                .thenAccept { ctx.status(204) }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Command execution failed: ${ex.message}"))
                    null
                }
        }
    }

    private fun consoleOutput(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        ctx.future {
            pterodactylService.readConsoleOutput()
                .thenAccept { consoleOutput ->
                    ctx.json(mapOf("consoleOutput" to consoleOutput))
                }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Console output failed: ${ex.message}"))
                    null
                }
        }
    }

    private fun listBackups(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        ctx.future {
            pterodactylService.listBackups()
                .thenAccept { backupsJson ->
                    try {
                        val mapper = ObjectMapper()
                        val root = mapper.readTree(backupsJson)
                        val data = root.get("data")
                        val flatBackups = ArrayList<Map<String, Any?>>()
                        if (data != null && data.isArray) {
                            for (item in data) {
                                val attrs = item.get("attributes")
                                if (attrs != null) {
                                    val b = HashMap<String, Any?>()
                                    b["uuid"] = attrs.get("uuid").asText()
                                    b["name"] = attrs.get("name").asText()
                                    b["bytes"] = attrs.get("bytes").asLong()
                                    b["isSuccessful"] = attrs.get("is_successful").asBoolean()
                                    b["isLocked"] = attrs.get("is_locked").asBoolean()
                                    b["createdAt"] = attrs.get("created_at").asText()
                                    b["completedAt"] =
                                        if (attrs.has("completed_at") && !attrs.get("completed_at").isNull) attrs.get(
                                            "completed_at"
                                        ).asText() else null

                                    val files = ArrayList<String>()
                                    if (attrs.has("ignored_files") && attrs.get("ignored_files").isArray) {
                                        for (f in attrs.get("ignored_files")) {
                                            files.add(f.asText())
                                        }
                                    }
                                    b["ignoredFiles"] = files
                                    flatBackups.add(b)
                                }
                            }
                        }
                        ctx.json(flatBackups)
                    } catch (e: Exception) {
                        ctx.status(500).json(ErrorResponse("Failed to parse backups list: ${e.message}"))
                    }
                }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Backups fetch failed: ${ex.message}"))
                    null
                }
        }
    }

    private fun createBackup(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        ctx.future {
            pterodactylService.createBackup()
                .thenAccept { backupJson ->
                    try {
                        val mapper = ObjectMapper()
                        val root = mapper.readTree(backupJson)
                        val attrs = root.get("attributes")
                        if (attrs != null) {
                            val b = HashMap<String, Any?>()
                            b["uuid"] = attrs.get("uuid").asText()
                            b["name"] = attrs.get("name").asText()
                            b["bytes"] = attrs.get("bytes").asLong()
                            b["isSuccessful"] = attrs.get("is_successful").asBoolean()
                            b["isLocked"] = attrs.get("is_locked").asBoolean()
                            b["createdAt"] = attrs.get("created_at").asText()
                            b["completedAt"] =
                                if (attrs.has("completed_at") && !attrs.get("completed_at").isNull) attrs.get(
                                    "completed_at"
                                ).asText() else null

                            val files = ArrayList<String>()
                            if (attrs.has("ignored_files") && attrs.get("ignored_files").isArray) {
                                for (f in attrs.get("ignored_files")) {
                                    files.add(f.asText())
                                }
                            }
                            b["ignoredFiles"] = files
                            ctx.json(b)
                        } else {
                            ctx.status(500).json(ErrorResponse("Invalid response structure from Pterodactyl"))
                        }
                    } catch (e: Exception) {
                        ctx.status(500).json(ErrorResponse("Failed to parse backup creation: ${e.message}"))
                    }
                }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Backup creation failed: ${ex.message}"))
                    null
                }
        }
    }

    private fun deleteBackup(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        val uuid = ctx.pathParam("uuid")
        ctx.future {
            pterodactylService.deleteBackup(uuid)
                .thenAccept { ctx.status(204) }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Backup deletion failed: ${ex.message}"))
                    null
                }
        }
    }

    private fun getBackupDownloadUrl(ctx: Context) {
        if (!ctx.hasPermission("pterodactyl")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        if (!pterodactylService.isEnabled()) {
            ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
            return
        }

        val uuid = ctx.pathParam("uuid")
        ctx.future {
            pterodactylService.getBackupDownloadUrl(uuid)
                .thenAccept { downloadJson ->
                    try {
                        val mapper = ObjectMapper()
                        val root = mapper.readTree(downloadJson)
                        val attrs = root.get("attributes")
                        if (attrs != null && attrs.has("url")) {
                            val res = HashMap<String, String>()
                            res["url"] = attrs.get("url").asText()
                            ctx.json(res)
                        } else {
                            ctx.status(500).json(ErrorResponse("Invalid signed URL structure from Pterodactyl"))
                        }
                    } catch (e: Exception) {
                        ctx.status(500).json(ErrorResponse("Failed to parse download URL: ${e.message}"))
                    }
                }
                .exceptionally { ex ->
                    ctx.status(502).json(ErrorResponse("Backup download failed: ${ex.message}"))
                    null
                }
        }
    }
}
