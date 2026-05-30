package com.aspa.plugin.api

import com.aspa.plugin.model.PerformanceAnomaly
import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import com.aspa.plugin.model.ServerMetricsRecord
import com.aspa.plugin.model.User
import java.util.Optional
import java.util.concurrent.CompletableFuture

interface DatabaseProvider {
    /**
     * Initializes connections and creates schemas if they do not exist.
     */
    @Throws(Exception::class)
    fun initialize()

    /**
     * Closes connections and clean up resource pools.
     */
    fun shutdown()

    /**
     * Asynchronously records server performance metrics.
     */
    fun saveServerMetrics(record: ServerMetricsRecord): CompletableFuture<Void>

    /**
     * Asynchronously saves or updates player profile and session logs.
     */
    fun savePlayerSession(record: PlayerSessionRecord): CompletableFuture<Void>

    /**
     * Retrieves historical server performance logs for a specific time window.
     */
    fun getServerMetricsHistory(startEpochMs: Long, endEpochMs: Long): CompletableFuture<List<ServerMetricsRecord>>

    /**
     * Retrieves a single player's details and session logs.
     */
    fun getPlayerProfile(uuid: String): CompletableFuture<Optional<PlayerProfile>>

    /**
     * Retrieves a single player's details and session logs by name.
     */
    fun getPlayerProfileByName(username: String): CompletableFuture<Optional<PlayerProfile>>

    /**
     * Retrieves all recorded player sessions for analysis.
     */
    fun getAllSessions(startEpochMs: Long, endEpochMs: Long): CompletableFuture<List<PlayerSessionRecord>>

    /**
     * Saves calculated performance anomalies detected by the analysis engine.
     */
    fun saveAnomaly(anomaly: PerformanceAnomaly): CompletableFuture<Void>

    /**
     * Retrieves all calculated performance anomalies.
     */
    fun getAnomalies(limit: Int): CompletableFuture<List<PerformanceAnomaly>>

    /**
     * Retrieves a list of all registered web portal users.
     */
    fun getUsers(): CompletableFuture<List<User>>

    /**
     * Retrieves a single web user by username.
     */
    fun getUser(username: String): CompletableFuture<Optional<User>>

    /**
     * Retrieves a web user by their active session token.
     */
    fun getUserByToken(token: String): CompletableFuture<Optional<User>>

    /**
     * Saves or updates a web user.
     */
    fun saveUser(user: User): CompletableFuture<Void>

    /**
     * Deletes a web user by username.
     */
    fun deleteUser(username: String): CompletableFuture<Void>

    /**
     * Checks if there is at least one registered user in the database.
     */
    fun hasAnyUser(): CompletableFuture<Boolean>
}
