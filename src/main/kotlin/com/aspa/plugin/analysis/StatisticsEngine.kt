package com.aspa.plugin.analysis

import com.aspa.plugin.api.AnalysisEngine
import com.aspa.plugin.model.ForecastResult
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.RetentionReport
import com.aspa.plugin.model.ServerMetricsRecord
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class StatisticsEngine : AnalysisEngine {
    override fun detectAnomalies(history: List<ServerMetricsRecord>): CompletableFuture<List<PerformanceAnomaly>> {
        return CompletableFuture.supplyAsync {
            val anomalies = ArrayList<PerformanceAnomaly>()
            if (history.isEmpty()) {
                return@supplyAsync anomalies
            }

            val zScoreThreshold = getAnomalyZScoreThreshold()

            var totalVelocity = 0.0
            var totalEntityCount = 0.0
            var totalPlayerCount = 0.0
            var totalRamUsed = 0.0
            val count = history.size

            for (j in 0 until count) {
                val r = history[j]
                val vel = if (j > 0) (r.loadedChunks - history[j - 1].loadedChunks).toDouble() else 0.0
                totalVelocity += vel

                var ent = 0.0
                r.entityCounts?.values?.forEach { ent += it }
                totalEntityCount += ent
                totalPlayerCount += r.onlinePlayers
                totalRamUsed += r.ramUsedMb
            }

            val baselineVelocity = if (count > 0) totalVelocity / count else 0.0
            val baselineEntityCount = if (count > 0) totalEntityCount / count else 0.0
            val baselinePlayerCount = if (count > 0) totalPlayerCount / count else 0.0
            val baselineRamUsed = if (count > 0) totalRamUsed / count else 0.0

            for (i in 0 until count) {
                val record = history[i]

                val startWin = max(0, i - 59)
                val winSize = i - startWin + 1
                var winSum = 0.0
                for (k in startWin..i) {
                    winSum += history[k].mspt
                }
                val rollingMean = winSum / winSize

                var winVarianceSum = 0.0
                for (k in startWin..i) {
                    val diff = history[k].mspt - rollingMean
                    winVarianceSum += diff * diff
                }
                val rollingStdDev = sqrt(winVarianceSum / winSize)
                val thresholdStdDev = max(0.1, rollingStdDev)

                val isAnomaly =
                    (record.tps < 19.0 && record.mspt > rollingMean + (zScoreThreshold * thresholdStdDev)) ||
                        (record.tps < 18.0)

                if (isAnomaly) {
                    val corrStart = max(0, i - 14)
                    val corrSize = i - corrStart + 1
                    val msptWin = DoubleArray(corrSize)
                    val velWin = DoubleArray(corrSize)
                    val entWin = DoubleArray(corrSize)
                    val playWin = DoubleArray(corrSize)
                    val ramWin = DoubleArray(corrSize)

                    for (k in corrStart..i) {
                        val idx = k - corrStart
                        val r = history[k]
                        msptWin[idx] = r.mspt
                        velWin[idx] = if (k > 0) (r.loadedChunks - history[k - 1].loadedChunks).toDouble() else 0.0

                        var ent = 0.0
                        r.entityCounts?.values?.forEach { ent += it }
                        entWin[idx] = ent
                        playWin[idx] = r.onlinePlayers.toDouble()
                        ramWin[idx] = r.ramUsedMb.toDouble()
                    }

                    val corrVel = computePearsonCorrelation(msptWin, velWin)
                    val corrEnt = computePearsonCorrelation(msptWin, entWin)
                    val corrPlay = computePearsonCorrelation(msptWin, playWin)
                    val corrRam = computePearsonCorrelation(msptWin, ramWin)

                    val strengthVel = min(1.0, max(0.0, abs(corrVel)))
                    val strengthEnt = min(1.0, max(0.0, abs(corrEnt)))
                    val strengthPlay = min(1.0, max(0.0, abs(corrPlay)))
                    val strengthRam = min(1.0, max(0.0, abs(corrRam)))

                    val currentVel =
                        if (i > 0) (record.loadedChunks - history[i - 1].loadedChunks).toDouble() else 0.0
                    var currentEnt = 0.0
                    record.entityCounts?.values?.forEach { currentEnt += it }
                    val currentPlay = record.onlinePlayers.toDouble()
                    val currentRam = record.ramUsedMb.toDouble()

                    val factors = ArrayList<PerformanceAnomaly.CorrelatedFactor>()

                    val velDesc =
                        String.format(
                            "Chunk load velocity: %.1f chunks/interval (Baseline: %.1f, Correlation: %.2f)",
                            currentVel,
                            baselineVelocity,
                            strengthVel
                        )
                    factors.add(
                        PerformanceAnomaly.CorrelatedFactor(
                            "CHUNK_LOAD_VELOCITY",
                            currentVel,
                            strengthVel,
                            velDesc
                        )
                    )

                    val entDesc =
                        String.format(
                            "Entity count: %.0f entities (Baseline: %.1f, Correlation: %.2f)",
                            currentEnt,
                            baselineEntityCount,
                            strengthEnt
                        )
                    factors.add(
                        PerformanceAnomaly.CorrelatedFactor(
                            "ENTITY_COUNT",
                            currentEnt,
                            strengthEnt,
                            entDesc
                        )
                    )

                    val playDesc =
                        String.format(
                            "Player count: %.0f players (Baseline: %.1f, Correlation: %.2f)",
                            currentPlay,
                            baselinePlayerCount,
                            strengthPlay
                        )
                    factors.add(
                        PerformanceAnomaly.CorrelatedFactor(
                            "PLAYER_COUNT",
                            currentPlay,
                            strengthPlay,
                            playDesc
                        )
                    )

                    val ramDesc =
                        String.format(
                            "RAM usage: %.1f MB (Baseline: %.1f MB, Correlation: %.2f)",
                            currentRam,
                            baselineRamUsed,
                            strengthRam
                        )
                    factors.add(
                        PerformanceAnomaly.CorrelatedFactor(
                            "RAM_SPIKE",
                            currentRam,
                            strengthRam,
                            ramDesc
                        )
                    )

                    var severity = "LOW"
                    if (record.tps < 12.0 || record.mspt > rollingMean + 5 * thresholdStdDev) {
                        severity = "CRITICAL"
                    } else if (record.tps < 15.0 || record.mspt > rollingMean + 4 * thresholdStdDev) {
                        severity = "HIGH"
                    } else if (record.tps < 17.0 || record.mspt > rollingMean + 3 * thresholdStdDev) {
                        severity = "MEDIUM"
                    }

                    anomalies.add(
                        PerformanceAnomaly(
                            record.timestamp,
                            severity,
                            record.tps,
                            record.mspt,
                            factors
                        )
                    )
                }
            }

            anomalies
        }
    }

    override fun forecastActivity(history: List<PlayerSessionRecord>): CompletableFuture<ForecastResult> {
        return CompletableFuture.supplyAsync {
            if (history.isEmpty()) {
                return@supplyAsync ForecastResult(System.currentTimeMillis() + 86400000L, 0, 0.5, 0.0)
            }

            var minTimestamp = Long.MAX_VALUE
            for (s in history) {
                if (s.loginMs < minTimestamp) minTimestamp = s.loginMs
            }

            val minDt = Instant.ofEpochMilli(minTimestamp).atZone(ZoneOffset.UTC)
            val alignedMinDt = minDt
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            val baseWeekMs = alignedMinDt.toInstant().toEpochMilli()

            val weekHourlyCounts = HashMap<Int, IntArray>()

            for (s in history) {
                var login = s.loginMs
                var logout = s.logoutMs
                if (logout <= 0) logout = System.currentTimeMillis()
                if (logout < login) logout = login

                val startHour = (login / 3600000L) * 3600000L
                val endHour = (logout / 3600000L) * 3600000L

                var hourMs = startHour
                while (hourMs <= endHour) {
                    if (hourMs >= baseWeekMs) {
                        val weekIndex = ((hourMs - baseWeekMs) / (7 * 24 * 3600000L)).toInt()
                        val weekHour = (((hourMs - baseWeekMs) % (7 * 24 * 3600000L)) / 3600000L).toInt()
                        if (weekHour in 0..167) {
                            val arr = weekHourlyCounts.getOrPut(weekIndex) { IntArray(168) }
                            arr[weekHour]++
                        }
                    }
                    hourMs += 3600000L
                }
            }

            var peakWeekHour = 0
            var maxAvg = -1.0
            val numWeeks = weekHourlyCounts.size

            for (weekHour in 0 until 168) {
                var sum = 0.0
                for (hours in weekHourlyCounts.values) {
                    sum += hours[weekHour].toDouble()
                }
                val avg = sum / if (numWeeks > 0) numWeeks else 1
                if (avg > maxAvg) {
                    maxAvg = avg
                    peakWeekHour = weekHour
                }
            }

            val weekIndices = ArrayList(weekHourlyCounts.keys)
            Collections.sort(weekIndices)

            val m = weekIndices.size
            var predictedPlayerCount: Int
            var confidenceInterval = 0.8
            var growthTrend = 0.0

            if (m >= 2) {
                var sumX = 0.0
                var sumY = 0.0
                for (wIdx in weekIndices) {
                    sumX += wIdx
                    sumY += weekHourlyCounts[wIdx]!![peakWeekHour].toDouble()
                }
                val meanX = sumX / m
                val meanY = sumY / m

                var numReg = 0.0
                var denReg = 0.0
                for (wIdx in weekIndices) {
                    val diffX = wIdx - meanX
                    val diffY = weekHourlyCounts[wIdx]!![peakWeekHour] - meanY
                    numReg += diffX * diffY
                    denReg += diffX * diffX
                }

                val b = if (denReg != 0.0) numReg / denReg else 0.0
                val a = meanY - b * meanX

                val maxWeekIndex = weekIndices[m - 1]
                val nextWeekIndex = maxWeekIndex + 1
                predictedPlayerCount = Math.round(a + b * nextWeekIndex).toInt()
                if (predictedPlayerCount < 0) {
                    predictedPlayerCount = 0
                }
                growthTrend = b

                var ssTot = 0.0
                var ssRes = 0.0
                for (wIdx in weekIndices) {
                    val yVal = weekHourlyCounts[wIdx]!![peakWeekHour].toDouble()
                    val yPred = a + b * wIdx
                    ssTot += (yVal - meanY) * (yVal - meanY)
                    ssRes += (yVal - yPred) * (yVal - yPred)
                }
                val r2 = if (ssTot > 0) (1.0 - (ssRes / ssTot)) else 1.0
                confidenceInterval = min(1.0, max(0.1, r2))
            } else {
                predictedPlayerCount = Math.round(maxAvg).toInt()
                growthTrend = 0.0
                confidenceInterval = 0.75
            }

            val nowMs = System.currentTimeMillis()
            val nowDt = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC)
            val currentWeekBase = nowDt
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            val currentWeekBaseMs = currentWeekBase.toInstant().toEpochMilli()

            var peakTimeThisWeekMs = currentWeekBaseMs + peakWeekHour * 3600000L
            if (peakTimeThisWeekMs <= nowMs) {
                peakTimeThisWeekMs += 7 * 24 * 3600000L
            }

            ForecastResult(peakTimeThisWeekMs, predictedPlayerCount, confidenceInterval, growthTrend)
        }
    }

    override fun calculateRetentionMetrics(history: List<PlayerSessionRecord>): CompletableFuture<RetentionReport> {
        return CompletableFuture.supplyAsync {
            if (history.isEmpty()) {
                return@supplyAsync RetentionReport(0, 0, 0.0, 0.0, 0L, ArrayList(), Array(7) { IntArray(24) }, 0)
            }

            val playerSessions = HashMap<String, MutableList<PlayerSessionRecord>>()
            for (s in history) {
                playerSessions.computeIfAbsent(s.uuid ?: "") { ArrayList() }.add(s)
            }

            var newPlayers = 0
            var returningPlayers = 0
            for (entry in playerSessions.entries) {
                val sessions = entry.value
                if (sessions.size <= 1) {
                    newPlayers++
                } else {
                    var minDay = Long.MAX_VALUE
                    var maxDay = Long.MIN_VALUE
                    for (s in sessions) {
                        val d =
                            Instant.ofEpochMilli(s.loginMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        if (d < minDay) minDay = d
                        if (d > maxDay) maxDay = d
                    }
                    if (maxDay > minDay) {
                        returningPlayers++
                    } else {
                        newPlayers++
                    }
                }
            }

            val dailyActivePlayers = HashMap<Long, MutableSet<String>>()
            for (s in history) {
                var logout = s.logoutMs
                if (logout <= 0) logout = System.currentTimeMillis()
                if (logout < s.loginMs) logout = s.loginMs

                val startDay =
                    Instant.ofEpochMilli(s.loginMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                val endDay = Instant.ofEpochMilli(logout).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                for (day in startDay..endDay) {
                    dailyActivePlayers.computeIfAbsent(day) { HashSet() }.add(s.uuid ?: "")
                }
            }

            var minDay = Long.MAX_VALUE
            var maxDay = Long.MIN_VALUE
            for (day in dailyActivePlayers.keys) {
                if (day < minDay) minDay = day
                if (day > maxDay) maxDay = day
            }

            var retentionRateD1 = 0.0
            var retentionRateW1 = 0.0

            if (dailyActivePlayers.isNotEmpty()) {
                var d1Numerator = 0L
                var d1Denominator = 0L
                for (d in minDay..(maxDay - 1)) {
                    val activeD = dailyActivePlayers[d] ?: emptySet()
                    if (activeD.isEmpty()) continue
                    val activeNext = dailyActivePlayers[d + 1] ?: emptySet()
                    val retained = activeD.count { activeNext.contains(it) }.toLong()
                    d1Numerator += retained
                    d1Denominator += activeD.size.toLong()
                }
                retentionRateD1 = if (d1Denominator > 0) d1Numerator.toDouble() / d1Denominator else 0.0

                var d7Numerator = 0L
                var d7Denominator = 0L
                for (d in minDay..(maxDay - 7)) {
                    val activeD = dailyActivePlayers[d] ?: emptySet()
                    if (activeD.isEmpty()) continue
                    val activeNext = dailyActivePlayers[d + 7] ?: emptySet()
                    val retained = activeD.count { activeNext.contains(it) }.toLong()
                    d7Numerator += retained
                    d7Denominator += activeD.size.toLong()
                }
                retentionRateW1 = if (d7Denominator > 0) d7Numerator.toDouble() / d7Denominator else 0.0
            }

            var totalPlaytime = 0L
            for (s in history) {
                var playtime = s.playtimeMs
                if (playtime <= 0) {
                    var logout = s.logoutMs
                    if (logout <= 0) logout = System.currentTimeMillis()
                    if (logout >= s.loginMs) {
                        playtime = logout - s.loginMs
                    }
                }
                totalPlaytime += playtime
            }
            val averagePlaytimeMs = if (history.isEmpty()) 0L else totalPlaytime / history.size

            val countryPlayers = HashMap<String, MutableSet<String>>()
            for (s in history) {
                var code = s.countryCode
                code =
                    if (code == null || code.trim().isEmpty()) "UNKNOWN" else code.trim().uppercase()
                countryPlayers.computeIfAbsent(code) { HashSet() }.add(s.uuid ?: "")
            }

            val countryNames = HashMap<String, String>()
            countryNames["US"] = "United States"
            countryNames["CA"] = "Canada"
            countryNames["GB"] = "United Kingdom"
            countryNames["DE"] = "Germany"
            countryNames["FR"] = "France"
            countryNames["AU"] = "Australia"
            countryNames["BR"] = "Brazil"
            countryNames["JP"] = "Japan"
            countryNames["CN"] = "China"
            countryNames["RU"] = "Russia"
            countryNames["IN"] = "India"
            countryNames["ZA"] = "South Africa"
            countryNames["NL"] = "Netherlands"
            countryNames["SE"] = "Sweden"
            countryNames["NO"] = "Norway"
            countryNames["DK"] = "Denmark"
            countryNames["FI"] = "Finland"
            countryNames["PL"] = "Poland"
            countryNames["ES"] = "Spain"
            countryNames["IT"] = "Italy"
            countryNames["UNKNOWN"] = "Unknown Country"

            val geographicDistribution = ArrayList<RetentionReport.CountryDistribution>()
            for (entry in countryPlayers.entries) {
                val code = entry.key
                val name = countryNames[code] ?: code
                val count = entry.value.size
                geographicDistribution.add(RetentionReport.CountryDistribution(code, name, count))
            }
            geographicDistribution.sortWith { a, b -> b.count.compareTo(a.count) }

            val punchcardMatrix = Array(7) { IntArray(24) }
            for (s in history) {
                var logout = s.logoutMs
                if (logout <= 0) logout = System.currentTimeMillis()
                if (logout < s.loginMs) logout = s.loginMs

                var hourMs = (s.loginMs / 3600000L) * 3600000L
                val endHour = (logout / 3600000L) * 3600000L
                while (hourMs <= endHour) {
                    val dt = Instant.ofEpochMilli(hourMs).atZone(ZoneOffset.UTC)
                    val day = dt.dayOfWeek.value - 1
                    val hour = dt.hour
                    punchcardMatrix[day][hour]++
                    hourMs += 3600000L
                }
            }

            var totalPing = 0
            var pingCount = 0
            for (s in history) {
                if (s.averagePing > 0) {
                    totalPing += s.averagePing
                    pingCount++
                }
            }
            val averagePing = if (pingCount > 0) totalPing / pingCount else 0

            RetentionReport(
                newPlayers,
                returningPlayers,
                retentionRateD1,
                retentionRateW1,
                averagePlaytimeMs,
                geographicDistribution,
                punchcardMatrix,
                averagePing
            )
        }
    }

    private fun getAnomalyZScoreThreshold(): Double {
        val threshold = 2.5
        val filesToTry = Arrays.asList(
            File("plugins/ASPA/config.yml"),
            File("config.yml"),
            File("src/main/resources/config.yml")
        )
        for (file in filesToTry) {
            if (file.exists() && file.isFile) {
                try {
                    FileInputStream(file).use { `is` ->
                        val value = parseZScoreFromStream(`is`)
                        if (value > 0) return value
                    }
                } catch (_: Exception) {
                    // ignore and try next
                }
            }
        }
        try {
            StatisticsEngine::class.java.getResourceAsStream("/config.yml")?.use { `is` ->
                val value = parseZScoreFromStream(`is`)
                if (value > 0) return value
            }
        } catch (_: Exception) {
            // ignore
        }
        return threshold
    }

    private fun parseZScoreFromStream(`is`: InputStream): Double {
        try {
            BufferedReader(InputStreamReader(`is`)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("anomaly-z-score-threshold:")) {
                        val parts = trimmed.split(":".toRegex(), limit = 2).toTypedArray()
                        if (parts.size > 1) {
                            return parts[1].trim().toDouble()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        return -1.0
    }

    private fun computePearsonCorrelation(x: DoubleArray, y: DoubleArray): Double {
        if (x.size != y.size || x.size < 2) {
            return 0.0
        }
        val n = x.size
        var sumX = 0.0
        var sumY = 0.0
        for (j in 0 until n) {
            sumX += x[j]
            sumY += y[j]
        }
        val meanX = sumX / n
        val meanY = sumY / n

        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (j in 0 until n) {
            val diffX = x[j] - meanX
            val diffY = y[j] - meanY
            num += diffX * diffY
            denX += diffX * diffX
            denY += diffY * diffY
        }
        if (denX == 0.0 || denY == 0.0) {
            return 0.0
        }
        return num / sqrt(denX * denY)
    }
}
