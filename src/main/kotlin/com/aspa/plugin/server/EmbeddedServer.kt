package com.aspa.plugin.server

import com.aspa.plugin.api.AnalysisEngine
import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.api.MetricCollector
import com.aspa.plugin.collector.MetricsCache
import com.aspa.plugin.pterodactyl.PterodactylService
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location

class EmbeddedServer(
    private val port: Int,
    private val databaseProvider: DatabaseProvider,
    private val metricCollector: MetricCollector,
    private val analysisEngine: AnalysisEngine,
    private val pterodactylService: PterodactylService,
    private val metricsCache: MetricsCache
) {
    private var app: Javalin? = null

    fun start() {
        app = Javalin.create { config ->
            config.staticFiles.add { staticFiles ->
                staticFiles.hostedPath = "/"
                staticFiles.directory = "/web"
                staticFiles.location = Location.CLASSPATH
                staticFiles.precompress = false
            }
        }

        app!!.before("/api/v1/*") { ctx ->
            if (ctx.method().name == "OPTIONS") {
                return@before
            }
            val path = ctx.path()
            if (path == "/api/v1/setup/status" || path == "/api/v1/setup" || path == "/api/v1/login") {
                return@before
            }

            val authHeader = ctx.header("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.status(401).json(ErrorResponse("Unauthorized: Missing or invalid Authorization header"))
                return@before
            }
            val token = authHeader.substring(7).trim()
            val optUser = databaseProvider.getUserByToken(token).get()
            if (optUser.isPresent) {
                val user = optUser.get()
                ctx.attribute("user_username", user.username)
                ctx.attribute("user_role", user.role)
                ctx.attribute("user_permissions", user.permissions)
            } else {
                ctx.status(401).json(ErrorResponse("Unauthorized: Invalid session token"))
                return@before
            }
        }

        app!!.exception(Exception::class.java) { e, ctx ->
            ctx.status(500).json(ErrorResponse("Internal Server Error: ${e.message}"))
            e.printStackTrace()
        }

        // Register Controllers
        AuthController(databaseProvider).register(app!!)
        MetricController(databaseProvider, metricCollector, metricsCache).register(app!!)
        PlayerController(databaseProvider, analysisEngine).register(app!!)
        PterodactylController(pterodactylService).register(app!!)
        StaticController().register(app!!)

        app!!.start(port)
    }

    fun stop() {
        app?.stop()
    }
}
