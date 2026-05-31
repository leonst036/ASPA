package com.aspa.plugin.server

import com.aspa.plugin.api.DatabaseProvider
import com.aspa.plugin.model.User
import com.aspa.plugin.util.PasswordHasher
import io.javalin.Javalin
import io.javalin.http.Context
import java.util.HashMap
import java.util.UUID

class AuthController(
    private val databaseProvider: DatabaseProvider
) {
    fun register(app: Javalin) {
        app.get("/api/v1/setup/status") { getSetupStatus(it) }
        app.post("/api/v1/setup") { setup(it) }
        app.post("/api/v1/login") { login(it) }
        app.get("/api/v1/users/me") { getMe(it) }
        app.get("/api/v1/users") { getUsers(it) }
        app.post("/api/v1/users") { saveUser(it) }
        app.delete("/api/v1/users/{username}") { deleteUser(it) }
    }

    private fun getSetupStatus(ctx: Context) {
        val hasUsers = databaseProvider.hasAnyUser().get()
        val response = HashMap<String, Any>()
        response["setupRequired"] = !hasUsers
        ctx.json(response)
    }

    private fun setup(ctx: Context) {
        val hasUsers = databaseProvider.hasAnyUser().get()
        if (hasUsers) {
            ctx.status(400).json(ErrorResponse("Setup has already been completed"))
            return
        }

        @Suppress("UNCHECKED_CAST")
        val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
        val username = body?.get("username")?.trim()
        val password = body?.get("password")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            ctx.status(400).json(ErrorResponse("Username and password are required"))
            return
        }

        val token = UUID.randomUUID().toString()
        val adminUser = User(
            username,
            PasswordHasher.hashPassword(password),
            "ADMIN",
            listOf("health", "analytics", "inspector", "longtime", "pterodactyl"),
            token
        )

        databaseProvider.saveUser(adminUser).get()

        val response = HashMap<String, Any>()
        response["token"] = token
        response["user"] = mapOf(
            "username" to adminUser.username,
            "role" to adminUser.role,
            "permissions" to adminUser.permissions
        )
        ctx.json(response)
    }

    private fun login(ctx: Context) {
        @Suppress("UNCHECKED_CAST")
        val body = ctx.bodyAsClass(Map::class.java) as? Map<String, String>
        val username = body?.get("username")?.trim()
        val password = body?.get("password")
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            ctx.status(400).json(ErrorResponse("Username and password are required"))
            return
        }

        val optUser = databaseProvider.getUser(username).get()
        if (!optUser.isPresent) {
            ctx.status(401).json(ErrorResponse("Invalid username or password"))
            return
        }

        val user = optUser.get()
        if (!PasswordHasher.verifyPassword(password, user.passwordHash)) {
            ctx.status(401).json(ErrorResponse("Invalid username or password"))
            return
        }

        val token = UUID.randomUUID().toString()
        user.token = token
        if (PasswordHasher.needsRehash(user.passwordHash)) {
            user.passwordHash = PasswordHasher.hashPassword(password)
        }
        databaseProvider.saveUser(user).get()

        val response = HashMap<String, Any>()
        response["token"] = token
        response["user"] = mapOf(
            "username" to user.username,
            "role" to user.role,
            "permissions" to user.permissions
        )
        ctx.json(response)
    }

    private fun getMe(ctx: Context) {
        val username = ctx.attribute<String>("user_username")
        if (username == null) {
            ctx.status(401).json(ErrorResponse("Unauthorized"))
            return
        }

        val optUser = databaseProvider.getUser(username).get()
        if (optUser.isPresent) {
            val user = optUser.get()
            val profile = HashMap<String, Any>()
            profile["username"] = user.username
            profile["role"] = user.role
            profile["permissions"] = user.permissions
            ctx.json(profile)
        } else {
            ctx.status(404).json(ErrorResponse("User not found"))
        }
    }

    private fun getUsers(ctx: Context) {
        if (!ctx.isAdmin()) {
            ctx.status(403).json(ErrorResponse("Forbidden: Administrative rights required"))
            return
        }
        val users = databaseProvider.getUsers().get()
        val response = users.map { u ->
            mapOf(
                "username" to u.username,
                "role" to u.role,
                "permissions" to u.permissions
            )
        }
        ctx.json(response)
    }

    private fun saveUser(ctx: Context) {
        if (!ctx.isAdmin()) {
            ctx.status(403).json(ErrorResponse("Forbidden: Administrative rights required"))
            return
        }

        @Suppress("UNCHECKED_CAST")
        val body = ctx.bodyAsClass(Map::class.java) as? Map<String, Any>
        val username = (body?.get("username") as? String)?.trim()
        val password = body?.get("password") as? String
        val role = body?.get("role") as? String ?: "USER"
        @Suppress("UNCHECKED_CAST")
        val permissions = body?.get("permissions") as? List<String> ?: emptyList()

        if (username.isNullOrEmpty()) {
            ctx.status(400).json(ErrorResponse("Username is required"))
            return
        }

        val optExisting = databaseProvider.getUser(username).get()
        val passwordHash = if (!password.isNullOrEmpty()) {
            PasswordHasher.hashPassword(password)
        } else {
            if (optExisting.isPresent) {
                optExisting.get().passwordHash
            } else {
                ctx.status(400).json(ErrorResponse("Password is required for new users"))
                return
            }
        }

        val user = User(
            username,
            passwordHash,
            role,
            permissions,
            optExisting.orElse(null)?.token
        )
        databaseProvider.saveUser(user).get()
        ctx.status(201).json(mapOf("success" to true))
    }

    private fun deleteUser(ctx: Context) {
        if (!ctx.isAdmin()) {
            ctx.status(403).json(ErrorResponse("Forbidden: Administrative rights required"))
            return
        }
        val usernameToDelete = ctx.pathParam("username")
        val currentUser = ctx.attribute<String>("user_username")

        if (usernameToDelete == currentUser) {
            ctx.status(400).json(ErrorResponse("You cannot delete your own account"))
            return
        }

        val users = databaseProvider.getUsers().get()
        val admins = users.filter { it.role == "ADMIN" }
        if (admins.size <= 1 && admins.any { it.username == usernameToDelete }) {
            ctx.status(400).json(ErrorResponse("Cannot delete the last remaining administrator account"))
            return
        }

        databaseProvider.deleteUser(usernameToDelete).get()
        ctx.status(204)
    }
}
