package com.aspa.plugin.database

import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import com.aspa.plugin.model.User
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.bson.Document
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MongoDBProvider(private val uri: String, private val databaseName: String) : DatabaseProvider {
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private var mongoClient: MongoClient? = null
    private var db: MongoDatabase? = null
    private var metricsCol: MongoCollection<Document>? = null
    private var sessionsCol: MongoCollection<Document>? = null
    private var profilesCol: MongoCollection<Document>? = null
    private var anomaliesCol: MongoCollection<Document>? = null
    private var usersCol: MongoCollection<Document>? = null

    @Throws(Exception::class)
    override fun initialize() {
        mongoClient = MongoClients.create(uri)
        db = mongoClient!!.getDatabase(databaseName)

        metricsCol = db!!.getCollection("server_metrics")
        sessionsCol = db!!.getCollection("player_sessions")
        profilesCol = db!!.getCollection("player_profiles")
        anomaliesCol = db!!.getCollection("anomalies")
        usersCol = db!!.getCollection("aspa_users")

        metricsCol!!.createIndex(Document("timestamp", 1))
        sessionsCol!!.createIndex(Document("uuid", 1))
        sessionsCol!!.createIndex(Document("username", 1))
        sessionsCol!!.createIndex(Document("loginMs", 1))
        profilesCol!!.createIndex(Document("uuid", 1))
        profilesCol!!.createIndex(Document("username", 1))
        anomaliesCol!!.createIndex(Document("timestamp", 1))
        usersCol!!.createIndex(Document("username", 1))
        usersCol!!.createIndex(Document("token", 1))
    }

    override fun shutdown() {
        mongoClient?.close()
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
        if (records.isEmpty()) return
        val bulkWrites = ArrayList<com.mongodb.client.model.WriteModel<Document>>()
        for (r in records) {
            val doc = Document()
                .append("timestamp", r.timestamp)
                .append("tps", r.tps)
                .append("mspt", r.mspt)
                .append("cpuUsage", r.cpuUsage)
                .append("ramUsedMb", r.ramUsedMb)
                .append("ramMaxMb", r.ramMaxMb)
                .append("onlinePlayers", r.onlinePlayers)
                .append("loadedChunks", r.loadedChunks)
                .append("entityCounts", Document(r.entityCounts ?: emptyMap<String, Int>()))
                .append("gcCountDelta", r.gcCountDelta)
                .append("gcTimeDeltaMs", r.gcTimeDeltaMs)
                .append("avgPing", r.avgPing)
                .append("maxPing", r.maxPing)
                .append(
                    "chunksPerWorld",
                    Document(r.chunksPerWorld ?: emptyMap<String, Int>())
                )
                .append(
                    "entitiesPerWorld",
                    Document(r.entitiesPerWorld ?: emptyMap<String, Int>())
                )

            bulkWrites.add(
                com.mongodb.client.model.ReplaceOneModel(
                    Document("timestamp", r.timestamp),
                    doc,
                    com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
            )
        }
        metricsCol!!.bulkWrite(bulkWrites)
    }

    override fun savePlayerSession(record: PlayerSessionRecord): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val sessionDoc = Document()
                    .append("sessionId", record.sessionId)
                    .append("uuid", record.uuid)
                    .append("username", record.username)
                    .append("ipAddress", record.ipAddress)
                    .append("countryCode", record.countryCode)
                    .append("loginMs", record.loginMs)
                    .append("logoutMs", record.logoutMs)
                    .append("playtimeMs", record.playtimeMs)
                    .append("averagePing", record.averagePing)
                    .append("worldPlaytimes", Document(record.worldPlaytimes ?: emptyMap<String, Long>()))

                sessionsCol!!.replaceOne(
                    Document("sessionId", record.sessionId),
                    sessionDoc,
                    com.mongodb.client.model.ReplaceOptions().upsert(true)
                )

                val profileQuery = Document("uuid", record.uuid)
                val profileUpdate = Document()
                    .append(
                        "\$set",
                        Document("username", record.username)
                            .append("countryCode", record.countryCode)
                            .append("averagePing", record.averagePing)
                    )
                    .append("\$min", Document("firstLoginMs", record.loginMs))
                    .append(
                        "\$max",
                        Document(
                            "lastLoginMs",
                            if (record.logoutMs > 0) record.logoutMs else record.loginMs
                        )
                    )
                    .append("\$inc", Document("totalPlaytimeMs", record.playtimeMs))

                profilesCol!!.updateOne(profileQuery, profileUpdate, com.mongodb.client.model.UpdateOptions().upsert(true))
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
                val query = Document("timestamp", Document("\$gte", startEpochMs).append("\$lte", endEpochMs))
                for (doc in metricsCol!!.find(query).sort(Document("timestamp", 1))) {
                    val r = ServerMetricsRecord()
                    r.timestamp = doc.getLong("timestamp")
                    r.tps = doc.getDouble("tps")
                    r.mspt = doc.getDouble("mspt")
                    r.cpuUsage = doc.getDouble("cpuUsage")
                    r.ramUsedMb = doc.getLong("ramUsedMb")
                    r.ramMaxMb = doc.getLong("ramMaxMb")
                    r.onlinePlayers = doc.getInteger("onlinePlayers")
                    r.loadedChunks = doc.getInteger("loadedChunks")

                    val entityCountsDoc = doc.get("entityCounts") as? Document
                    val entityCounts = HashMap<String, Int>()
                    if (entityCountsDoc != null) {
                        for (key in entityCountsDoc.keys) {
                            entityCounts[key] = entityCountsDoc.getInteger(key)
                        }
                    }
                    r.entityCounts = entityCounts

                    r.gcCountDelta = doc.getLong("gcCountDelta") ?: 0L
                    r.gcTimeDeltaMs = doc.getLong("gcTimeDeltaMs") ?: 0L
                    r.avgPing = doc.getDouble("avgPing") ?: 0.0
                    r.maxPing = doc.getDouble("maxPing") ?: 0.0

                    val cpwDoc = doc.get("chunksPerWorld") as? Document
                    val cpw = HashMap<String, Int>()
                    if (cpwDoc != null) {
                        for (key in cpwDoc.keys) {
                            cpw[key] = cpwDoc.getInteger(key)
                        }
                    }
                    r.chunksPerWorld = cpw

                    val epwDoc = doc.get("entitiesPerWorld") as? Document
                    val epw = HashMap<String, Int>()
                    if (epwDoc != null) {
                        for (key in epwDoc.keys) {
                            epw[key] = epwDoc.getInteger(key)
                        }
                    }
                    r.entitiesPerWorld = epw

                    history.add(r)
                }
                history
            },
            executor
        )
    }

    override fun getPlayerProfile(uuid: String): CompletableFuture<Optional<PlayerProfile>> {
        return CompletableFuture.supplyAsync(
            {
                val doc = profilesCol!!.find(Document("uuid", uuid)).first()
                if (doc != null) {
                    val profile = PlayerProfile()
                    profile.uuid = doc.getString("uuid")
                    profile.username = doc.getString("username")
                    profile.firstLoginMs = doc.getLong("firstLoginMs")
                    profile.lastLoginMs = doc.getLong("lastLoginMs")
                    profile.totalPlaytimeMs = doc.getLong("totalPlaytimeMs")
                    profile.averagePing = doc.getInteger("averagePing")
                    profile.countryCode = doc.getString("countryCode")

                    val sessions = loadSessionsForPlayer(uuid)
                    profile.sessions = sessions
                    profile.activityPunchcard = computePunchcard(sessions)
                    return@supplyAsync Optional.of(profile)
                }
                Optional.empty()
            },
            executor
        )
    }

    override fun getPlayerProfileByName(username: String): CompletableFuture<Optional<PlayerProfile>> {
        return CompletableFuture.supplyAsync(
            {
                val doc = profilesCol!!.find(Document("username", username)).first()
                if (doc != null) {
                    val profile = PlayerProfile()
                    val uuid = doc.getString("uuid")
                    profile.uuid = uuid
                    profile.username = doc.getString("username")
                    profile.firstLoginMs = doc.getLong("firstLoginMs")
                    profile.lastLoginMs = doc.getLong("lastLoginMs")
                    profile.totalPlaytimeMs = doc.getLong("totalPlaytimeMs")
                    profile.averagePing = doc.getInteger("averagePing")
                    profile.countryCode = doc.getString("countryCode")

                    val sessions = loadSessionsForPlayer(uuid)
                    profile.sessions = sessions
                    profile.activityPunchcard = computePunchcard(sessions)
                    return@supplyAsync Optional.of(profile)
                }
                Optional.empty()
            },
            executor
        )
    }

    private fun loadSessionsForPlayer(uuid: String): List<PlayerSessionRecord> {
        val sessions = ArrayList<PlayerSessionRecord>()
        val query = Document("uuid", uuid)
        for (doc in sessionsCol!!.find(query).sort(Document("loginMs", -1))) {
            sessions.add(mapDocumentToSession(doc))
        }
        return sessions
    }

    private fun mapDocumentToSession(doc: Document): PlayerSessionRecord {
        val s = PlayerSessionRecord()
        s.sessionId = doc.getString("sessionId")
        s.uuid = doc.getString("uuid")
        s.username = doc.getString("username")
        s.ipAddress = doc.getString("ipAddress")
        s.countryCode = doc.getString("countryCode")
        s.loginMs = doc.getLong("loginMs")
        s.logoutMs = doc.getLong("logoutMs")
        s.playtimeMs = doc.getLong("playtimeMs")
        s.averagePing = doc.getInteger("averagePing")

        val worldPlaytimesDoc = doc.get("worldPlaytimes") as? Document
        val worldPlaytimes = HashMap<String, Long>()
        if (worldPlaytimesDoc != null) {
            for (key in worldPlaytimesDoc.keys) {
                worldPlaytimes[key] = worldPlaytimesDoc.getLong(key)
            }
        }
        s.worldPlaytimes = worldPlaytimes
        return s
    }

    private fun computePunchcard(sessions: List<PlayerSessionRecord>): Array<IntArray> {
        val punchcard = Array(7) { IntArray(24) }
        for (s in sessions) {
            if (s.loginMs > 0) {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = s.loginMs
                val day = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
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
                val query = Document("loginMs", Document("\$gte", startEpochMs).append("\$lte", endEpochMs))
                for (doc in sessionsCol!!.find(query).sort(Document("loginMs", 1))) {
                    sessions.add(mapDocumentToSession(doc))
                }
                sessions
            },
            executor
        )
    }

    override fun saveAnomaly(anomaly: PerformanceAnomaly): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val factorDocs = ArrayList<Document>()
                anomaly.correlatedFactors?.let { factors ->
                    for (f in factors) {
                        factorDocs.add(
                            Document()
                                .append("factor", f.factor)
                                .append("value", f.value)
                                .append("correlationStrength", f.correlationStrength)
                                .append("description", f.description)
                        )
                    }
                }

                val doc = Document()
                    .append("timestamp", anomaly.timestamp)
                    .append("severity", anomaly.severity)
                    .append("tps", anomaly.tps)
                    .append("mspt", anomaly.mspt)
                    .append("correlatedFactors", factorDocs)

                anomaliesCol!!.replaceOne(
                    Document("timestamp", anomaly.timestamp),
                    doc,
                    com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
            },
            executor
        )
    }

    override fun getAnomalies(limit: Int): CompletableFuture<List<PerformanceAnomaly>> {
        return CompletableFuture.supplyAsync(
            {
                val anomalies = ArrayList<PerformanceAnomaly>()
                for (doc in anomaliesCol!!.find().sort(Document("timestamp", -1)).limit(limit)) {
                    val a = PerformanceAnomaly()
                    a.timestamp = doc.getLong("timestamp")
                    a.severity = doc.getString("severity")
                    a.tps = doc.getDouble("tps")
                    a.mspt = doc.getDouble("mspt")

                    val factorDocs = doc.get("correlatedFactors") as? List<*>
                    val factors = ArrayList<PerformanceAnomaly.CorrelatedFactor>()
                    if (factorDocs != null) {
                        for (fd in factorDocs) {
                            if (fd is Document) {
                                factors.add(
                                    PerformanceAnomaly.CorrelatedFactor(
                                        fd.getString("factor"),
                                        fd.getDouble("value"),
                                        fd.getDouble("correlationStrength"),
                                        fd.getString("description")
                                    )
                                )
                            }
                        }
                    }
                    a.correlatedFactors = factors
                    anomalies.add(a)
                }
                anomalies
            },
            executor
        )
    }

    override fun getUsers(): CompletableFuture<List<User>> {
        return CompletableFuture.supplyAsync(
            {
                val list = ArrayList<User>()
                for (doc in usersCol!!.find()) {
                    @Suppress("UNCHECKED_CAST")
                    val permissions = doc.get("permissions") as? List<String> ?: emptyList()
                    list.add(
                        User(
                            doc.getString("username"),
                            doc.getString("passwordHash"),
                            doc.getString("role"),
                            permissions,
                            doc.getString("token")
                        )
                    )
                }
                list
            },
            executor
        )
    }

    override fun getUser(username: String): CompletableFuture<Optional<User>> {
        return CompletableFuture.supplyAsync(
            {
                val doc = usersCol!!.find(Document("username", username)).first()
                if (doc != null) {
                    @Suppress("UNCHECKED_CAST")
                    val permissions = doc.get("permissions") as? List<String> ?: emptyList()
                    return@supplyAsync Optional.of(
                        User(
                            doc.getString("username"),
                            doc.getString("passwordHash"),
                            doc.getString("role"),
                            permissions,
                            doc.getString("token")
                        )
                    )
                }
                Optional.empty()
            },
            executor
        )
    }

    override fun getUserByToken(token: String): CompletableFuture<Optional<User>> {
        return CompletableFuture.supplyAsync(
            {
                val doc = usersCol!!.find(Document("token", token)).first()
                if (doc != null) {
                    @Suppress("UNCHECKED_CAST")
                    val permissions = doc.get("permissions") as? List<String> ?: emptyList()
                    return@supplyAsync Optional.of(
                        User(
                            doc.getString("username"),
                            doc.getString("passwordHash"),
                            doc.getString("role"),
                            permissions,
                            doc.getString("token")
                        )
                    )
                }
                Optional.empty()
            },
            executor
        )
    }

    override fun saveUser(user: User): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                val doc = Document()
                    .append("username", user.username)
                    .append("passwordHash", user.passwordHash)
                    .append("role", user.role)
                    .append("permissions", user.permissions)
                    .append("token", user.token)
                usersCol!!.replaceOne(
                    Document("username", user.username),
                    doc,
                    com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
            },
            executor
        )
    }

    override fun deleteUser(username: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync(
            {
                usersCol!!.deleteOne(Document("username", username))
            },
            executor
        )
    }

    override fun hasAnyUser(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync(
            {
                usersCol!!.countDocuments() > 0
            },
            executor
        )
    }
}
