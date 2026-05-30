package com.aspa.plugin.api

import com.aspa.plugin.model.ForecastResult
import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.RetentionReport
import com.aspa.plugin.model.ServerMetricsRecord
import java.util.concurrent.CompletableFuture

interface AnalysisEngine {
    /**
     * Runs anomaly detection over historical data to correlate MSPT/TPS drops with event spikes.
     */
    fun detectAnomalies(history: List<ServerMetricsRecord>): CompletableFuture<List<PerformanceAnomaly>>

    /**
     * Forecasts the upcoming week's peak hours and player count using a historical punchcard matrix.
     */
    fun forecastActivity(history: List<PlayerSessionRecord>): CompletableFuture<ForecastResult>

    /**
     * Calculates cohort retention rates (daily/weekly/monthly) and churn metrics based on concrete sessions.
     */
    fun calculateRetentionMetrics(history: List<PlayerSessionRecord>): CompletableFuture<RetentionReport>
}
