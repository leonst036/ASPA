package com.aspa.plugin

import com.aspa.plugin.analysis.StatisticsEngine
import com.aspa.plugin.api.AnalysisEngine
import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.collector.MetricsBufferManager
import com.aspa.plugin.collector.PlayerMetricCollector
import com.aspa.plugin.collector.ServerMetricCollector
import com.aspa.plugin.database.DatabaseManager
import com.aspa.plugin.model.ForecastResult
import com.aspa.plugin.model.RetentionReport
import com.aspa.plugin.pterodactyl.PterodactylService
import com.aspa.plugin.server.EmbeddedServer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture

class ASPA : JavaPlugin(), TabExecutor {
    private val startTime = System.currentTimeMillis()

    private var databaseProvider: DatabaseProvider? = null
    private var analysisEngine: AnalysisEngine? = null

    private var serverMetricCollector: ServerMetricCollector? = null
    private var playerMetricCollector: PlayerMetricCollector? = null
    private var metricsBufferManager: MetricsBufferManager? = null
    private var pterodactylService: PterodactylService? = null
    private var embeddedServer: EmbeddedServer? = null

    private var metricCollectionTask: BukkitTask? = null
    private var dbFlushTask: BukkitTask? = null
    private var crunchTask: BukkitTask? = null

    fun getStartTime(): Long = startTime

    fun getDatabaseDriverName(): String =
        config.getString("database.driver", "SQLITE")!!.uppercase()

    override fun onEnable() {
        instance = this

        printBanner()
        saveDefaultConfig()

        serverMetricCollector = ServerMetricCollector(this).also { it.start() }
        playerMetricCollector = PlayerMetricCollector(this).also { it.start() }

        val dbDriver = config.getString("database.driver", "SQLITE") ?: "SQLITE"
        val dbFlushInterval = config.getLong("ingestion.db-flush-interval-seconds", 30)
        val dbConfigMap = HashMap<String, Any?>()
        val dbSec: ConfigurationSection? = config.getConfigurationSection("database")
        if (dbSec != null) {
            for (k in dbSec.getKeys(false)) {
                if (dbSec.isConfigurationSection(k)) {
                    val subSec = dbSec.getConfigurationSection(k)
                    if (subSec != null) {
                        val subMap = HashMap<String, Any?>()
                        for (sk in subSec.getKeys(true)) {
                            subMap[sk] = subSec.get(sk)
                        }
                        dbConfigMap[k] = subMap
                    }
                } else {
                    dbConfigMap[k] = dbSec.get(k)
                }
            }
        }

        try {
            val dbManager = DatabaseManager(
                dbDriver,
                dataFolder,
                dbConfigMap,
                dbFlushInterval
            )
            setDatabaseProvider(dbManager)
            metricsBufferManager = MetricsBufferManager(this).also { it.start() }
        } catch (e: Exception) {
            logger.severe("Failed to construct DatabaseManager: ${e.message}")
            e.printStackTrace()
        }

        setAnalysisEngine(StatisticsEngine())

        val pterodactylEnabled = config.getBoolean("pterodactyl.enabled", false)
        val pterodactylUrl = config.getString("pterodactyl.url", "") ?: ""
        val pterodactylApiToken = config.getString("pterodactyl.api-token", "") ?: ""
        val pterodactylServerId = config.getString("pterodactyl.server-id", "") ?: ""
        pterodactylService = PterodactylService(
            pterodactylEnabled,
            pterodactylUrl,
            pterodactylApiToken,
            pterodactylServerId,
            logger
        )

        val port = config.getInt("server.port", 8080)
        embeddedServer = EmbeddedServer(
            port,
            databaseProvider!!,
            serverMetricCollector!!,
            analysisEngine!!,
            pterodactylService!!
        )
        try {
            embeddedServer?.start()
            logger.info("ASPA Embedded HTTP server started on port $port")
        } catch (e: Exception) {
            logger.severe("Failed to start Embedded HTTP server: ${e.message}")
        }

        server.pluginManager.registerEvents(playerMetricCollector!!, this)

        getCommand("aspa")?.let {
            it.setExecutor(this)
            it.tabCompleter = this
        }

        startSchedulers()

        logger.info("ASPA Core & Ingestion modules loaded successfully.")
    }

