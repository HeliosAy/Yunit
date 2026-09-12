package net.yaycraft.yunit.config

data class PluginConfig(
    val database: DatabaseConfig,
    val cache: CacheConfig,
    val safety: SafetyConfig,
    val serverName: String,
    val currencySymbol: String,
    val currencyName: String,
    val debug: Boolean
)
