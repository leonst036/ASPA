package com.aspa.plugin.server

import com.aspa.plugin.ASPA
import com.aspa.plugin.api.AnalysisEngine
import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.model.ForecastResult
import com.aspa.plugin.model.PerformanceAnomaly
import io.javalin.Javalin
import io.javalin.http.Context
import java.util.ArrayList
import java.util.HashMap
import java.util.UUID
import java.util.concurrent.CompletableFuture

class PlayerController(
    private val databaseProvider: DatabaseProvider,
    private val analysisEngine: AnalysisEngine
) {
    fun register(app: Javalin) {
        app.get("/api/v1/players/overview") { getOverview(it) }
        app.get("/api/v1/players/inspect") { inspect(it) }
        app.get("/api/v1/players/invsee") { invsee(it) }
        app.get("/api/v1/analysis/report") { getReport(it) }
        app.get("/api/v1/players/search") { search(it) }
    }

    private fun getOverview(ctx: Context) {
        if (!ctx.hasPermission("analytics")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        val now = System.currentTimeMillis()
        val start =
            ctx.queryParamAsClass("start", Long::class.javaObjectType)
                .getOrDefault(now - 30L * 24 * 60 * 60 * 1000L)
        val end = ctx.queryParamAsClass("end", Long::class.javaObjectType).getOrDefault(now)

        val future = databaseProvider.getAllSessions(start, end).thenCompose(analysisEngine::calculateRetentionMetrics)
        ctx.future { future.thenAccept { ctx.json(it) } }
    }

    private fun inspect(ctx: Context) {
        if (!ctx.hasPermission("inspector")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        val uuid = ctx.queryParam("uuid")
        val username = ctx.queryParam("username")

        if (!uuid.isNullOrEmpty()) {
            val future = databaseProvider.getPlayerProfile(uuid).thenAccept { optProfile ->
                var profile = optProfile.orElse(null)

                val onlinePlayer = try {
                    org.bukkit.Bukkit.getPlayer(UUID.fromString(uuid))
                } catch (_: Exception) {
                    null
                }

                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    val collector = ASPA.getInstance()!!.getPlayerMetricCollector()
                    if (collector != null) {
                        val activeSessionOpt = collector.getActiveSession(onlinePlayer.uniqueId)
                        if (activeSessionOpt.isPresent) {
                            val activeSession = activeSessionOpt.get()
                            profile = if (profile == null) {
                                InventorySnapshotHelper.createActiveProfile(activeSession)
                            } else {
                                InventorySnapshotHelper.enrichWithActiveSession(profile, activeSession)
                            }
                        }
                    }
                }

                if (profile != null) {
                    ctx.json(profile)
                } else {
                    ctx.status(404).json(ErrorResponse("Player profile not found"))
                }
            }
            ctx.future { future }
        } else if (!username.isNullOrEmpty()) {
            val future = databaseProvider.getPlayerProfileByName(username).thenAccept { optProfile ->
                var profile = optProfile.orElse(null)

                val onlinePlayer = try {
                    org.bukkit.Bukkit.getPlayer(username)
                } catch (_: Exception) {
                    null
                }

                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    val collector = ASPA.getInstance()!!.getPlayerMetricCollector()
                    if (collector != null) {
                        val activeSessionOpt = collector.getActiveSession(onlinePlayer.uniqueId)
                        if (activeSessionOpt.isPresent) {
                            val activeSession = activeSessionOpt.get()
                            profile = if (profile == null) {
                                InventorySnapshotHelper.createActiveProfile(activeSession)
                            } else {
                                InventorySnapshotHelper.enrichWithActiveSession(profile, activeSession)
                            }
                        }
                    }
                }

                if (profile != null) {
                    ctx.json(profile)
                } else {
                    ctx.status(404).json(ErrorResponse("Player profile not found"))
                }
            }
            ctx.future { future }
        } else {
            ctx.status(400).json(ErrorResponse("Missing uuid or username query parameter"))
        }
    }

    private fun invsee(ctx: Context) {
        if (!ctx.hasPermission("inspector")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }

        val uuid = ctx.queryParam("uuid")
        val username = ctx.queryParam("username")
        if (uuid.isNullOrEmpty() && username.isNullOrEmpty()) {
            ctx.status(400).json(ErrorResponse("Missing uuid or username query parameter"))
            return
        }

        val now = System.currentTimeMillis()

        val player = if (!uuid.isNullOrEmpty()) {
            try {
                org.bukkit.Bukkit.getPlayer(UUID.fromString(uuid))
            } catch (_: Exception) {
                ctx.status(400).json(ErrorResponse("Invalid uuid format"))
                return
            }
        } else {
            org.bukkit.Bukkit.getPlayer(username!!)
        }

        if (player == null || !player.isOnline) {
            val offline = if (!uuid.isNullOrEmpty()) {
                org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(uuid))
            } else {
                org.bukkit.Bukkit.getOfflinePlayer(username!!)
            }

            val resolvedUuid = offline.uniqueId?.toString() ?: (uuid ?: "unknown")
            val resolvedUsername = offline.name ?: username ?: "unknown"
            val offlineSnapshot = InventorySnapshotHelper.buildInventorySnapshotFromOfflineData(offline.uniqueId, resolvedUuid, resolvedUsername, now)
            ctx.json(offlineSnapshot)
            return
        }

        ctx.json(InventorySnapshotHelper.buildInventorySnapshot(player, now))
    }

    private fun getReport(ctx: Context) {
        if (!ctx.hasPermission("health") && !ctx.hasPermission("analytics")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        val now = System.currentTimeMillis()
        val start =
            ctx.queryParamAsClass("start", Long::class.javaObjectType)
                .getOrDefault(now - 14L * 24 * 60 * 60 * 1000L)
        val end = ctx.queryParamAsClass("end", Long::class.javaObjectType).getOrDefault(now)

        val anomaliesFuture: CompletableFuture<List<PerformanceAnomaly>> = databaseProvider.getAnomalies(50)
        val forecastFuture: CompletableFuture<ForecastResult> =
            databaseProvider.getAllSessions(start, end).thenCompose(analysisEngine::forecastActivity)

        val future = anomaliesFuture.thenCombine(forecastFuture) { anomalies, forecast ->
            val report = HashMap<String, Any>()
            report["timestamp"] = now
            report["anomalies"] = anomalies
            report["forecast"] = forecast
            report
        }
        ctx.future { future.thenAccept { ctx.json(it) } }
    }

    private fun search(ctx: Context) {
        if (!ctx.hasPermission("inspector")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
        }
        val query = ctx.queryParam("q")
        if (query.isNullOrEmpty()) {
            val players = org.bukkit.Bukkit.getOfflinePlayers().toMutableList()
            players.sortByDescending { it.lastPlayed }
            val results = ArrayList<Map<String, String>>()
            for (p in players) {
                if (p.name != null) {
                    val res = HashMap<String, String>()
                    res["uuid"] = p.uniqueId.toString()
                    res["username"] = p.name!!
                    results.add(res)
                    if (results.size >= 4) break
                }
            }
            ctx.json(results)
            return
        }
        val q = query.lowercase()
        var results = ArrayList<Map<String, String>>()
        for (p in org.bukkit.Bukkit.getOfflinePlayers()) {
            if (p.name != null && p.name!!.lowercase().contains(q)) {
                val res = HashMap<String, String>()
                res["uuid"] = p.uniqueId.toString()
                res["username"] = p.name!!
                results.add(res)
            }
        }
        if (results.size > 10) {
            results = ArrayList(results.subList(0, 10))
        }
        ctx.json(results)
    }
}
