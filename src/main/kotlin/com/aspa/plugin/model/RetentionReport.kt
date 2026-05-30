package com.aspa.plugin.model

class RetentionReport() {
    var newPlayers: Int = 0
    var returningPlayers: Int = 0
    var retentionRateD1: Double = 0.0
    var retentionRateW1: Double = 0.0
    var averagePlaytimeMs: Long = 0
    var geographicDistribution: List<CountryDistribution>? = null
    var punchcardMatrix: Array<IntArray>? = null
    var averagePing: Int = 0

    constructor(
        newPlayers: Int,
        returningPlayers: Int,
        retentionRateD1: Double,
        retentionRateW1: Double,
        averagePlaytimeMs: Long,
        geographicDistribution: List<CountryDistribution>,
        punchcardMatrix: Array<IntArray>,
        averagePing: Int
    ) : this() {
        this.newPlayers = newPlayers
        this.returningPlayers = returningPlayers
        this.retentionRateD1 = retentionRateD1
        this.retentionRateW1 = retentionRateW1
        this.averagePlaytimeMs = averagePlaytimeMs
        this.geographicDistribution = geographicDistribution
        this.punchcardMatrix = punchcardMatrix
        this.averagePing = averagePing
    }

    class CountryDistribution() {
        var countryCode: String? = null
        var countryName: String? = null
        var count: Int = 0

        constructor(countryCode: String, countryName: String, count: Int) : this() {
            this.countryCode = countryCode
            this.countryName = countryName
            this.count = count
        }
    }
}
