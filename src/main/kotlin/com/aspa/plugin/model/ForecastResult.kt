package com.aspa.plugin.model

class ForecastResult() {
    var nextPeakTimeMs: Long = 0
    var predictedPlayerCount: Int = 0
    var confidenceInterval: Double = 0.0
    var growthTrend: Double = 0.0

    constructor(
        nextPeakTimeMs: Long,
        predictedPlayerCount: Int,
        confidenceInterval: Double,
        growthTrend: Double
    ) : this() {
        this.nextPeakTimeMs = nextPeakTimeMs
        this.predictedPlayerCount = predictedPlayerCount
        this.confidenceInterval = confidenceInterval
        this.growthTrend = growthTrend
    }
}
