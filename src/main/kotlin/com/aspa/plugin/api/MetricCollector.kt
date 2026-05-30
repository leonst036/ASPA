package com.aspa.plugin.api

import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import java.util.concurrent.CompletableFuture

interface MetricCollector {
    /**
     * Gathers a snapshot of all active server performance metrics.
     * Guaranteed to compile CPU, TPS, MSPT, RAM, Chunks, and Entity counts asynchronously.
     */
    fun collectServerMetrics(): CompletableFuture<ServerMetricsRecord>

    /**
     * Invoked when a player joins the server to begin tracking session state.
     */
    fun handlePlayerJoin(uuid: String, username: String, ipAddress: String)

    /**
     * Invoked when a player leaves to calculate session length, log statistics, and persist session metrics.
     */
    fun handlePlayerQuit(uuid: String): CompletableFuture<PlayerSessionRecord>
}
