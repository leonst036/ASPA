package com.aspa.plugin.server

import io.javalin.http.Context

data class ErrorResponse(val error: String)

fun Context.isAdmin(): Boolean {
    val role = this.attribute<String>("user_role") ?: return false
    return role == "ADMIN"
}

fun Context.hasPermission(requiredPerm: String): Boolean {
    val role = this.attribute<String>("user_role") ?: return false
    if (role == "ADMIN") return true
    val permissions = this.attribute<List<String>>("user_permissions") ?: return false
    return permissions.contains(requiredPerm)
}
