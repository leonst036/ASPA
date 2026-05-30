package com.aspa.plugin.model

class PlayerProfile() {
    var uuid: String? = null
    var username: String? = null
    var firstLoginMs: Long = 0
    var lastLoginMs: Long = 0
    var totalPlaytimeMs: Long = 0
    var averagePing: Int = 0
    var countryCode: String? = null
    var sessions: List<PlayerSessionRecord>? = null
    var activityPunchcard: Array<IntArray>? = null

    constructor(
        uuid: String,
        username: String,
        firstLoginMs: Long,
        lastLoginMs: Long,
        totalPlaytimeMs: Long,
        averagePing: Int,
        countryCode: String,
        sessions: List<PlayerSessionRecord>,
        activityPunchcard: Array<IntArray>
    ) : this() {
        this.uuid = uuid
        this.username = username
        this.firstLoginMs = firstLoginMs
        this.lastLoginMs = lastLoginMs
        this.totalPlaytimeMs = totalPlaytimeMs
        this.averagePing = averagePing
        this.countryCode = countryCode
        this.sessions = sessions
        this.activityPunchcard = activityPunchcard
    }
}
