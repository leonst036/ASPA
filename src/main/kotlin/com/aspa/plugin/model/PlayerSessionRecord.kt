package com.aspa.plugin.model

class PlayerSessionRecord() {
    var sessionId: String? = null
    var uuid: String? = null
    var username: String? = null
    var ipAddress: String? = null
    var countryCode: String? = null
    var loginMs: Long = 0
    var logoutMs: Long = 0
    var playtimeMs: Long = 0
    var averagePing: Int = 0
    var worldPlaytimes: Map<String, Long>? = null

    constructor(
        sessionId: String,
        uuid: String,
        username: String,
        ipAddress: String,
        countryCode: String,
        loginMs: Long,
        logoutMs: Long,
        playtimeMs: Long,
        averagePing: Int,
        worldPlaytimes: Map<String, Long>
    ) : this() {
        this.sessionId = sessionId
        this.uuid = uuid
        this.username = username
        this.ipAddress = ipAddress
        this.countryCode = countryCode
        this.loginMs = loginMs
        this.logoutMs = logoutMs
        this.playtimeMs = playtimeMs
        this.averagePing = averagePing
        this.worldPlaytimes = worldPlaytimes
    }
}
