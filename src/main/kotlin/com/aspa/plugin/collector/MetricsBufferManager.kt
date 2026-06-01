package com.aspa.plugin.collector

import com.aspa.plugin.ASPA
import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.database.DatabaseManager
import com.aspa.plugin.database.SQLiteProvider
import com.aspa.plugin.database.MySQLProvider
import com.aspa.plugin.database.MongoDBProvider
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.ArrayList

class MetricsBufferManager(private val plugin: ASPA) {
    private val serverQueue = ConcurrentLinkedQueue<ServerMetricsRecord>()
    private val playerQueue = ConcurrentLinkedQueue<PlayerSessionRecord>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null

    fun start() {
        val intervalSeconds = plugin.config.getLong("ingestion.db-flush-interval-seconds", 30L)
        flushJob = scope.launch {
            while (isActive) {
                delay(intervalSeconds * 1000L)
                try {
                    flush()
                } catch (e: Exception) {
                    plugin.logger.severe("Error during periodic metrics flush: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    fun submitServerMetric(record: ServerMetricsRecord) {
        serverQueue.offer(record)
    }

    fun submitPlayerSession(record: PlayerSessionRecord) {
        playerQueue.offer(record)
    }

    fun flush() {
        val dbProvider = plugin.getDatabaseProvider() ?: return

        // 1. Drain ServerMetricsRecord completely
        val serverList = ArrayList<ServerMetricsRecord>()
        while (true) {
            val r = serverQueue.poll() ?: break
            serverList.add(r)
        }

        if (serverList.isNotEmpty()) {
            try {
                if (dbProvider is DatabaseManager) {
                    val activeProvider = dbProvider.getActiveProvider()
                    when (activeProvider) {
                        is SQLiteProvider -> activeProvider.saveServerMetricsBatch(serverList)
                        is MySQLProvider -> activeProvider.saveServerMetricsBatch(serverList)
                        is MongoDBProvider -> activeProvider.saveServerMetricsBatch(serverList)
                        else -> fallbackSaveServerMetrics(dbProvider, serverList)
                    }
                } else {
                    fallbackSaveServerMetrics(dbProvider, serverList)
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to batch save server metrics: ${e.message}")
                e.printStackTrace()
                // If batch save failed, let's try individual fallback
                try {
                    fallbackSaveServerMetrics(dbProvider, serverList)
                } catch (ex: Exception) {
                    plugin.logger.severe("Failed individual fallback save of server metrics: ${ex.message}")
                }
            }
        }

        // 2. Drain PlayerSessionRecord completely
        val playerList = ArrayList<PlayerSessionRecord>()
        while (true) {
            val r = playerQueue.poll() ?: break
            playerList.add(r)
        }

        if (playerList.isNotEmpty()) {
            val futures = playerList.map { record ->
                dbProvider.savePlayerSession(record).exceptionally { ex ->
                    plugin.logger.severe("Failed to save player session for ${record.username}: ${ex.message}")
                    null
                }
            }
            try {
                for (future in futures) {
                    future.get()
                }
            } catch (e: Exception) {
                plugin.logger.severe("Error waiting for player sessions to save: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun fallbackSaveServerMetrics(dbProvider: DatabaseProvider, list: List<ServerMetricsRecord>) {
        val futures = list.map { record ->
            dbProvider.saveServerMetrics(record).exceptionally { ex ->
                plugin.logger.severe("Failed to save server metrics at timestamp ${record.timestamp}: ${ex.message}")
                null
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).get()
    }

    fun shutdown() {
        flushJob?.cancel()
        try {
            runBlocking {
                flush()
            }
        } catch (e: Exception) {
            plugin.logger.severe("Error during graceful shutdown flush of MetricsBufferManager: ${e.message}")
            e.printStackTrace()
        }
        scope.cancel()
    }
}
