package com.aspa.plugin.model

class PerformanceAnomaly() {
    var timestamp: Long = 0
    var severity: String? = null
    var tps: Double = 0.0
    var mspt: Double = 0.0
    var correlatedFactors: List<CorrelatedFactor>? = null

    constructor(
        timestamp: Long,
        severity: String,
        tps: Double,
        mspt: Double,
        correlatedFactors: List<CorrelatedFactor>
    ) : this() {
        this.timestamp = timestamp
        this.severity = severity
        this.tps = tps
        this.mspt = mspt
        this.correlatedFactors = correlatedFactors
    }

    class CorrelatedFactor() {
        var factor: String? = null
        var value: Double = 0.0
        var correlationStrength: Double = 0.0
        var description: String? = null

        constructor(
            factor: String,
            value: Double,
            correlationStrength: Double,
            description: String
        ) : this() {
            this.factor = factor
            this.value = value
            this.correlationStrength = correlationStrength
            this.description = description
        }
    }
}
