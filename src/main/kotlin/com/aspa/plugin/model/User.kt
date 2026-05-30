package com.aspa.plugin.model

class User() {
    var username: String = ""
    var passwordHash: String = ""
    var role: String = "USER"
    var permissions: List<String> = emptyList()
    var token: String? = null

    constructor(
        username: String,
        passwordHash: String,
        role: String,
        permissions: List<String>,
        token: String? = null
    ) : this() {
        this.username = username
        this.passwordHash = passwordHash
        this.role = role
        this.permissions = permissions
        this.token = token
    }
}
