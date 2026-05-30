package com.aspa.plugin.database

import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import com.aspa.plugin.model.User
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SQLiteProvider(private val dbFile: File) : DatabaseProvider {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var connection: Connection? = null

    @Synchronized
    private fun getConnection(): Connection {
        if (connection == null || connection!!.isClosed) {
            try {
                Class.forName("org.sqlite.JDBC")
            } catch (e: ClassNotFoundException) {
                throw SQLException("SQLite JDBC driver not found", e)
            }
            dbFile.parentFile?.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            connection!!.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL;")
            }
        }
        return connection!!
    }

    @Throws(Exception::class)
    override fun initialize() {
        val conn = getConnection()
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

    override fun shutdown() {
        try {
            if (connection != null && !connection!!.isClosed) {
                connection!!.close()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        executor.shutdown()
    }

    override fun saveServerMetrics(record: ServerMetricsRecord): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                try {
                    saveServerMetricsBatch(Collections.singletonList(record))
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            },
            executor
        )
    }

    @Throws(Exception::class)
    fun saveServerMetricsBatch(records: List<ServerMetricsRecord>) {
        val sql =
            "REPLACE INTO server_metrics (timestamp, tps, mspt, cpu_usage, ram_used_mb, ram_max_mb, online_players, loaded_chunks, entity_counts, gc_count_delta, gc_time_delta_ms, avg_ping, max_ping, chunks_per_world, entities_per_world) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val conn = getConnection()
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

    override fun savePlayerSession(record: PlayerSessionRecord): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val conn = getConnection()
                try {
                    conn.autoCommit = false
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
                    throw RuntimeException(e)
                } finally {
                    try {
                        conn.autoCommit = true
                    } catch (_: SQLException) {
                        // ignore
                    }
                }
            },
            executor
        )
    }

    override fun getServerMetricsHistory(
        startEpochMs: Long,
        endEpochMs: Long
    ): CompletableFuture<List<ServerMetricsRecord>> {
        return CompletableFuture.supplyAsync(
            {
                val history = ArrayList<ServerMetricsRecord>()
                val sql =
                    "SELECT * FROM server_metrics WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC"
                try {
                    val conn = getConnection()
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
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                history
            },
            executor
        )
    }

    override fun getPlayerProfile(uuid: String): CompletableFuture<Optional<PlayerProfile>> {
        return CompletableFuture.supplyAsync(
            {
                val sql = "SELECT * FROM player_profiles WHERE uuid = ?"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, uuid)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val profile = PlayerProfile()
                                profile.uuid = rs.getString("uuid")
                                profile.username = rs.getString("username")
                                profile.firstLoginMs = rs.getLong("first_login_ms")
                                profile.lastLoginMs = rs.getLong("last_login_ms")
                                profile.totalPlaytimeMs = rs.getLong("total_playtime_ms")
                                profile.averagePing = rs.getInt("average_ping")
                                profile.countryCode = rs.getString("country_code")

                                val sessions = loadSessionsForPlayer(uuid)
                                profile.sessions = sessions
                                profile.activityPunchcard = computePunchcard(sessions)
                                return@supplyAsync Optional.of(profile)
                            }
                        }
                    }
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                Optional.empty()
            },
            executor
        )
    }

    override fun getPlayerProfileByName(username: String): CompletableFuture<Optional<PlayerProfile>> {
        return CompletableFuture.supplyAsync(
            {
                val sql = "SELECT * FROM player_profiles WHERE username = ?"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, username)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val profile = PlayerProfile()
                                val uuid = rs.getString("uuid")
                                profile.uuid = uuid
                                profile.username = rs.getString("username")
                                profile.firstLoginMs = rs.getLong("first_login_ms")
                                profile.lastLoginMs = rs.getLong("last_login_ms")
                                profile.totalPlaytimeMs = rs.getLong("total_playtime_ms")
                                profile.averagePing = rs.getInt("average_ping")
                                profile.countryCode = rs.getString("country_code")

                                val sessions = loadSessionsForPlayer(uuid)
                                profile.sessions = sessions
                                profile.activityPunchcard = computePunchcard(sessions)
                                return@supplyAsync Optional.of(profile)
                            }
                        }
                    }
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                Optional.empty()
            },
            executor
        )
    }

    @Throws(SQLException::class)
    private fun loadSessionsForPlayer(uuid: String): List<PlayerSessionRecord> {
        val sessions = ArrayList<PlayerSessionRecord>()
        val sql = "SELECT * FROM player_sessions WHERE uuid = ? ORDER BY login_ms DESC"
        val conn = getConnection()
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
        return CompletableFuture.supplyAsync(
            {
                val sessions = ArrayList<PlayerSessionRecord>()
                val sql =
                    "SELECT * FROM player_sessions WHERE login_ms >= ? AND login_ms <= ? ORDER BY login_ms ASC"
                try {
                    val conn = getConnection()
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
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                sessions
            },
            executor
        )
    }

    override fun saveAnomaly(anomaly: PerformanceAnomaly): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val sql =
                    "REPLACE INTO anomalies (timestamp, severity, tps, mspt, correlated_factors) VALUES (?, ?, ?, ?, ?)"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setLong(1, anomaly.timestamp)
                        ps.setString(2, anomaly.severity)
                        ps.setDouble(3, anomaly.tps)
                        ps.setDouble(4, anomaly.mspt)
                        ps.setString(5, serializeFactors(anomaly.correlatedFactors))
                        ps.executeUpdate()
                    }
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
            },
            executor
        )
    }

    override fun getAnomalies(limit: Int): CompletableFuture<List<PerformanceAnomaly>> {
        return CompletableFuture.supplyAsync(
            {
                val list = ArrayList<PerformanceAnomaly>()
                val sql = "SELECT * FROM anomalies ORDER BY timestamp DESC LIMIT ?"
                try {
                    val conn = getConnection()
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
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                list
            },
            executor
        )
    }

    override fun getUsers(): CompletableFuture<List<User>> {
        return CompletableFuture.supplyAsync(
            {
                val list = ArrayList<User>()
                val sql = "SELECT * FROM aspa_users"
                try {
                    val conn = getConnection()
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
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                list
            },
            executor
        )
    }

    override fun getUser(username: String): CompletableFuture<Optional<User>> {
        return CompletableFuture.supplyAsync(
            {
                val sql = "SELECT * FROM aspa_users WHERE username = ?"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, username)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@supplyAsync Optional.of(
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
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                Optional.empty()
            },
            executor
        )
    }

    override fun getUserByToken(token: String): CompletableFuture<Optional<User>> {
        return CompletableFuture.supplyAsync(
            {
                val sql = "SELECT * FROM aspa_users WHERE token = ?"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, token)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@supplyAsync Optional.of(
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
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
                Optional.empty()
            },
            executor
        )
    }

    override fun saveUser(user: User): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val sql = "REPLACE INTO aspa_users (username, password_hash, role, permissions, token) VALUES (?, ?, ?, ?, ?)"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, user.username)
                        ps.setString(2, user.passwordHash)
                        ps.setString(3, user.role)
                        ps.setString(4, serializePermissions(user.permissions))
                        ps.setString(5, user.token)
                        ps.executeUpdate()
                    }
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
            },
            executor
        )
    }

    override fun deleteUser(username: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val sql = "DELETE FROM aspa_users WHERE username = ?"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.setString(1, username)
                        ps.executeUpdate()
                    }
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
            },
            executor
        )
    }

    override fun hasAnyUser(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync(
            {
                val sql = "SELECT 1 FROM aspa_users LIMIT 1"
                try {
                    val conn = getConnection()
                    conn.prepareStatement(sql).use { ps ->
                        ps.executeQuery().use { rs ->
                            return@supplyAsync rs.next()
                        }
                    }
                } catch (e: SQLException) {
                    throw RuntimeException(e)
                }
            },
            executor
        )
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
