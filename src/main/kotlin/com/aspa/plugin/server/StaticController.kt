package com.aspa.plugin.server

import io.javalin.Javalin
import io.javalin.http.Context

class StaticController {
    fun register(app: Javalin) {
        app.get("/favicon.png") { getFavicon(it) }
        app.get("/favicon.ico") { getFavicon(it) }
        app.get("/icons.png") { getIcons(it) }
        app.get("/logo.png") { getLogo(it) }
        app.get("/{*path}") { getFallback(it) }
    }

    private fun getFavicon(ctx: Context) {
        EmbeddedServer::class.java.getResourceAsStream("/web/favicon.png").use { `is` ->
            if (`is` != null) {
                ctx.contentType("image/png")
                ctx.result(`is`.readBytes())
            } else {
                ctx.status(404)
            }
        }
    }

    private fun getIcons(ctx: Context) {
        EmbeddedServer::class.java.getResourceAsStream("/web/icons.png").use { `is` ->
            if (`is` != null) {
                ctx.contentType("image/png")
                ctx.result(`is`.readBytes())
            } else {
                ctx.status(404)
            }
        }
    }

    private fun getLogo(ctx: Context) {
        EmbeddedServer::class.java.getResourceAsStream("/web/logo.png").use { `is` ->
            if (`is` != null) {
                ctx.contentType("image/png")
                ctx.result(`is`.readBytes())
            } else {
                ctx.status(404)
            }
        }
    }

    private fun getFallback(ctx: Context) {
        val path = ctx.path()
        if (!path.startsWith("/api/")) {
            EmbeddedServer::class.java.getResourceAsStream("/web/index.html").use { `is` ->
                if (`is` != null) {
                    ctx.contentType("text/html")
                    ctx.result(`is`.readBytes())
                } else {
                    ctx.status(404).result("Static assets not found. Build front-end folder first.")
                }
            }
        } else {
            ctx.status(404).json(ErrorResponse("API Endpoint not found"))
        }
    }
}
