package net.yaycraft.yunit.redis

import net.yaycraft.yunit.config.RedisConfig
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

class RedisManager(
    private val config: RedisConfig,
    private val logger: Logger,
    private val onInvalidate: (UUID) -> Unit
) {
    @Volatile
    private var jedisPool: JedisPool? = null
    private var pubSubThread: Thread? = null

    @Volatile
    private var jedisPubSub: JedisPubSub? = null

    @Volatile
    private var running = false

    private var publishExecutor: ExecutorService? = null

    private val channelName = "yunit:sync"

    fun connect() {
        if (!config.enabled) return

        try {
            val poolConfig = JedisPoolConfig().apply {
                maxTotal = 8
                maxIdle = 8
                minIdle = 2
                testOnBorrow = true
            }

            val pool = if (config.password != null) {
                JedisPool(poolConfig, config.host, config.port, 2000, config.password)
            } else {
                JedisPool(poolConfig, config.host, config.port, 2000)
            }
            jedisPool = pool

            pool.resource.use { jedis ->
                jedis.ping()
                logger.info("Redis bağlantısı başarıyla kuruldu: ${config.host}:${config.port}")
            }

            publishExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "Yunit-Redis-Publisher").apply { isDaemon = true }
            }
            running = true
            startSubscriber()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Redis bağlantısı kurulamadı! Lütfen bilgileri kontrol edin.", e)
            jedisPool?.close()
            jedisPool = null
        }
    }

    private fun startSubscriber() {
        pubSubThread = thread(start = true, isDaemon = true, name = "Yunit-Redis-Subscriber") {
            while (running) {
                try {
                    val pool = jedisPool ?: break
                    pool.resource.use { jedis ->
                        val pubSub = object : JedisPubSub() {
                            override fun onMessage(channel: String?, message: String?) {
                                if (channel == channelName && message != null) {
                                    try {
                                        onInvalidate(UUID.fromString(message.trim()))
                                    } catch (e: IllegalArgumentException) {
                                        logger.warning("Redis'ten geçersiz UUID alındı: $message")
                                    }
                                }
                            }
                        }
                        jedisPubSub = pubSub
                        logger.info("Redis sub ($channelName) başlatıldı.")
                        jedis.subscribe(pubSub, channelName)
                    }
                } catch (e: Exception) {
                    if (running) {
                        logger.warning("Redis sub koptu, 5 saniye sonra yeniden denenecek... (${e.message})")
                        try {
                            Thread.sleep(5000)
                        } catch (ie: InterruptedException) {
                            break
                        }
                    }
                }
            }
        }
    }

    fun publishUpdate(uuid: UUID) {
        val executor = publishExecutor ?: return
        if (!config.enabled || jedisPool == null) return

        try {
            executor.execute {
                try {
                    jedisPool?.resource?.use { jedis ->
                        jedis.publish(channelName, uuid.toString())
                    }
                } catch (e: Exception) {
                    logger.warning("Redis mesajı gönderilemedi: ${e.message}")
                }
            }
        } catch (ignored: RejectedExecutionException) {
        }
    }

    fun shutdown() {
        if (!config.enabled) return
        running = false

        try {
            jedisPubSub?.takeIf { it.isSubscribed }?.unsubscribe()
        } catch (e: Exception) {
            logger.fine("Redis unsubscribe hatası: ${e.message}")
        }
        pubSubThread?.interrupt()

        publishExecutor?.let { executor ->
            executor.shutdown()
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        jedisPool?.close()
        jedisPool = null
        logger.info("Redis bağlantıları kapatıldı.")
    }
}
