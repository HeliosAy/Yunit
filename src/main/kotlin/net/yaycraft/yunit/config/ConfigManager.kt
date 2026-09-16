package net.yaycraft.yunit.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {

    fun load(): PluginConfig {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val config: FileConfiguration = plugin.config

        val databaseConfig = DatabaseConfig(
            host = config.getString("database.host") ?: "localhost",
            port = config.getInt("database.port", 3306),
            database = config.getString("database.database") ?: "yunit",
            username = config.getString("database.username") ?: "root",
            password = config.getString("database.password") ?: "",
            useSSL = config.getBoolean("database.use-ssl", false),
            poolSize = config.getInt("database.pool.maximum-pool-size", 15),
            minimumIdle = config.getInt("database.pool.minimum-idle", 3),
            connectionTimeout = config.getLong("database.pool.connection-timeout", 5000),
            idleTimeout = config.getLong("database.pool.idle-timeout", 300000),
            maxLifetime = config.getLong("database.pool.max-lifetime", 600000)
        )

        val rawRedisPassword = config.getString("redis.password")
        val redisPassword = if (rawRedisPassword.isNullOrBlank()) null else rawRedisPassword

        val redisConfig = RedisConfig(
            enabled = config.getBoolean("redis.enabled", false),
            host = config.getString("redis.host") ?: "localhost",
            port = config.getInt("redis.port", 6379),
            password = redisPassword
        )

        val cacheConfig = CacheConfig(
            expireAfterWriteSeconds = config.getLong("cache.expire-after-write-seconds", 30),
            maximumSize = config.getLong("cache.maximum-size", 2000)
        )

        val safetyConfig = SafetyConfig(
            transactionCooldownMs = config.getLong("safety.transaction-cooldown-ms", 500),
            healthCheckIntervalSeconds = config.getLong("safety.health-check-interval-seconds", 10).coerceAtLeast(1),
            recoveryAutoRefund = config.getBoolean("safety.recovery-auto-refund", true)
        )

        val serverName = config.getString("server-name")?.trim()?.takeIf { it.isNotEmpty() }?.take(64) ?: "survival"
        val currencySymbol = config.getString("currency.symbol") ?: "*"
        val currencyName = config.getString("currency.name") ?: "Yunit"
        val debug = config.getBoolean("debug", false)

        return PluginConfig(
            database = databaseConfig,
            redis = redisConfig,
            cache = cacheConfig,
            safety = safetyConfig,
            serverName = serverName,
            currencySymbol = currencySymbol,
            currencyName = currencyName,
            debug = debug
        )
    }
}
