package com.aspa.plugin.server

import com.aspa.plugin.ASPA
import com.aspa.plugin.api.AnalysisEngine
import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.api.MetricCollector
import com.aspa.plugin.model.ForecastResult
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.pterodactyl.PterodactylService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.aspa.plugin.model.User
import com.aspa.plugin.util.PasswordHasher
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Comparator
import java.util.HashMap
import java.util.Optional
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable

class EmbeddedServer(
    private val port: Int,
    private val apiToken: String,
    private val databaseProvider: DatabaseProvider,
    private val metricCollector: MetricCollector,
    private val analysisEngine: AnalysisEngine,
    private val pterodactylService: PterodactylService
) {
    private var app: Javalin? = null

    fun start() {
        app = Javalin.create { config ->
            config.staticFiles.add { staticFiles ->
                staticFiles.hostedPath = "/"
                staticFiles.directory = "/web"
                staticFiles.location = Location.CLASSPATH
                staticFiles.precompress = false
            }
        }

        app!!.before("/api/v1/*") { ctx ->
            if (ctx.method().name == "OPTIONS") {
                return@before
            }
            val path = ctx.path()
            if (path == "/api/v1/setup/status" || path == "/api/v1/setup" || path == "/api/v1/login") {
                return@before
            }

            val authHeader = ctx.header("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.status(401).json(ErrorResponse("Unauthorized: Missing or invalid Authorization header"))
                return@before
            }
            val token = authHeader.substring(7).trim()
            if (secureEquals(token, apiToken)) {
                // Legacy system admin token - full access
                ctx.attribute("user_username", "legacy_system")
                ctx.attribute("user_role", "ADMIN")
                ctx.attribute("user_permissions", listOf("health", "analytics", "inspector", "longtime", "pterodactyl", "users"))
                return@before
            }

            val optUser = databaseProvider.getUserByToken(token).get()
            if (optUser.isPresent) {
                val user = optUser.get()
                ctx.attribute("user_username", user.username)
                ctx.attribute("user_role", user.role)
                ctx.attribute("user_permissions", user.permissions)
            } else {
                ctx.status(401).json(ErrorResponse("Unauthorized: Invalid session token"))
                return@before
            }
        }

        app!!.exception(Exception::class.java) { e, ctx ->
            ctx.status(500).json(ErrorResponse("Internal Server Error: ${e.message}"))
            e.printStackTrace()
        }

        app!!.get("/api/v1/setup/status") { ctx ->
            val hasUsers = databaseProvider.hasAnyUser().get()
            val response = HashMap<String, Any>()
            response["setupRequired"] = !hasUsers
            ctx.json(response)
        }

        app!!.post("/api/v1/setup") { ctx ->
            val hasUsers = databaseProvider.hasAnyUser().get()
            if (hasUsers) {
                ctx.status(400).json(ErrorResponse("Setup has already been completed"))
                return@post
            }

            @Suppress("UNCHECKED_CAST")
            val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
            val username = body?.get("username")?.trim()
            val password = body?.get("password")
            if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
                ctx.status(400).json(ErrorResponse("Username and password are required"))
                return@post
            }

            val token = UUID.randomUUID().toString()
            val adminUser = User(
                username,
                PasswordHasher.hashPassword(password),
                "ADMIN",
                listOf("health", "analytics", "inspector", "longtime", "pterodactyl"),
                token
            )

            databaseProvider.saveUser(adminUser).get()

            val response = HashMap<String, Any>()
            response["token"] = token
            response["user"] = mapOf(
                "username" to adminUser.username,
                "role" to adminUser.role,
                "permissions" to adminUser.permissions
            )
            ctx.json(response)
        }

        app!!.post("/api/v1/login") { ctx ->
            @Suppress("UNCHECKED_CAST")
            val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
            val username = body?.get("username")?.trim()
            val password = body?.get("password")
            if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
                ctx.status(400).json(ErrorResponse("Username and password are required"))
                return@post
            }

            val optUser = databaseProvider.getUser(username).get()
            if (!optUser.isPresent) {
                ctx.status(401).json(ErrorResponse("Invalid username or password"))
                return@post
            }

            val user = optUser.get()
            if (!PasswordHasher.verifyPassword(password, user.passwordHash)) {
                ctx.status(401).json(ErrorResponse("Invalid username or password"))
                return@post
            }

            val token = UUID.randomUUID().toString()
            user.token = token
            if (PasswordHasher.needsRehash(user.passwordHash)) {
                user.passwordHash = PasswordHasher.hashPassword(password)
            }
            databaseProvider.saveUser(user).get()

            val response = HashMap<String, Any>()
            response["token"] = token
            response["user"] = mapOf(
                "username" to user.username,
                "role" to user.role,
                "permissions" to user.permissions
            )
            ctx.json(response)
        }

        app!!.get("/api/v1/users/me") { ctx ->
            val username = ctx.attribute<String>("user_username")
            if (username == null) {
                ctx.status(401).json(ErrorResponse("Unauthorized"))
                return@get
            }
            if (username == "legacy_system") {
                val profile = HashMap<String, Any>()
                profile["username"] = "legacy_system"
                profile["role"] = "ADMIN"
                profile["permissions"] = listOf("health", "analytics", "inspector", "longtime", "pterodactyl")
                ctx.json(profile)
                return@get
            }

            val optUser = databaseProvider.getUser(username).get()
            if (optUser.isPresent) {
                val user = optUser.get()
                val profile = HashMap<String, Any>()
                profile["username"] = user.username
                profile["role"] = user.role
                profile["permissions"] = user.permissions
                ctx.json(profile)
            } else {
                ctx.status(404).json(ErrorResponse("User not found"))
            }
        }

        app!!.get("/api/v1/users") { ctx ->
            if (!isAdmin(ctx)) {
                ctx.status(403).json(ErrorResponse("Forbidden: Administrative rights required"))
                return@get
            }
            val users = databaseProvider.getUsers().get()
            val response = users.map { u ->
                mapOf(
                    "username" to u.username,
                    "role" to u.role,
                    "permissions" to u.permissions
                )
            }
            ctx.json(response)
        }

        app!!.post("/api/v1/users") { ctx ->
            if (!isAdmin(ctx)) {
                ctx.status(403).json(ErrorResponse("Forbidden: Administrative rights required"))
                return@post
            }

            @Suppress("UNCHECKED_CAST")
            val body = ctx.bodyAsClass(Map::class.java) as? Map<String, Any>
            val username = (body?.get("username") as? String)?.trim()
            val password = body?.get("password") as? String
            val role = body?.get("role") as? String ?: "USER"
            @Suppress("UNCHECKED_CAST")
            val permissions = body?.get("permissions") as? List<String> ?: emptyList()

            if (username.isNullOrEmpty()) {
                ctx.status(400).json(ErrorResponse("Username is required"))
                return@post
            }

            val optExisting = databaseProvider.getUser(username).get()
            val passwordHash = if (!password.isNullOrEmpty()) {
                PasswordHasher.hashPassword(password)
            } else {
                if (optExisting.isPresent) {
                    optExisting.get().passwordHash
                } else {
                    ctx.status(400).json(ErrorResponse("Password is required for new users"))
                    return@post
                }
            }

            val user = User(
                username,
                passwordHash,
                role,
                permissions,
                optExisting.orElse(null)?.token
            )
            databaseProvider.saveUser(user).get()
            ctx.status(201).json(mapOf("success" to true))
        }

        app!!.delete("/api/v1/users/{username}") { ctx ->
            if (!isAdmin(ctx)) {
                ctx.status(403).json(ErrorResponse("Forbidden: Administrative rights required"))
                return@delete
            }
            val usernameToDelete = ctx.pathParam("username")
            val currentUser = ctx.attribute<String>("user_username")

            if (usernameToDelete == currentUser) {
                ctx.status(400).json(ErrorResponse("You cannot delete your own account"))
                return@delete
            }

            val users = databaseProvider.getUsers().get()
            val admins = users.filter { it.role == "ADMIN" }
            if (admins.size <= 1 && admins.any { it.username == usernameToDelete }) {
                ctx.status(400).json(ErrorResponse("Cannot delete the last remaining administrator account"))
                return@delete
            }

            databaseProvider.deleteUser(usernameToDelete).get()
            ctx.status(204)
        }

        app!!.get("/api/v1/status") { ctx ->
            if (!hasPermission(ctx, "health")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            val sessionsFuture = databaseProvider.getAllSessions(0, System.currentTimeMillis())
            val future = sessionsFuture.thenCombine(metricCollector.collectServerMetrics()) { sessions, record ->
                val status = HashMap<String, Any>()
                status["status"] = "online"
                status["version"] = "ASPA v1.0.0"
                status["uptimeMs"] = System.currentTimeMillis() - ASPA.getInstance()!!.getStartTime()
                status["databaseDriver"] = ASPA.getInstance()!!.getDatabaseDriverName()

                val uniquePlayers = sessions.map { it.uuid }.distinct().count()
                status["totalPlayersTracked"] = uniquePlayers.toInt()
                status["totalSessionsTracked"] = sessions.size
                status["metrics"] = record
                status
            }
            ctx.future { future.thenAccept { ctx.json(it) } }
        }

        app!!.get("/api/v1/metrics/history") { ctx ->
            if (!hasPermission(ctx, "health")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            val now = System.currentTimeMillis()
            val start =
                ctx.queryParamAsClass("start", Long::class.javaObjectType).getOrDefault(now - 2 * 60 * 60 * 1000L)
            val end = ctx.queryParamAsClass("end", Long::class.javaObjectType).getOrDefault(now)

            val future = databaseProvider.getServerMetricsHistory(start, end).thenApply { history ->
                val response = HashMap<String, Any>()
                response["start"] = start
                response["end"] = end
                response["history"] = history
                response
            }
            ctx.future { future.thenAccept { ctx.json(it) } }
        }

        app!!.get("/api/v1/players/overview") { ctx ->
            if (!hasPermission(ctx, "analytics")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            val now = System.currentTimeMillis()
            val start =
                ctx.queryParamAsClass("start", Long::class.javaObjectType)
                    .getOrDefault(now - 30L * 24 * 60 * 60 * 1000L)
            val end = ctx.queryParamAsClass("end", Long::class.javaObjectType).getOrDefault(now)

            val future = databaseProvider.getAllSessions(start, end).thenCompose(analysisEngine::calculateRetentionMetrics)
            ctx.future { future.thenAccept { ctx.json(it) } }
        }

        app!!.get("/api/v1/players/inspect") { ctx ->
            if (!hasPermission(ctx, "inspector")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
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
                                    createActiveProfile(activeSession)
                                } else {
                                    enrichWithActiveSession(profile, activeSession)
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
                                    createActiveProfile(activeSession)
                                } else {
                                    enrichWithActiveSession(profile, activeSession)
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

        app!!.get("/api/v1/players/invsee") { ctx ->
            if (!hasPermission(ctx, "inspector")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }

            val uuid = ctx.queryParam("uuid")
            val username = ctx.queryParam("username")
            if (uuid.isNullOrEmpty() && username.isNullOrEmpty()) {
                ctx.status(400).json(ErrorResponse("Missing uuid or username query parameter"))
                return@get
            }

            val now = System.currentTimeMillis()

            val player = if (!uuid.isNullOrEmpty()) {
                try {
                    org.bukkit.Bukkit.getPlayer(UUID.fromString(uuid))
                } catch (_: Exception) {
                    ctx.status(400).json(ErrorResponse("Invalid uuid format"))
                    return@get
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
                val offlineSnapshot = buildInventorySnapshotFromOfflineData(offline.uniqueId, resolvedUuid, resolvedUsername, now)
                ctx.json(offlineSnapshot)
                return@get
            }

            ctx.json(buildInventorySnapshot(player, now))
        }

        app!!.get("/api/v1/analysis/report") { ctx ->
            if (!hasPermission(ctx, "health") && !hasPermission(ctx, "analytics")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
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

        app!!.get("/api/v1/players/search") { ctx ->
            if (!hasPermission(ctx, "inspector")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
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
                return@get
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

        app!!.get("/api/v1/metrics/longtime") { ctx ->
            if (!hasPermission(ctx, "longtime")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            val now = System.currentTimeMillis()
            val start =
                ctx.queryParamAsClass("start", Long::class.javaObjectType).getOrDefault(now - 24L * 60 * 60 * 1000L)
            val end = ctx.queryParamAsClass("end", Long::class.javaObjectType).getOrDefault(now)
            val resolutionParam = ctx.queryParam("resolution")

            val rangeMs = end - start
            val bucket: Pair<Long, String> = if (!resolutionParam.isNullOrEmpty()) {
                when (resolutionParam) {
                    "1m" -> 60_000L to "1m"
                    "5m" -> 5 * 60_000L to "5m"
                    "15m" -> 15 * 60_000L to "15m"
                    "1h" -> 60 * 60_000L to "1h"
                    "6h" -> 6 * 60 * 60_000L to "6h"
                    else -> 5 * 60_000L to "5m"
                }
            } else {
                when {
                    rangeMs <= 3L * 60 * 60 * 1000L -> 60_000L to "1m"
                    rangeMs <= 12L * 60 * 60 * 1000L -> 5 * 60_000L to "5m"
                    rangeMs <= 3L * 24 * 60 * 60 * 1000L -> 15 * 60_000L to "15m"
                    rangeMs <= 14L * 24 * 60 * 60 * 1000L -> 60 * 60_000L to "1h"
                    else -> 6L * 60 * 60_000L to "6h"
                }
            }

            val finalBucketMs = bucket.first
            val finalResolution = bucket.second

            val future = databaseProvider.getServerMetricsHistory(start, end).thenApply { rawHistory ->
                val buckets = java.util.TreeMap<Long, MutableList<com.aspa.plugin.model.ServerMetricsRecord>>()
                for (r in rawHistory) {
                    val bucketKey = (r.timestamp / finalBucketMs) * finalBucketMs
                    buckets.computeIfAbsent(bucketKey) { ArrayList() }.add(r)
                }

                val downsampled = ArrayList<com.aspa.plugin.model.ServerMetricsRecord>()
                for ((key, bucketList) in buckets) {
                    val count = bucketList.size
                    if (count == 0) continue

                    val avg = com.aspa.plugin.model.ServerMetricsRecord()
                    avg.timestamp = key + finalBucketMs / 2

                    var sumTps = 0.0
                    var sumMspt = 0.0
                    var sumCpu = 0.0
                    var sumRamUsed = 0L
                    var sumRamMax = 0L
                    var sumPlayers = 0
                    var sumChunks = 0
                    var sumGcCount = 0L
                    var sumGcTime = 0L
                    var sumAvgPing = 0.0
                    var sumMaxPing = 0.0
                    val sumEntityCounts = HashMap<String, Long>()
                    val sumChunksPerWorld = HashMap<String, Long>()
                    val sumEntitiesPerWorld = HashMap<String, Long>()

                    for (r in bucketList) {
                        sumTps += r.tps
                        sumMspt += r.mspt
                        sumCpu += r.cpuUsage
                        sumRamUsed += r.ramUsedMb
                        sumRamMax += r.ramMaxMb
                        sumPlayers += r.onlinePlayers
                        sumChunks += r.loadedChunks
                        sumGcCount += r.gcCountDelta
                        sumGcTime += r.gcTimeDeltaMs
                        sumAvgPing += r.avgPing
                        sumMaxPing += r.maxPing

                        r.entityCounts?.forEach { (k, v) ->
                            sumEntityCounts[k] = (sumEntityCounts[k] ?: 0L) + v
                        }
                        r.chunksPerWorld?.forEach { (k, v) ->
                            sumChunksPerWorld[k] = (sumChunksPerWorld[k] ?: 0L) + v
                        }
                        r.entitiesPerWorld?.forEach { (k, v) ->
                            sumEntitiesPerWorld[k] = (sumEntitiesPerWorld[k] ?: 0L) + v
                        }
                    }

                    avg.tps = sumTps / count
                    avg.mspt = sumMspt / count
                    avg.cpuUsage = sumCpu / count
                    avg.ramUsedMb = sumRamUsed / count
                    avg.ramMaxMb = sumRamMax / count
                    avg.onlinePlayers = sumPlayers / count
                    avg.loadedChunks = sumChunks / count
                    avg.gcCountDelta = sumGcCount / count
                    avg.gcTimeDeltaMs = sumGcTime / count
                    avg.avgPing = sumAvgPing / count
                    avg.maxPing = sumMaxPing / count

                    val avgEntityCounts = HashMap<String, Int>()
                    for ((k, v) in sumEntityCounts) {
                        avgEntityCounts[k] = (v / count).toInt()
                    }
                    avg.entityCounts = avgEntityCounts

                    val avgChunksPerWorld = HashMap<String, Int>()
                    for ((k, v) in sumChunksPerWorld) {
                        avgChunksPerWorld[k] = (v / count).toInt()
                    }
                    avg.chunksPerWorld = avgChunksPerWorld

                    val avgEntitiesPerWorld = HashMap<String, Int>()
                    for ((k, v) in sumEntitiesPerWorld) {
                        avgEntitiesPerWorld[k] = (v / count).toInt()
                    }
                    avg.entitiesPerWorld = avgEntitiesPerWorld

                    downsampled.add(avg)
                }

                val response = HashMap<String, Any>()
                response["start"] = start
                response["end"] = end
                response["resolution"] = finalResolution
                response["bucketMs"] = finalBucketMs
                response["rawCount"] = rawHistory.size
                response["downsampledCount"] = downsampled.size
                response["history"] = downsampled
                response
            }
            ctx.future { future.thenAccept { ctx.json(it) } }
        }

        app!!.get("/api/v1/pterodactyl/status") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            val response = HashMap<String, Any>()
            response["enabled"] = pterodactylService.isEnabled()
            response["configured"] = pterodactylService.isConfigured()

            if (!pterodactylService.isEnabled()) {
                ctx.json(response)
                return@get
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

        app!!.post("/api/v1/pterodactyl/power") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@post
            }
            if (!pterodactylService.isEnabled()) {
                ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
                return@post
            }

            @Suppress("UNCHECKED_CAST")
            val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
            val signal = body?.get("signal")
            if (signal == null) {
                ctx.status(400).json(ErrorResponse("Missing power signal in body"))
                return@post
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

        app!!.post("/api/v1/pterodactyl/command") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@post
            }
            if (!pterodactylService.isEnabled()) {
                ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
                return@post
            }

            @Suppress("UNCHECKED_CAST")
            val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
            val command = body?.get("command")
            if (command == null) {
                ctx.status(400).json(ErrorResponse("Missing command in body"))
                return@post
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

        app!!.get("/api/v1/pterodactyl/backups") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            if (!pterodactylService.isEnabled()) {
                ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
                return@get
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

        app!!.post("/api/v1/pterodactyl/backups") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@post
            }
            if (!pterodactylService.isEnabled()) {
                ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
                return@post
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

        app!!.delete("/api/v1/pterodactyl/backups/{uuid}") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@delete
            }
            if (!pterodactylService.isEnabled()) {
                ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
                return@delete
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

        app!!.get("/api/v1/pterodactyl/backups/{uuid}/download") { ctx ->
            if (!hasPermission(ctx, "pterodactyl")) {
                ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
                return@get
            }
            if (!pterodactylService.isEnabled()) {
                ctx.status(400).json(ErrorResponse("Pterodactyl integration is disabled"))
                return@get
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

        app!!.get("/favicon.png") { ctx ->
            EmbeddedServer::class.java.getResourceAsStream("/web/favicon.png").use { `is` ->
                if (`is` != null) {
                    ctx.contentType("image/png")
                    ctx.result(`is`.readBytes())
                } else {
                    ctx.status(404)
                }
            }
        }

        app!!.get("/favicon.ico") { ctx ->
            EmbeddedServer::class.java.getResourceAsStream("/web/favicon.png").use { `is` ->
                if (`is` != null) {
                    ctx.contentType("image/png")
                    ctx.result(`is`.readBytes())
                } else {
                    ctx.status(404)
                }
            }
        }

        app!!.get("/icons.png") { ctx ->
            EmbeddedServer::class.java.getResourceAsStream("/web/icons.png").use { `is` ->
                if (`is` != null) {
                    ctx.contentType("image/png")
                    ctx.result(`is`.readBytes())
                } else {
                    ctx.status(404)
                }
            }
        }

        app!!.get("/logo.png") { ctx ->
            EmbeddedServer::class.java.getResourceAsStream("/web/logo.png").use { `is` ->
                if (`is` != null) {
                    ctx.contentType("image/png")
                    ctx.result(`is`.readBytes())
                } else {
                    ctx.status(404)
                }
            }
        }

        app!!.get("/{*path}") { ctx ->
            val path = ctx.path()
            if (!path.startsWith("/api/")) {
                EmbeddedServer::class.java.getResourceAsStream("/web/index.html").use { `is` ->
                    if (`is` != null) {
                        ctx.contentType("text/html")
                        ctx.result(`is`.readBytes())
                    } else {
                        ctx.status(404).result("Static assets not found. Build front-end folder first.")
                    }
                }
            } else {
                ctx.status(404).json(ErrorResponse("API Endpoint not found"))
            }
        }

        app!!.start(port)
    }

    private fun createActiveProfile(activeSession: PlayerSessionRecord): PlayerProfile {
        val profile = PlayerProfile()
        profile.uuid = activeSession.uuid
        profile.username = activeSession.username
        profile.firstLoginMs = activeSession.loginMs
        profile.lastLoginMs = activeSession.loginMs
        profile.totalPlaytimeMs = activeSession.playtimeMs
        profile.averagePing = activeSession.averagePing
        profile.countryCode = activeSession.countryCode

        val sessions = ArrayList<PlayerSessionRecord>()
        sessions.add(activeSession)
        profile.sessions = sessions
        profile.activityPunchcard = computePunchcardForProfile(sessions)
        return profile
    }

    private fun enrichWithActiveSession(
        profile: PlayerProfile,
        activeSession: PlayerSessionRecord
    ): PlayerProfile {
        val sessions = ArrayList<PlayerSessionRecord>()
        sessions.add(activeSession)
        profile.sessions?.let { sessions.addAll(it) }
        profile.sessions = sessions

        profile.lastLoginMs = activeSession.loginMs

        var newTotalPlaytime = activeSession.playtimeMs
        if (profile.totalPlaytimeMs > 0) {
            newTotalPlaytime += profile.totalPlaytimeMs
        }
        profile.totalPlaytimeMs = newTotalPlaytime

        var sumPing = 0L
        var count = 0
        for (s in sessions) {
            sumPing += s.averagePing.toLong()
            count++
        }
        if (count > 0) {
            profile.averagePing = (sumPing / count).toInt()
        }

        profile.activityPunchcard = computePunchcardForProfile(sessions)
        return profile
    }

    private fun computePunchcardForProfile(sessions: List<PlayerSessionRecord>): Array<IntArray> {
        val punchcard = Array(7) { IntArray(24) }
        for (s in sessions) {
            if (s.loginMs > 0) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = s.loginMs
                val day = cal.get(Calendar.DAY_OF_WEEK) - 1
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                if (day in 0..6 && hour in 0..23) {
                    punchcard[day][hour]++
                }
            }
        }
        return punchcard
    }

    private fun buildEmptyInventorySnapshot(
        uuid: String,
        username: String,
        reason: String,
        now: Long
    ): Map<String, Any?> {
        val response = HashMap<String, Any?>()
        response["uuid"] = uuid
        response["username"] = username
        response["online"] = false
        response["fetchedAtMs"] = now
        response["unavailableReason"] = reason
        response["inventory"] = MutableList<Map<String, Any?>?>(36) { null }
        response["armor"] = MutableList<Map<String, Any?>?>(4) { null }
        response["offhand"] = null
        response["enderChest"] = MutableList<Map<String, Any?>?>(27) { null }
        return response
    }

    private fun buildInventorySnapshot(player: org.bukkit.entity.Player, now: Long): Map<String, Any?> {
        val response = HashMap<String, Any?>()
        response["uuid"] = player.uniqueId.toString()
        response["username"] = player.name
        response["online"] = true
        response["fetchedAtMs"] = now

        val storage = player.inventory.storageContents
        val inventorySlots = ArrayList<Map<String, Any?>?>()
        for (i in 0 until 36) {
            val item = if (i < storage.size) storage[i] else null
            inventorySlots.add(serializeItem(item, i))
        }
        response["inventory"] = inventorySlots

        val armorSlots = ArrayList<Map<String, Any?>?>()
        armorSlots.add(serializeItem(player.inventory.helmet, 0))
        armorSlots.add(serializeItem(player.inventory.chestplate, 1))
        armorSlots.add(serializeItem(player.inventory.leggings, 2))
        armorSlots.add(serializeItem(player.inventory.boots, 3))
        response["armor"] = armorSlots

        response["offhand"] = serializeItem(player.inventory.itemInOffHand, 0)

        val ender = player.enderChest.contents
        val enderSlots = ArrayList<Map<String, Any?>?>()
        for (i in 0 until 27) {
            val item = if (i < ender.size) ender[i] else null
            enderSlots.add(serializeItem(item, i))
        }
        response["enderChest"] = enderSlots
        return response
    }

    private fun buildInventorySnapshotFromOfflineData(
        uuid: UUID?,
        fallbackUuid: String,
        username: String,
        now: Long
    ): Map<String, Any?> {
        if (uuid == null) {
            return buildEmptyInventorySnapshot(fallbackUuid, username, "Player data unavailable", now)
        }

        val dataFile = findPlayerDataFile(uuid)
            ?: return buildEmptyInventorySnapshot(fallbackUuid, username, "Player data file not found", now)

        return try {
            val root = readPlayerDataRoot(dataFile)
                ?: return buildEmptyInventorySnapshot(fallbackUuid, username, "Failed to read player data", now)
            buildInventorySnapshotFromNbt(uuid, username, root, now)
        } catch (ex: Exception) {
            buildEmptyInventorySnapshot(fallbackUuid, username, "Failed to read player data: ${ex.message}", now)
        }
    }

    private fun findPlayerDataFile(uuid: UUID): File? {
        for (world in org.bukkit.Bukkit.getWorlds()) {
            val file = File(File(world.worldFolder, "playerdata"), "${uuid}.dat")
            if (file.exists()) return file
        }
        return null
    }

    private fun readPlayerDataRoot(file: File): Any? {
        return readPlayerDataRootReflective(file)
    }

    private fun buildInventorySnapshotFromNbt(
        uuid: UUID,
        username: String,
        root: Any,
        now: Long
    ): Map<String, Any?> {
        val response = HashMap<String, Any?>()
        response["uuid"] = uuid.toString()
        response["username"] = username
        response["online"] = false
        response["fetchedAtMs"] = now
        response["unavailableReason"] = "Player is offline. Showing last saved inventory snapshot."

        val inventorySlots = MutableList<Map<String, Any?>?>(36) { null }
        val armorSlots = MutableList<Map<String, Any?>?>(4) { null }
        var offhand: Map<String, Any?>? = null
        val enderSlots = MutableList<Map<String, Any?>?>(27) { null }

        val inventoryTag = getListTag(root, "Inventory", 10)
        val invSize = listSize(inventoryTag)
        for (i in 0 until invSize) {
            val item = getListCompound(inventoryTag, i) ?: continue
            val slot = readByte(item, "Slot")?.toInt() ?: continue
            val mapped = serializeItemFromNbt(item, slot)
            when {
                slot in 0..35 -> inventorySlots[slot] = mapped
                slot in 100..103 -> armorSlots[slot - 100] = mapped
                slot == -106 -> offhand = mapped
            }
        }

        val enderTag = getListTag(root, "EnderItems", 10)
        val enderSize = listSize(enderTag)
        for (i in 0 until enderSize) {
            val item = getListCompound(enderTag, i) ?: continue
            val slot = readByte(item, "Slot")?.toInt() ?: continue
            if (slot in 0..26) {
                enderSlots[slot] = serializeItemFromNbt(item, slot)
            }
        }

        response["inventory"] = inventorySlots
        response["armor"] = armorSlots
        response["offhand"] = offhand
        response["enderChest"] = enderSlots
        return response
    }

    private fun serializeItem(item: ItemStack?, slot: Int): Map<String, Any?>? {
        if (item == null || item.type == Material.AIR) return null
        val result = HashMap<String, Any?>()
        result["slot"] = slot
        result["material"] = item.type.name
        result["amount"] = item.amount

        val meta = item.itemMeta
        if (meta != null) {
            if (meta.hasDisplayName()) {
                result["displayName"] = meta.displayName
            }
            if (meta.hasLore()) {
                result["lore"] = meta.lore
            }
            if (meta.hasCustomModelData()) {
                result["customModelData"] = meta.customModelData
            }
            if (meta.hasEnchants()) {
                val enchantments = HashMap<String, Int>()
                for ((ench, level) in meta.enchants) {
                    enchantments[ench.key.key] = level
                }
                result["enchantments"] = enchantments
            }
            if (meta is Damageable) {
                result["durability"] = meta.damage
            }
        }

        return result
    }

    private fun serializeItemFromNbt(item: Any, slot: Int): Map<String, Any?> {
        val result = HashMap<String, Any?>()
        result["slot"] = slot

        val rawId = readString(item, "id") ?: "minecraft:air"
        val materialId = rawId.substringAfter(":", rawId).uppercase()
        result["material"] = materialId

        result["amount"] = readByte(item, "Count")?.toInt() ?: 1

        val damage = readInt(item, "Damage") ?: readShort(item, "Damage")?.toInt()
        if (damage != null) result["durability"] = damage

        return result
    }

    private fun readPlayerDataRootReflective(file: File): Any? {
        return try {
            val nbtIo = Class.forName("net.minecraft.nbt.NbtIo")
            val readCompressed = nbtIo.getMethod("readCompressed", InputStream::class.java)
            file.inputStream().use { input ->
                readCompressed.invoke(null, input)
            }
        } catch (ex: Exception) {
            null
        }
    }

    private fun getListTag(compound: Any, key: String, elementType: Int): Any? {
        return try {
            val method = compound.javaClass.getMethod("getList", String::class.java, Int::class.javaPrimitiveType)
            method.invoke(compound, key, elementType)
        } catch (ex: Exception) {
            null
        }
    }

    private fun listSize(list: Any?): Int {
        if (list == null) return 0
        return try {
            val method = list.javaClass.getMethod("size")
            (method.invoke(list) as? Int) ?: 0
        } catch (ex: Exception) {
            0
        }
    }

    private fun getListCompound(list: Any?, index: Int): Any? {
        if (list == null) return null
        return try {
            val method = list.javaClass.getMethod("getCompound", Int::class.javaPrimitiveType)
            method.invoke(list, index)
        } catch (_: Exception) {
            try {
                val method = list.javaClass.getMethod("get", Int::class.javaPrimitiveType)
                method.invoke(list, index)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun readString(compound: Any, key: String): String? {
        return try {
            val method = compound.javaClass.getMethod("getString", String::class.java)
            method.invoke(compound, key) as? String
        } catch (ex: Exception) {
            null
        }
    }

    private fun readByte(compound: Any, key: String): Byte? {
        return try {
            val method = compound.javaClass.getMethod("getByte", String::class.java)
            method.invoke(compound, key) as? Byte
        } catch (ex: Exception) {
            null
        }
    }

    private fun readShort(compound: Any, key: String): Short? {
        return try {
            val method = compound.javaClass.getMethod("getShort", String::class.java)
            method.invoke(compound, key) as? Short
        } catch (ex: Exception) {
            null
        }
    }

    private fun readInt(compound: Any, key: String): Int? {
        return try {
            val method = compound.javaClass.getMethod("getInt", String::class.java)
            method.invoke(compound, key) as? Int
        } catch (ex: Exception) {
            null
        }
    }


    private fun hasPermission(ctx: io.javalin.http.Context, requiredPerm: String): Boolean {
        val role = ctx.attribute<String>("user_role") ?: return false
        if (role == "ADMIN") return true
        val permissions = ctx.attribute<List<String>>("user_permissions") ?: return false
        return permissions.contains(requiredPerm)
    }

    private fun isAdmin(ctx: io.javalin.http.Context): Boolean {
        val role = ctx.attribute<String>("user_role") ?: return false
        return role == "ADMIN"
    }

    private fun secureEquals(left: String, right: String): Boolean {
        val leftBytes = left.toByteArray(StandardCharsets.UTF_8)
        val rightBytes = right.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(leftBytes, rightBytes)
    }

    fun stop() {
        app?.stop()
    }

    data class ErrorResponse(val error: String)
}
