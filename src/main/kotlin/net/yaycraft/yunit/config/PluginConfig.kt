package net.yaycraft.yunit.config

data class PluginConfig(
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val cache: CacheConfig,
    val safety: SafetyConfig,
    val serverName: String,
    val currencySymbol: String,
    val currencyName: String,
    val debug: Boolean
)

data class RedisConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val password: String?
)
