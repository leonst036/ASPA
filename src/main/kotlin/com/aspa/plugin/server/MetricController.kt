package com.aspa.plugin.server

import com.aspa.plugin.ASPA
import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.api.MetricCollector
import io.javalin.Javalin
import io.javalin.http.Context
import java.util.ArrayList
import java.util.HashMap

class MetricController(
    private val databaseProvider: DatabaseProvider,
    private val metricCollector: MetricCollector
) {
    fun register(app: Javalin) {
        app.get("/api/v1/status") { getStatus(it) }
        app.get("/api/v1/metrics/history") { getHistory(it) }
        app.get("/api/v1/metrics/longtime") { getLongtime(it) }
    }

    private fun getStatus(ctx: Context) {
        if (!ctx.hasPermission("health")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
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

    private fun getHistory(ctx: Context) {
        if (!ctx.hasPermission("health")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
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

    private fun getLongtime(ctx: Context) {
        if (!ctx.hasPermission("longtime")) {
            ctx.status(403).json(ErrorResponse("Forbidden: Access denied"))
            return
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
}
