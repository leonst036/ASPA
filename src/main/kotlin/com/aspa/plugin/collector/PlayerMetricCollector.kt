package com.aspa.plugin.collector

import com.aspa.plugin.ASPA
import com.aspa.plugin.api.MetricCollector
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.ArrayList
import java.util.Collections
import java.util.ConcurrentModificationException
import java.util.HashMap
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class PlayerMetricCollector(private val plugin: ASPA) : MetricCollector, Listener {
    private var databaseReader: DatabaseReader? = null

    private val playerLoginTimes = ConcurrentHashMap<UUID, Long>()
    private val playerIpAddresses = ConcurrentHashMap<UUID, String>()
    private val playerUsernames = ConcurrentHashMap<UUID, String>()
    private val playerCountryCodes = ConcurrentHashMap<UUID, String>()
    private val playerTimezones = ConcurrentHashMap<UUID, String>()
    private val playerWorldPlaytimes = ConcurrentHashMap<UUID, MutableMap<String, Long>>()
    private val playerWorldStartTimes = ConcurrentHashMap<UUID, Long>()
    private val playerPings = ConcurrentHashMap<UUID, MutableList<Int>>()
    private val playerPunchcards = ConcurrentHashMap<UUID, Array<IntArray>>()
    private val playerLastPunchcardMinutes = ConcurrentHashMap<UUID, String>()
    private val playerCurrentWorlds = ConcurrentHashMap<UUID, String>()

    init {
        initializeGeoIP()
    }

    private fun initializeGeoIP() {
        val databaseFile = File(plugin.dataFolder, "GeoLite2-City.mmdb")
        if (databaseFile.exists()) {
            try {
                databaseReader = DatabaseReader.Builder(databaseFile).build()
                plugin.logger.info("Successfully loaded MaxMind GeoIP database.")
            } catch (e: IOException) {
                plugin.logger.warning("Failed to initialize MaxMind DatabaseReader: ${e.message}")
            }
        } else {
            plugin.logger.warning("======================================================================")
            plugin.logger.warning("GeoLite2-City.mmdb is missing from the plugin data folder!")
            plugin.logger.warning("Geographic lookups will fall back to 'Unknown'.")
            plugin.logger.warning(
                "Please download GeoLite2-City.mmdb from MaxMind and place it in: ${plugin.dataFolder.absolutePath}"
            )
            plugin.logger.warning("======================================================================")
        }
    }

    fun start() {
        for (player in Bukkit.getOnlinePlayers()) {
            try {
                val uuidStr = player.uniqueId.toString()
                val username = player.name
                val ipAddress =
                    player.address?.address?.hostAddress ?: "127.0.0.1"
                handlePlayerJoin(uuidStr, username, ipAddress)
            } catch (_: Exception) {
                // ignore
            }
        }

        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                for (player in Bukkit.getOnlinePlayers()) {
                    val uuid = player.uniqueId

                    val pings = playerPings[uuid]
                    if (pings != null) {
                        pings.add(player.ping)
                    }

                    val punchcard = playerPunchcards[uuid]
                    if (punchcard != null) {
                        val zoneId = getPlayerZoneId(uuid)
                        val nowLocal = ZonedDateTime.now(zoneId)
                        val dayOfWeek = nowLocal.dayOfWeek.value - 1
                        val hourOfDay = nowLocal.hour

                        val minuteKey = "$dayOfWeek-$hourOfDay-${nowLocal.minute}"
                        val lastKey = playerLastPunchcardMinutes[uuid]
                        if (minuteKey != lastKey) {
                            punchcard[dayOfWeek][hourOfDay]++
                            playerLastPunchcardMinutes[uuid] = minuteKey
                        }
                    }
                }
            },
            0L,
            200L
        )
    }

    fun getPlayerZoneId(uuid: UUID): ZoneId {
        val tz = playerTimezones[uuid]
        if (tz != null) {
            try {
                return ZoneId.of(tz)
            } catch (_: Exception) {
                // fall back
            }
        }
        return ZoneId.systemDefault()
    }

    fun getAndRemovePunchcard(uuid: UUID): Array<IntArray>? = playerPunchcards.remove(uuid)

    fun getActiveSession(uuid: UUID): Optional<PlayerSessionRecord> {
        val loginMs = playerLoginTimes[uuid] ?: return Optional.empty()
        val username = playerUsernames[uuid] ?: "Unknown"
        val ipAddress = playerIpAddresses[uuid] ?: "127.0.0.1"
        val countryCode = playerCountryCodes[uuid] ?: "Unknown"

        val now = System.currentTimeMillis()
        val playtimeMs = now - loginMs

        val pings = playerPings[uuid]
        var avgPing = 0
        if (pings != null && pings.isNotEmpty()) {
            var sum = 0L
            synchronized(pings) {
                for (p in pings) {
                    sum += p.toLong()
                }
            }
            avgPing = (sum / pings.size).toInt()
        }

        val worldPlaytimes = HashMap<String, Long>()
        val cachedWorlds = playerWorldPlaytimes[uuid]
        if (cachedWorlds != null) {
            worldPlaytimes.putAll(cachedWorlds)
        }

        val currentWorld = playerCurrentWorlds[uuid]
        if (currentWorld != null) {
            val worldStartTime = playerWorldStartTimes[uuid]
            if (worldStartTime != null) {
                val duration = now - worldStartTime
                worldPlaytimes[currentWorld] = (worldPlaytimes[currentWorld] ?: 0L) + duration
            }
        }

        val sessionId = "$uuid-$loginMs"
        val record = PlayerSessionRecord(
            sessionId,
            uuid.toString(),
            username,
            ipAddress,
            countryCode,
            loginMs,
            0L,
            playtimeMs,
            avgPing,
            worldPlaytimes
        )
        return Optional.of(record)
    }

    override fun collectServerMetrics(): CompletableFuture<ServerMetricsRecord> {
        throw UnsupportedOperationException("PlayerMetricCollector does not handle server metrics")
    }

    override fun handlePlayerJoin(uuidStr: String, username: String, ipAddress: String) {
        val uuid = UUID.fromString(uuidStr)
        val now = System.currentTimeMillis()

        playerLoginTimes[uuid] = now
        playerIpAddresses[uuid] = ipAddress
        playerUsernames[uuid] = username
        playerWorldPlaytimes[uuid] = ConcurrentHashMap()
        playerWorldStartTimes[uuid] = now
        playerPings[uuid] = Collections.synchronizedList(ArrayList())
        playerPunchcards[uuid] = Array(7) { IntArray(24) }

        val player = Bukkit.getPlayer(uuid)
        if (player != null) {
            playerCurrentWorlds[uuid] = player.world.name
        } else {
            playerCurrentWorlds[uuid] = "world"
        }

        CompletableFuture.runAsync {
            var countryCode = "Unknown"
            var timezone = "UTC"
            if (databaseReader != null) {
                try {
                    val ip = InetAddress.getByName(ipAddress)
                    if (!ip.isLoopbackAddress && !ip.isSiteLocalAddress) {
                        val response: CityResponse? = databaseReader!!.city(ip)
                        if (response != null) {
                            response.country?.isoCode?.let { countryCode = it }
                            response.location?.timeZone?.let { timezone = it }
                        }
                    } else {
                        countryCode = "Local"
                    }
                } catch (_: Exception) {
                    // fall back
                }
            }
            playerCountryCodes[uuid] = countryCode
            playerTimezones[uuid] = timezone
        }
    }

    override fun handlePlayerQuit(uuidStr: String): CompletableFuture<PlayerSessionRecord> {
        val uuid = UUID.fromString(uuidStr)
        val logoutMs = System.currentTimeMillis()
        val future = CompletableFuture<PlayerSessionRecord>()

        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                try {
                    val player = Bukkit.getPlayer(uuid)
                    if (player != null) {
                        val currentWorld = player.world.name
                        val worldStartTime = playerWorldStartTimes[uuid]
                        if (worldStartTime != null) {
                            val duration = logoutMs - worldStartTime
                            val worldPlaytimes = playerWorldPlaytimes[uuid]
                            if (worldPlaytimes != null) {
                                worldPlaytimes[currentWorld] =
                                    (worldPlaytimes[currentWorld] ?: 0L) + duration
                            }
                        }
                    }

                    CompletableFuture.runAsync {
                        try {
                            val loginMs = playerLoginTimes.remove(uuid) ?: logoutMs
                            var finalUsername = playerUsernames.remove(uuid)
                            if (finalUsername == null) {
                                finalUsername = player?.name ?: "Unknown"
                            }
                            var finalIpAddress = playerIpAddresses.remove(uuid)
                            if (finalIpAddress == null) {
                                finalIpAddress =
                                    player?.address?.address?.hostAddress ?: "0.0.0.0"
                            }
                            var countryCode = playerCountryCodes.remove(uuid)
                            if (countryCode == null) {
                                countryCode = "Unknown"
                            }
                            playerTimezones.remove(uuid)

                            val playtimeMs = logoutMs - loginMs

                            val pings = playerPings.remove(uuid)
                            var avgPing = 0
                            if (pings != null && pings.isNotEmpty()) {
                                var sum = 0L
                                synchronized(pings) {
                                    for (p in pings) {
                                        sum += p.toLong()
                                    }
                                }
                                avgPing = (sum / pings.size).toInt()
                            } else if (player != null) {
                                avgPing = player.ping
                            }

                            var worldPlaytimes = playerWorldPlaytimes.remove(uuid)
                            if (worldPlaytimes == null) {
                                worldPlaytimes = HashMap()
                            }

                            playerWorldStartTimes.remove(uuid)
                            playerLastPunchcardMinutes.remove(uuid)
                            playerPunchcards.remove(uuid)
                            playerCurrentWorlds.remove(uuid)

                            val sessionId = "$uuidStr-$loginMs"
                            val record = PlayerSessionRecord(
                                sessionId,
                                uuidStr,
                                finalUsername,
                                finalIpAddress,
                                countryCode,
                                loginMs,
                                logoutMs,
                                playtimeMs,
                                avgPing,
                                worldPlaytimes
                            )
                            future.complete(record)
                        } catch (e: Exception) {
                            future.completeExceptionally(e)
                        }
                    }
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        )

        return future
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val username = player.name
        val ipAddress = player.address?.address?.hostAddress ?: "127.0.0.1"
        handlePlayerJoin(uuid, username, ipAddress)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuidStr = player.uniqueId.toString()
        handlePlayerQuit(uuidStr).thenAccept { record ->
            plugin.getMetricsBufferManager()?.submitPlayerSession(record)
        }.exceptionally { ex ->
            plugin.logger.severe("Failed to process player quit session for ${player.name}: ${ex.message}")
            null
        }
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val previousWorldName = event.from.name
        val newWorldName = player.world.name

        val now = System.currentTimeMillis()
        playerCurrentWorlds[uuid] = newWorldName
        val lastWorldChangeTime = playerWorldStartTimes[uuid]
        if (lastWorldChangeTime != null) {
            val timeSpent = now - lastWorldChangeTime
            val worldPlaytimes = playerWorldPlaytimes[uuid]
            if (worldPlaytimes != null) {
                worldPlaytimes[previousWorldName] =
                    (worldPlaytimes[previousWorldName] ?: 0L) + timeSpent
            }
        }
        playerWorldStartTimes[uuid] = now
    }
}
