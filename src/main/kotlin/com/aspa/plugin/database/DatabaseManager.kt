package com.aspa.plugin.database

import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import com.aspa.plugin.model.User
import java.io.File
import java.util.Collections
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class DatabaseManager(
    driver: String,
    dataFolder: File,
    configMap: Map<String, Any?>,
    private val flushIntervalSeconds: Long
) : DatabaseProvider {
    private val driver: String = driver.uppercase()
    private val activeProvider: DatabaseProvider

    private val metricsQueue: ConcurrentLinkedQueue<ServerMetricsRecord> = ConcurrentLinkedQueue()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    init {
        activeProvider = when (this.driver) {
            "SQLITE" -> {
                @Suppress("UNCHECKED_CAST")
                val sqliteCfg = configMap["sqlite"] as? Map<String, Any?> ?: Collections.emptyMap()
                val fileName = sqliteCfg["file"] as? String ?: "analytics.db"
                val dbFile = File(dataFolder, fileName)
                SQLiteProvider(dbFile)
            }
            "MYSQL" -> {
                @Suppress("UNCHECKED_CAST")
                val mysqlCfg = configMap["mysql"] as? Map<String, Any?> ?: Collections.emptyMap()
                val host = mysqlCfg["host"] as? String ?: "localhost"
                val port = (mysqlCfg["port"] as? Number)?.toInt() ?: 3306
                val database = mysqlCfg["database"] as? String ?: "aspa_analytics"
                val username = mysqlCfg["username"] as? String ?: "aspa"
                val password = mysqlCfg["password"] as? String ?: ""

                @Suppress("UNCHECKED_CAST")
                val poolCfg = mysqlCfg["pool"] as? Map<String, Any?> ?: Collections.emptyMap()
                val maxPoolSize = (poolCfg["maximum-pool-size"] as? Number)?.toInt() ?: 10
                val minIdle = (poolCfg["minimum-idle"] as? Number)?.toInt() ?: 2
                val connectionTimeout = (poolCfg["connection-timeout"] as? Number)?.toLong() ?: 30000L
                val idleTimeout = (poolCfg["idle-timeout"] as? Number)?.toLong() ?: 600000L
                val maxLifetime = (poolCfg["max-lifetime"] as? Number)?.toLong() ?: 1800000L

                MySQLProvider(
                    host,
                    port,
                    database,
                    username,
                    password,
                    maxPoolSize,
                    minIdle,
                    connectionTimeout,
                    idleTimeout,
                    maxLifetime
                )
            }
            "MONGODB" -> {
                @Suppress("UNCHECKED_CAST")
                val mongoCfg = configMap["mongodb"] as? Map<String, Any?> ?: Collections.emptyMap()
                val uri = mongoCfg["uri"] as? String ?: "mongodb://localhost:27017"
                val database = mongoCfg["database"] as? String ?: "aspa_analytics"
                MongoDBProvider(uri, database)
            }
            else -> throw IllegalArgumentException("Unsupported database driver: ${this.driver}")
        }
    }

    override fun initialize() {
        activeProvider.initialize()
        scheduler.scheduleAtFixedRate(
            this::flushMetrics,
            flushIntervalSeconds,
            flushIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    override fun shutdown() {
        scheduler.shutdown()
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        flushMetrics()
        activeProvider.shutdown()
    }

    override fun saveServerMetrics(record: ServerMetricsRecord): CompletableFuture<Void> {
        metricsQueue.offer(record)
        return CompletableFuture.completedFuture(null)
    }

    private fun flushMetrics() {
        val batch = ArrayList<ServerMetricsRecord>()
        while (true) {
            val record = metricsQueue.poll() ?: break
            batch.add(record)
        }

        if (batch.isNotEmpty()) {
            try {
                when (activeProvider) {
                    is SQLiteProvider -> activeProvider.saveServerMetricsBatch(batch)
                    is MySQLProvider -> activeProvider.saveServerMetricsBatch(batch)
                    is MongoDBProvider -> activeProvider.saveServerMetricsBatch(batch)
                    else -> {
                        for (r in batch) {
                            activeProvider.saveServerMetrics(r).get()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun savePlayerSession(record: PlayerSessionRecord): CompletableFuture<Void> =
        activeProvider.savePlayerSession(record)

    override fun getServerMetricsHistory(
        startEpochMs: Long,
        endEpochMs: Long
    ): CompletableFuture<List<ServerMetricsRecord>> =
        activeProvider.getServerMetricsHistory(startEpochMs, endEpochMs)

    override fun getPlayerProfile(uuid: String): CompletableFuture<Optional<PlayerProfile>> =
        activeProvider.getPlayerProfile(uuid)

    override fun getPlayerProfileByName(username: String): CompletableFuture<Optional<PlayerProfile>> =
        activeProvider.getPlayerProfileByName(username)

    override fun getAllSessions(
        startEpochMs: Long,
        endEpochMs: Long
    ): CompletableFuture<List<PlayerSessionRecord>> =
        activeProvider.getAllSessions(startEpochMs, endEpochMs)

    override fun saveAnomaly(anomaly: PerformanceAnomaly): CompletableFuture<Void> =
        activeProvider.saveAnomaly(anomaly)

    override fun getAnomalies(limit: Int): CompletableFuture<List<PerformanceAnomaly>> =
        activeProvider.getAnomalies(limit)

    override fun getUsers(): CompletableFuture<List<User>> =
        activeProvider.getUsers()

    override fun getUser(username: String): CompletableFuture<Optional<User>> =
        activeProvider.getUser(username)

    override fun getUserByToken(token: String): CompletableFuture<Optional<User>> =
        activeProvider.getUserByToken(token)

    override fun saveUser(user: User): CompletableFuture<Void> =
        activeProvider.saveUser(user)

    override fun deleteUser(username: String): CompletableFuture<Void> =
        activeProvider.deleteUser(username)

    override fun hasAnyUser(): CompletableFuture<Boolean> =
        activeProvider.hasAnyUser()

    fun getActiveProvider(): DatabaseProvider = activeProvider
}