    override fun onDisable() {
        stopSchedulers()

        if (embeddedServer != null) {
            try {
                embeddedServer?.stop()
                logger.info("ASPA Embedded HTTP server stopped.")
            } catch (e: Exception) {
                logger.severe("Error stopping Embedded HTTP server: ${e.message}")
            }
        }

        if (metricsBufferManager != null) {
            try {
                metricsBufferManager?.shutdown()
                logger.info("MetricsBufferManager cleanly shut down and flushed.")
            } catch (e: Exception) {
                logger.severe("Error during MetricsBufferManager shutdown: ${e.message}")
            }
        }

        if (databaseProvider != null) {
            try {
                databaseProvider?.shutdown()
                logger.info("DatabaseProvider cleanly shut down.")
            } catch (e: Exception) {
                logger.severe("Error during DatabaseProvider shutdown: ${e.message}")
            }
        }

        instance = null
        logger.info("ASPA Core modules disabled cleanly.")
    }

    fun getDatabaseProvider(): DatabaseProvider? = databaseProvider

    fun getMetricsBufferManager(): MetricsBufferManager? = metricsBufferManager

    fun setDatabaseProvider(databaseProvider: DatabaseProvider?) {
        this.databaseProvider = databaseProvider
        if (databaseProvider != null) {
            try {
                databaseProvider.initialize()
                logger.info(
                    "DatabaseProvider ${databaseProvider.javaClass.simpleName} registered and initialized successfully."
                )
            } catch (e: Exception) {
                logger.severe("Failed to initialize DatabaseProvider: ${e.message}")
            }
        }
    }

    fun getAnalysisEngine(): AnalysisEngine? = analysisEngine

    fun setAnalysisEngine(analysisEngine: AnalysisEngine?) {
        this.analysisEngine = analysisEngine
        if (analysisEngine != null) {
            logger.info("AnalysisEngine ${analysisEngine.javaClass.simpleName} registered successfully.")
        }
    }

    fun getServerMetricCollector(): ServerMetricCollector? = serverMetricCollector

    fun getPlayerMetricCollector(): PlayerMetricCollector? = playerMetricCollector

    fun getPterodactylService(): PterodactylService? = pterodactylService

    private fun printBanner() {
        logger.info(
            "\n" +
                "    ___   _____ ____  ___ \n" +
                "   /   | / ___// __ \\/   |\n" +
                "  / /| | \\__ \\/ /_/ / /| |\n" +
                " / ___ |___/ / ____/ ___ |\n" +
                "/_/  |_/____/_/   /_/  |_|\n" +
                "  Advanced Server Performance Analysis"
        )
    }

    private fun startSchedulers() {
        stopSchedulers()

        val collectInterval = config.getInt("ingestion.collect-interval-seconds", 10)
        metricCollectionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this,
            Runnable {
                val collector = serverMetricCollector ?: return@Runnable
                collector.collectServerMetrics()
                    .thenAccept { record ->
                        metricsBufferManager?.submitServerMetric(record)
                    }
                    .exceptionally { ex ->
                        logger.severe("Failed to collect server metrics: ${ex.message}")
                        null
                    }
            },
            20L * collectInterval,
            20L * collectInterval
        )

        // dbFlushTask is now fully handled by MetricsBufferManager

