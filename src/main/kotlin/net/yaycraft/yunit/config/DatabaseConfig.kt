package net.yaycraft.yunit.config

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val useSSL: Boolean,
    val poolSize: Int,
    val minimumIdle: Int,
    val connectionTimeout: Long,
    val idleTimeout: Long,
    val maxLifetime: Long
)
