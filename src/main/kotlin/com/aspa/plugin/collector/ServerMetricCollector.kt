package com.aspa.plugin.collector

import com.aspa.plugin.ASPA
import com.aspa.plugin.api.MetricCollector
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.entity.Entity
import java.lang.management.ManagementFactory
import java.util.HashMap
import java.util.concurrent.CompletableFuture

class ServerMetricCollector(private val plugin: ASPA) : MetricCollector {
    private val tickIntervals = LongArray(100)
    private var tickCount = 0
    private var lastTickTime: Long = 0

    private val nanoIntervals = LongArray(20)
    private var nanoCount = 0
    private var lastTickNano: Long = 0

    fun start() {
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                val now = System.currentTimeMillis()
                if (lastTickTime != 0L) {
                    val delta = now - lastTickTime
                    tickIntervals[tickCount % tickIntervals.size] = delta
                    tickCount++
                }
                lastTickTime = now

                val nowNano = System.nanoTime()
                if (lastTickNano != 0L) {
                    val deltaNano = nowNano - lastTickNano
                    nanoIntervals[nanoCount % nanoIntervals.size] = deltaNano
                    nanoCount++
                }
                lastTickNano = nowNano
            },
            0L,
            1L
        )
    }

    fun getAverageTps(): Double {
        if (tickCount == 0) {
            return 20.0
        }
        val count = minOf(tickCount, tickIntervals.size)
        var sum = 0L
        for (i in 0 until count) {
            sum += tickIntervals[i]
        }
        if (sum == 0L) {
            return 20.0
        }
        val avgIntervalMs = sum.toDouble() / count
        val tps = 1000.0 / avgIntervalMs
        return minOf(20.0, tps)
    }

    fun getAverageMspt(): Double {
        if (nanoCount == 0) {
            return 50.0
        }
        val count = minOf(nanoCount, nanoIntervals.size)
        var sum = 0L
        for (i in 0 until count) {
            sum += nanoIntervals[i]
        }
        if (sum == 0L) {
            return 50.0
        }
        val avgIntervalNano = sum.toDouble() / count
        return avgIntervalNano / 1_000_000.0
    }

    private var lastGcCount: Long = -1
    private var lastGcTimeMs: Long = -1

    @Synchronized
    private fun getGcStatsDelta(): LongArray {
        var totalGcCount = 0L
        var totalGcTimeMs = 0L
        for (gcBean in ManagementFactory.getGarbageCollectorMXBeans()) {
            val count = gcBean.collectionCount
            val time = gcBean.collectionTime
            if (count != -1L) totalGcCount += count
            if (time != -1L) totalGcTimeMs += time
        }

        var deltaCount = 0L
        var deltaTimeMs = 0L

        if (lastGcCount != -1L) {
            deltaCount = maxOf(0L, totalGcCount - lastGcCount)
        }
        if (lastGcTimeMs != -1L) {
            deltaTimeMs = maxOf(0L, totalGcTimeMs - lastGcTimeMs)
        }

        lastGcCount = totalGcCount
        lastGcTimeMs = totalGcTimeMs

        return longArrayOf(deltaCount, deltaTimeMs)
    }

    override fun collectServerMetrics(): CompletableFuture<ServerMetricsRecord> {
        val future = CompletableFuture<ServerMetricsRecord>()

        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                try {
                    val onlinePlayers = Bukkit.getOnlinePlayers().size
                    var loadedChunks = 0
                    var monsters = 0
                    var animals = 0
                    var tileEntities = 0

                    var totalPing = 0
                    var maxPingVal = 0
                    var pingCount = 0
                    for (player in Bukkit.getOnlinePlayers()) {
                        val p = player.ping
                        totalPing += p
                        if (p > maxPingVal) {
                            maxPingVal = p
                        }
                        pingCount++
                    }
                    val avgPingVal = if (pingCount > 0) totalPing.toDouble() / pingCount else 0.0
                    val maxPingValFinal = maxPingVal

                    val chunksPerWorld = HashMap<String, Int>()
                    val entitiesPerWorld = HashMap<String, Int>()

                    for (world in Bukkit.getWorlds()) {
                        val worldName = world.name
                        val worldChunks = world.loadedChunks.size
                        val worldEntities = world.entities.size

                        chunksPerWorld[worldName] = worldChunks
                        entitiesPerWorld[worldName] = worldEntities

                        loadedChunks += worldChunks
                        for (entity in world.entities) {
                            when (entity) {
                                is org.bukkit.entity.Monster -> monsters++
                                is org.bukkit.entity.Animals -> animals++
                            }
                        }
                        for (chunk in world.loadedChunks) {
                            tileEntities += chunk.tileEntities.size
                        }
                    }

                    val finalOnlinePlayers = onlinePlayers
                    val finalLoadedChunks = loadedChunks
                    val finalMonsters = monsters
                    val finalAnimals = animals
                    val finalTileEntities = tileEntities

                    CompletableFuture.runAsync {
                        try {
                            val timestamp = System.currentTimeMillis()
                            val currentTps = getAverageTps()
                            val currentMspt = getAverageMspt()

                            val osBean = ManagementFactory.getPlatformMXBean(
                                com.sun.management.OperatingSystemMXBean::class.java
                            )
                            var cpu = osBean.processCpuLoad
                            if (cpu < 0) {
                                cpu = osBean.systemCpuLoad
                            }
                            if (cpu < 0) {
                                cpu = 0.0
                            }
                            val cpuUsage = cpu * 100.0

                            val maxMemory = Runtime.getRuntime().maxMemory()
                            val totalMemory = Runtime.getRuntime().totalMemory()
                            val freeMemory = Runtime.getRuntime().freeMemory()
                            val usedMemory = totalMemory - freeMemory
                            val ramUsedMb = usedMemory / (1024 * 1024)
                            val ramMaxMb = maxMemory / (1024 * 1024)

                            val entityCounts = HashMap<String, Int>()
                            entityCounts["monsters"] = finalMonsters
                            entityCounts["animals"] = finalAnimals
                            entityCounts["tileEntities"] = finalTileEntities

                            val gcStats = getGcStatsDelta()
                            val gcCountDelta = gcStats[0]
                            val gcTimeDeltaMs = gcStats[1]

                            val record = ServerMetricsRecord(
                                timestamp,
                                currentTps,
                                currentMspt,
                                cpuUsage,
                                ramUsedMb,
                                ramMaxMb,
                                finalOnlinePlayers,
                                finalLoadedChunks,
                                entityCounts,
                                gcCountDelta,
                                gcTimeDeltaMs,
                                avgPingVal,
                                maxPingValFinal.toDouble(),
                                chunksPerWorld,
                                entitiesPerWorld
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

    override fun handlePlayerJoin(uuid: String, username: String, ipAddress: String) {
        throw UnsupportedOperationException("ServerMetricCollector does not handle player metrics")
    }

    override fun handlePlayerQuit(uuid: String): CompletableFuture<PlayerSessionRecord> {
        throw UnsupportedOperationException("ServerMetricCollector does not handle player metrics")
    }
}