        val crunchInterval = config.getInt("analysis.crunch-interval-minutes", 15)
        crunchTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            this,
            Runnable { runAnalysisCrunching() },
            20L * 60L * crunchInterval,
            20L * 60L * crunchInterval
        )
    }

    private fun stopSchedulers() {
        metricCollectionTask?.cancel()
        metricCollectionTask = null

        dbFlushTask?.cancel()
        dbFlushTask = null

        crunchTask?.cancel()
        crunchTask = null
    }

    fun runAnalysisCrunching() {
        val analysis = analysisEngine
        val provider = databaseProvider
        if (analysis == null || provider == null) {
            return
        }

        logger.info("Starting historical data analysis crunching...")

        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000L)

        provider.getServerMetricsHistory(oneDayAgo, now)
            .thenCompose { history ->
                logger.info("Loaded ${history.size} server metric records. Running anomaly detection...")
                analysis.detectAnomalies(history)
            }
            .thenCompose { anomalies ->
                logger.info("Detected ${anomalies.size} anomalies. Saving to database...")
                val futures = anomalies.map { anomaly -> provider.saveAnomaly(anomaly) }.toTypedArray()
                CompletableFuture.allOf(*futures)
            }
            .thenCompose {
                val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
                provider.getAllSessions(sevenDaysAgo, now)
            }
            .thenCompose { sessions ->
                logger.info("Loaded ${sessions.size} player sessions. Running activity forecasting and retention analysis...")
                val forecastFuture: CompletableFuture<ForecastResult> = analysis.forecastActivity(sessions)
                val retentionFuture: CompletableFuture<RetentionReport> = analysis.calculateRetentionMetrics(sessions)
                CompletableFuture.allOf(forecastFuture, retentionFuture)
            }
            .whenComplete { _, ex ->
                if (ex != null) {
                    logger.severe("Error during historical data analysis crunching: ${ex.message}")
                } else {
                    logger.info("Historical data analysis crunching completed successfully.")
                }
            }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("aspa.admin")) {
            sender.sendMessage("§cYou do not have permission to execute this command!")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§eUsage: /aspa [reload|status]")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                reloadConfig()
                startSchedulers()
                sender.sendMessage("§a[ASPA] Configuration reloaded successfully!")
            }
            "status" -> showStatus(sender)
            else -> sender.sendMessage("§cUnknown subcommand! Usage: /aspa [reload|status]")
        }
        return true
    }

    private fun showStatus(sender: CommandSender) {
        sender.sendMessage("§a[ASPA] Collecting current server status...")
        val collector = serverMetricCollector
        if (collector == null) {
            sender.sendMessage("§cServerMetricCollector is not running!")
            return
        }
        collector.collectServerMetrics().thenAccept { record ->
            sender.sendMessage("§2=== ASPA Server Status ===")
            sender.sendMessage(String.format("§aTPS: §f%.2f", record.tps))
            sender.sendMessage(String.format("§aMSPT: §f%.2f ms", record.mspt))
            sender.sendMessage(String.format("§aCPU Usage: §f%.1f%%", record.cpuUsage))
            sender.sendMessage(String.format("§aRAM: §f%d MB / %d MB", record.ramUsedMb, record.ramMaxMb))
            sender.sendMessage(String.format("§aOnline Players: §f%d", record.onlinePlayers))
            sender.sendMessage(String.format("§aLoaded Chunks: §f%d", record.loadedChunks))
            sender.sendMessage(
                String.format(
                    "§aMonsters: §f%d",
                    record.entityCounts?.getOrDefault("monsters", 0) ?: 0
                )
            )
            sender.sendMessage(
                String.format(
                    "§aAnimals: §f%d",
                    record.entityCounts?.getOrDefault("animals", 0) ?: 0
                )
            )
            sender.sendMessage(
                String.format(
                    "§aTile Entities: §f%d",
                    record.entityCounts?.getOrDefault("tileEntities", 0) ?: 0
                )
            )
            sender.sendMessage(
                String.format(
                    "§aDatabase: §f%s",
                    if (databaseProvider != null) "§2ONLINE (${databaseProvider!!.javaClass.simpleName})" else "§cOFFLINE"
                )
            )
            sender.sendMessage(
                String.format(
                    "§aAnalysis Engine: §f%s",
                    if (analysisEngine != null) "§2ONLINE (${analysisEngine!!.javaClass.simpleName})" else "§cOFFLINE"
                )
            )
        }.exceptionally { ex ->
            sender.sendMessage("§cFailed to collect status: ${ex.message}")
            null
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val completions = ArrayList<String>()
            val input = args[0].lowercase()
            for (sub in arrayOf("reload", "status")) {
                if (sub.startsWith(input)) {
                    completions.add(sub)
                }
            }
            return completions
        }
        return emptyList()
    }

    companion object {
        @JvmStatic
        private var instance: ASPA? = null

        @JvmStatic
        fun getInstance(): ASPA? = instance
    }
}
