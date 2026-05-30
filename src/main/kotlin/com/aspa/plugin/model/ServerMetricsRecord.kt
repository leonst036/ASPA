package com.aspa.plugin.model

class ServerMetricsRecord() {
    var timestamp: Long = 0
    var tps: Double = 0.0
    var mspt: Double = 0.0
    var cpuUsage: Double = 0.0
    var ramUsedMb: Long = 0
    var ramMaxMb: Long = 0
    var onlinePlayers: Int = 0
    var loadedChunks: Int = 0
    var entityCounts: Map<String, Int>? = null
    var gcCountDelta: Long = 0
    var gcTimeDeltaMs: Long = 0
    var avgPing: Double = 0.0
    var maxPing: Double = 0.0
    var chunksPerWorld: Map<String, Int>? = null
    var entitiesPerWorld: Map<String, Int>? = null

    constructor(
        timestamp: Long,
        tps: Double,
        mspt: Double,
        cpuUsage: Double,
        ramUsedMb: Long,
        ramMaxMb: Long,
        onlinePlayers: Int,
        loadedChunks: Int,
        entityCounts: Map<String, Int>
    ) : this() {
        this.timestamp = timestamp
        this.tps = tps
        this.mspt = mspt
        this.cpuUsage = cpuUsage
        this.ramUsedMb = ramUsedMb
        this.ramMaxMb = ramMaxMb
        this.onlinePlayers = onlinePlayers
        this.loadedChunks = loadedChunks
        this.entityCounts = entityCounts
    }

    constructor(
        timestamp: Long,
        tps: Double,
        mspt: Double,
        cpuUsage: Double,
        ramUsedMb: Long,
        ramMaxMb: Long,
        onlinePlayers: Int,
        loadedChunks: Int,
        entityCounts: Map<String, Int>,
        gcCountDelta: Long,
        gcTimeDeltaMs: Long,
        avgPing: Double,
        maxPing: Double,
        chunksPerWorld: Map<String, Int>,
        entitiesPerWorld: Map<String, Int>
    ) : this() {
        this.timestamp = timestamp
        this.tps = tps
        this.mspt = mspt
        this.cpuUsage = cpuUsage
        this.ramUsedMb = ramUsedMb
        this.ramMaxMb = ramMaxMb
        this.onlinePlayers = onlinePlayers
        this.loadedChunks = loadedChunks
        this.entityCounts = entityCounts
        this.gcCountDelta = gcCountDelta
        this.gcTimeDeltaMs = gcTimeDeltaMs
        this.avgPing = avgPing
        this.maxPing = maxPing
        this.chunksPerWorld = chunksPerWorld
        this.entitiesPerWorld = entitiesPerWorld
    }
}
