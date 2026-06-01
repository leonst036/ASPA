package com.aspa.plugin.database

import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import com.aspa.plugin.model.User
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.HashMap
import java.util.Optional
import java.util.TimeZone
import java.util.concurrent.CompletableFuture

class SQLiteProvider(private val dbFile: File) : DatabaseProvider {
    private val dbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dataSource: HikariDataSource? = null

    private fun getConnection(): Connection {
        val ds = dataSource ?: throw SQLException("DataSource not initialized")
        return ds.connection
    }

    @Throws(Exception::class)
    override fun initialize() {
        dbFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        config.driverClassName = "org.sqlite.JDBC"
        config.maximumPoolSize = 1
        config.connectionTimeout = 30000
        config.addDataSourceProperty("journal_mode", "WAL")
        dataSource = HikariDataSource(config)

        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS server_metrics (" +
                        "timestamp BIGINT PRIMARY KEY," +
                        "tps REAL," +
                        "mspt REAL," +
                        "cpu_usage REAL," +
                        "ram_used_mb BIGINT," +
                        "ram_max_mb BIGINT," +
                        "online_players INTEGER," +
                        "loaded_chunks INTEGER," +
                        "entity_counts TEXT" +
                        ");"
                )

                val migrations = arrayOf(
                    "ALTER TABLE server_metrics ADD COLUMN gc_count_delta BIGINT DEFAULT 0;",
                    "ALTER TABLE server_metrics ADD COLUMN gc_time_delta_ms BIGINT DEFAULT 0;",
                    "ALTER TABLE server_metrics ADD COLUMN avg_ping REAL DEFAULT 0.0;",
                    "ALTER TABLE server_metrics ADD COLUMN max_ping REAL DEFAULT 0.0;",
                    "ALTER TABLE server_metrics ADD COLUMN chunks_per_world TEXT DEFAULT '{}';",
                    "ALTER TABLE server_metrics ADD COLUMN entities_per_world TEXT DEFAULT '{}';"
                )
                for (migration in migrations) {
                    try {
                        stmt.execute(migration)
                    } catch (e: SQLException) {
                        // Column already exists, safe to ignore
                    }
                }

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player_sessions (" +
                        "session_id TEXT PRIMARY KEY," +
                        "uuid TEXT," +
                        "username TEXT," +
                        "ip_address TEXT," +
                        "country_code TEXT," +
                        "login_ms BIGINT," +
                        "logout_ms BIGINT," +
                        "playtime_ms BIGINT," +
                        "average_ping INTEGER," +
                        "world_playtimes TEXT" +
                        ");"
                )

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player_profiles (" +
                        "uuid TEXT PRIMARY KEY," +
                        "username TEXT," +
                        "first_login_ms BIGINT," +
                        "last_login_ms BIGINT," +
                        "total_playtime_ms BIGINT," +
                        "average_ping INTEGER," +
                        "country_code TEXT" +
                        ");"
                )

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS anomalies (" +
                        "timestamp BIGINT PRIMARY KEY," +
                        "severity TEXT," +
                        "tps REAL," +
                        "mspt REAL," +
                        "correlated_factors TEXT" +
                        ");"
                )

                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS aspa_users (" +
                        "username TEXT PRIMARY KEY," +
                        "password_hash TEXT," +
                        "role TEXT," +
                        "permissions TEXT," +
                        "token TEXT" +
                        ");"
                )

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_metrics_timestamp ON server_metrics(timestamp);")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_token ON aspa_users(token);")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_uuid ON player_sessions(uuid);")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_username ON player_sessions(username);")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_profiles_username ON player_profiles(username);")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_anomalies_timestamp ON anomalies(timestamp);")
            }
        }
    }

    override fun shutdown() {
        dbScope.cancel()
        try {
            dataSource?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun saveServerMetrics(record: ServerMetricsRecord): CompletableFuture<Void> {
        return dbScope.future {
            try {
                saveServerMetricsBatch(Collections.singletonList(record))
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }.thenApply<Void> { null }
    }

    @Throws(Exception::class)
    fun saveServerMetricsBatch(records: List<ServerMetricsRecord>) {
        val sql =
            "REPLACE INTO server_metrics (timestamp, tps, mspt, cpu_usage, ram_used_mb, ram_max_mb, online_players, loaded_chunks, entity_counts, gc_count_delta, gc_time_delta_ms, avg_ping, max_ping, chunks_per_world, entities_per_world) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                conn.autoCommit = false
                try {
                    for (r in records) {
                        ps.setLong(1, r.timestamp)
                        ps.setDouble(2, r.tps)
                        ps.setDouble(3, r.mspt)
                        ps.setDouble(4, r.cpuUsage)
                        ps.setLong(5, r.ramUsedMb)
                        ps.setLong(6, r.ramMaxMb)
                        ps.setInt(7, r.onlinePlayers)
                        ps.setInt(8, r.loadedChunks)
                        ps.setString(9, serializeMap(r.entityCounts))
                        ps.setLong(10, r.gcCountDelta)
                        ps.setLong(11, r.gcTimeDeltaMs)
                        ps.setDouble(12, r.avgPing)
                        ps.setDouble(13, r.maxPing)
                        ps.setString(14, serializeMap(r.chunksPerWorld))
                        ps.setString(15, serializeMap(r.entitiesPerWorld))
                        ps.addBatch()
                    }
                    ps.executeBatch()
                    conn.commit()
                } catch (e: Exception) {
                    try {
                        conn.rollback()
                    } catch (_: SQLException) {
                        // ignore
                    }
                    throw e
                } finally {
                    try {
                        conn.autoCommit = true
                    } catch (_: SQLException) {
                        // ignore
                    }
                }
            }
        }
    }

    override fun savePlayerSession(record: PlayerSessionRecord): CompletableFuture<Void> {
        return dbScope.future {
            try {
                getConnection().use { conn ->
                    conn.autoCommit = false
                    try {
                        val sessionSql =
                            "REPLACE INTO player_sessions (session_id, uuid, username, ip_address, country_code, login_ms, logout_ms, playtime_ms, average_ping, world_playtimes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        conn.prepareStatement(sessionSql).use { ps ->
                            ps.setString(1, record.sessionId)
                            ps.setString(2, record.uuid)
                            ps.setString(3, record.username)
                            ps.setString(4, record.ipAddress)
                            ps.setString(5, record.countryCode)
                            ps.setLong(6, record.loginMs)
                            ps.setLong(7, record.logoutMs)
                            ps.setLong(8, record.playtimeMs)
                            ps.setInt(9, record.averagePing)
                            ps.setString(10, serializeMap(record.worldPlaytimes))
                            ps.executeUpdate()
                        }

                        val profileSql =
                            "INSERT INTO player_profiles (uuid, username, first_login_ms, last_login_ms, total_playtime_ms, average_ping, country_code) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                                "ON CONFLICT(uuid) DO UPDATE SET " +
                                "username = excluded.username, " +
                                "first_login_ms = MIN(player_profiles.first_login_ms, excluded.first_login_ms), " +
                                "last_login_ms = MAX(player_profiles.last_login_ms, excluded.last_login_ms), " +
                                "total_playtime_ms = player_profiles.total_playtime_ms + excluded.total_playtime_ms, " +
                                "average_ping = excluded.average_ping, " +
                                "country_code = excluded.country_code"
                        conn.prepareStatement(profileSql).use { ps ->
                            ps.setString(1, record.uuid)
                            ps.setString(2, record.username)
                            ps.setLong(3, record.loginMs)
                            ps.setLong(4, if (record.logoutMs > 0) record.logoutMs else record.loginMs)
                            ps.setLong(5, record.playtimeMs)
                            ps.setInt(6, record.averagePing)
                            ps.setString(7, record.countryCode)
                            ps.executeUpdate()
                        }

                        conn.commit()
                    } catch (e: Exception) {
                        try {
                            conn.rollback()
                        } catch (_: SQLException) {
                            // ignore
                        }
                        throw e
                    } finally {
                        try {
                            conn.autoCommit = true
                        } catch (_: SQLException) {
                            // ignore
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }.thenApply<Void> { null }
    }

    override fun getServerMetricsHistory(
        startEpochMs: Long,
        endEpochMs: Long
    ): CompletableFuture<List<ServerMetricsRecord>> {
        return dbScope.future {
            val history = ArrayList<ServerMetricsRecord>()
            val sql =
                "SELECT * FROM server_metrics WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setLong(1, startEpochMs)
                        ps.setLong(2, endEpochMs)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                val r = ServerMetricsRecord()
                                r.timestamp = rs.getLong("timestamp")
                                r.tps = rs.getDouble("tps")
                                r.mspt = rs.getDouble("mspt")
                                r.cpuUsage = rs.getDouble("cpu_usage")
                                r.ramUsedMb = rs.getLong("ram_used_mb")
                                r.ramMaxMb = rs.getLong("ram_max_mb")
                                r.onlinePlayers = rs.getInt("online_players")
                                r.loadedChunks = rs.getInt("loaded_chunks")
                                r.entityCounts = deserializeMap(rs.getString("entity_counts"), Int::class.java)
                                r.gcCountDelta = rs.getLong("gc_count_delta")
                                r.gcTimeDeltaMs = rs.getLong("gc_time_delta_ms")
                                r.avgPing = rs.getDouble("avg_ping")
                                r.maxPing = rs.getDouble("max_ping")
                                r.chunksPerWorld = deserializeMap(rs.getString("chunks_per_world"), Int::class.java)
                                r.entitiesPerWorld = deserializeMap(rs.getString("entities_per_world"), Int::class.java)
                                history.add(r)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
            history
        }
    }

    override fun getPlayerProfile(uuid: String): CompletableFuture<Optional<PlayerProfile>> {
        return dbScope.future {
            val sql = "SELECT * FROM player_profiles WHERE uuid = ?"
            var profile: PlayerProfile? = null
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, uuid)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val p = PlayerProfile()
                                p.uuid = rs.getString("uuid")
                                p.username = rs.getString("username")
                                p.firstLoginMs = rs.getLong("first_login_ms")
                                p.lastLoginMs = rs.getLong("last_login_ms")
                                p.totalPlaytimeMs = rs.getLong("total_playtime_ms")
                                p.averagePing = rs.getInt("average_ping")
                                p.countryCode = rs.getString("country_code")
                                profile = p
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }

            if (profile != null) {
                try {
                    val sessions = loadSessionsForPlayer(uuid)
                    profile!!.sessions = sessions
                    profile!!.activityPunchcard = computePunchcard(sessions)
                    Optional.of(profile!!)
                } catch (e: SQLException) {
                    e.printStackTrace()
                    throw RuntimeException(e)
                }
            } else {
                Optional.empty<PlayerProfile>()
            }
        }
    }

    override fun getPlayerProfileByName(username: String): CompletableFuture<Optional<PlayerProfile>> {
        return dbScope.future {
            val sql = "SELECT * FROM player_profiles WHERE username = ?"
            var profile: PlayerProfile? = null
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, username)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val p = PlayerProfile()
                                p.uuid = rs.getString("uuid")
                                p.username = rs.getString("username")
                                p.firstLoginMs = rs.getLong("first_login_ms")
                                p.lastLoginMs = rs.getLong("last_login_ms")
                                p.totalPlaytimeMs = rs.getLong("total_playtime_ms")
                                p.averagePing = rs.getInt("average_ping")
                                p.countryCode = rs.getString("country_code")
                                profile = p
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }

            if (profile != null) {
                try {
                    val sessions = loadSessionsForPlayer(profile!!.uuid!!)
                    profile!!.sessions = sessions
                    profile!!.activityPunchcard = computePunchcard(sessions)
                    Optional.of(profile!!)
                } catch (e: SQLException) {
                    e.printStackTrace()
                    throw RuntimeException(e)
                }
            } else {
                Optional.empty<PlayerProfile>()
            }
        }
    }

    @Throws(SQLException::class)
    private fun loadSessionsForPlayer(uuid: String): List<PlayerSessionRecord> {
        val sessions = ArrayList<PlayerSessionRecord>()
        val sql = "SELECT * FROM player_sessions WHERE uuid = ? ORDER BY login_ms DESC"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val s = PlayerSessionRecord()
                        s.sessionId = rs.getString("session_id")
                        s.uuid = rs.getString("uuid")
                        s.username = rs.getString("username")
                        s.ipAddress = rs.getString("ip_address")
                        s.countryCode = rs.getString("country_code")
                        s.loginMs = rs.getLong("login_ms")
                        s.logoutMs = rs.getLong("logout_ms")
                        s.playtimeMs = rs.getLong("playtime_ms")
                        s.averagePing = rs.getInt("average_ping")
                        s.worldPlaytimes = deserializeMap(rs.getString("world_playtimes"), Long::class.java)
                        sessions.add(s)
                    }
                }
            }
        }
        return sessions
    }

    private fun computePunchcard(sessions: List<PlayerSessionRecord>): Array<IntArray> {
        val punchcard = Array(7) { IntArray(24) }
        for (s in sessions) {
            if (s.loginMs > 0) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = s.loginMs
                val day = cal.get(Calendar.DAY_OF_WEEK) - 1
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                if (day in 0..6 && hour in 0..23) {
                    punchcard[day][hour]++
                }
            }
        }
        return punchcard
    }

    override fun getAllSessions(
        startEpochMs: Long,
        endEpochMs: Long
    ): CompletableFuture<List<PlayerSessionRecord>> {
        return dbScope.future {
            val sessions = ArrayList<PlayerSessionRecord>()
            val sql =
                "SELECT * FROM player_sessions WHERE login_ms >= ? AND login_ms <= ? ORDER BY login_ms ASC"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setLong(1, startEpochMs)
                        ps.setLong(2, endEpochMs)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                val s = PlayerSessionRecord()
                                s.sessionId = rs.getString("session_id")
                                s.uuid = rs.getString("uuid")
                                s.username = rs.getString("username")
                                s.ipAddress = rs.getString("ip_address")
                                s.countryCode = rs.getString("country_code")
                                s.loginMs = rs.getLong("login_ms")
                                s.logoutMs = rs.getLong("logout_ms")
                                s.playtimeMs = rs.getLong("playtime_ms")
                                s.averagePing = rs.getInt("average_ping")
                                s.worldPlaytimes =
                                    deserializeMap(rs.getString("world_playtimes"), Long::class.java)
                                sessions.add(s)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
            sessions
        }
    }

    override fun saveAnomaly(anomaly: PerformanceAnomaly): CompletableFuture<Void> {
        return dbScope.future {
            val sql =
                "REPLACE INTO anomalies (timestamp, severity, tps, mspt, correlated_factors) VALUES (?, ?, ?, ?, ?)"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setLong(1, anomaly.timestamp)
                        ps.setString(2, anomaly.severity)
                        ps.setDouble(3, anomaly.tps)
                        ps.setDouble(4, anomaly.mspt)
                        ps.setString(5, serializeFactors(anomaly.correlatedFactors))
                        ps.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }.thenApply<Void> { null }
    }

    override fun getAnomalies(limit: Int): CompletableFuture<List<PerformanceAnomaly>> {
        return dbScope.future {
            val list = ArrayList<PerformanceAnomaly>()
            val sql = "SELECT * FROM anomalies ORDER BY timestamp DESC LIMIT ?"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setInt(1, limit)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                val a = PerformanceAnomaly()
                                a.timestamp = rs.getLong("timestamp")
                                a.severity = rs.getString("severity")
                                a.tps = rs.getDouble("tps")
                                a.mspt = rs.getDouble("mspt")
                                a.correlatedFactors = deserializeFactors(rs.getString("correlated_factors"))
                                list.add(a)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
            list
        }
    }

    override fun getUsers(): CompletableFuture<List<User>> {
        return dbScope.future {
            val list = ArrayList<User>()
            val sql = "SELECT * FROM aspa_users"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                list.add(
                                    User(
                                        rs.getString("username"),
                                        rs.getString("password_hash"),
                                        rs.getString("role"),
                                        deserializePermissions(rs.getString("permissions")),
                                        rs.getString("token")
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
            list
        }
    }

    override fun getUser(username: String): CompletableFuture<Optional<User>> {
        return dbScope.future {
            val sql = "SELECT * FROM aspa_users WHERE username = ?"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, username)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@future Optional.of(
                                    User(
                                        rs.getString("username"),
                                        rs.getString("password_hash"),
                                        rs.getString("role"),
                                        deserializePermissions(rs.getString("permissions")),
                                        rs.getString("token")
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
            Optional.empty<User>()
        }
    }

    override fun getUserByToken(token: String): CompletableFuture<Optional<User>> {
        return dbScope.future {
            val sql = "SELECT * FROM aspa_users WHERE token = ?"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, token)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@future Optional.of(
                                    User(
                                        rs.getString("username"),
                                        rs.getString("password_hash"),
                                        rs.getString("role"),
                                        deserializePermissions(rs.getString("permissions")),
                                        rs.getString("token")
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
            Optional.empty<User>()
        }
    }

    override fun saveUser(user: User): CompletableFuture<Void> {
        return dbScope.future {
            val sql = "REPLACE INTO aspa_users (username, password_hash, role, permissions, token) VALUES (?, ?, ?, ?, ?)"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, user.username)
                        ps.setString(2, user.passwordHash)
                        ps.setString(3, user.role)
                        ps.setString(4, serializePermissions(user.permissions))
                        ps.setString(5, user.token)
                        ps.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }.thenApply<Void> { null }
    }

    override fun deleteUser(username: String): CompletableFuture<Void> {
        return dbScope.future {
            val sql = "DELETE FROM aspa_users WHERE username = ?"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, username)
                        ps.executeUpdate()
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }.thenApply<Void> { null }
    }

    override fun hasAnyUser(): CompletableFuture<Boolean> {
        return dbScope.future {
            val sql = "SELECT 1 FROM aspa_users LIMIT 1"
            try {
                getConnection().use { conn ->
                    conn.prepareStatement(sql).use { ps ->
                        ps.executeQuery().use { rs ->
                            return@future rs.next()
                        }
                    }
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }
    }

    private companion object {
        private val MAPPER = ObjectMapper()

        private fun serializeMap(map: Map<*, *>?): String {
            if (map == null) return "{}"
            return try {
                MAPPER.writeValueAsString(map)
            } catch (e: Exception) {
                "{}"
            }
        }

        private fun <T> deserializeMap(json: String?, valueType: Class<T>): Map<String, T> {
            if (json.isNullOrEmpty()) return HashMap()
            return try {
                MAPPER.readValue(
                    json,
                    MAPPER.typeFactory.constructMapType(HashMap::class.java, String::class.java, valueType)
                )
            } catch (e: Exception) {
                HashMap()
            }
        }

        private fun serializePermissions(permissions: List<String>?): String {
            if (permissions == null) return "[]"
            return try {
                MAPPER.writeValueAsString(permissions)
            } catch (e: Exception) {
                "[]"
            }
        }

        private fun deserializePermissions(json: String?): List<String> {
            if (json.isNullOrEmpty()) return ArrayList()
            return try {
                MAPPER.readValue(
                    json,
                    MAPPER.typeFactory.constructCollectionType(
                        ArrayList::class.java,
                        String::class.java
                    )
                )
            } catch (e: Exception) {
                ArrayList()
            }
        }

        private fun serializeFactors(factors: List<PerformanceAnomaly.CorrelatedFactor>?): String {
            if (factors == null) return "[]"
            return try {
                MAPPER.writeValueAsString(factors)
            } catch (e: Exception) {
                "[]"
            }
        }

        private fun deserializeFactors(json: String?): List<PerformanceAnomaly.CorrelatedFactor> {
            if (json.isNullOrEmpty()) return ArrayList()
            return try {
                MAPPER.readValue(
                    json,
                    MAPPER.typeFactory.constructCollectionType(
                        ArrayList::class.java,
                        PerformanceAnomaly.CorrelatedFactor::class.java
                    )
                )
            } catch (e: Exception) {
                ArrayList()
            }
        }
    }
}
