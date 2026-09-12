package net.yaycraft.yunit.redis

import net.yaycraft.yunit.config.RedisConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

class RedisManager(
    private val config: RedisConfig,
    private val logger: Logger,
    private val onInvalidate: (UUID) -> Unit
) {
    private var jedisPool: JedisPool? = null
    private var pubSubThread: Thread? = null
    private var jedisPubSub: JedisPubSub? = null

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

            jedisPool = if (config.password != null) {
                JedisPool(poolConfig, config.host, config.port, 2000, config.password)
            } else {
                JedisPool(poolConfig, config.host, config.port, 2000)
            }

            jedisPool!!.resource.use { jedis ->
                jedis.ping()
                logger.info("Redis bağlantısı başarıyla kuruldu: ${config.host}:${config.port}")
            }

            startSubscriber()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Redis bağlantısı kurulamadı! Lütfen bilgileri kontrol edin.", e)
            jedisPool?.close()
            jedisPool = null
        }
    }

    private fun startSubscriber() {
        pubSubThread = thread(start = true, isDaemon = true, name = "Yunit-Redis-Subscriber") {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    jedisPool?.resource?.use { jedis ->
                        jedisPubSub = object : JedisPubSub() {
                            override fun onMessage(channel: String?, message: String?) {
                                if (channel == channelName && message != null) {
                                    try {
                                        val uuid = UUID.fromString(message)
                                        onInvalidate(uuid)
                                    } catch (e: IllegalArgumentException) {
                                        logger.warning("Redis'ten geçersiz UUID alındı: $message")
                                    }
                                }
                            }
                        }
                        logger.info("Redis sub ($channelName) başlatıldı.")
                        jedis.subscribe(jedisPubSub, channelName)
                    }
                } catch (e: Exception) {
                    if (!Thread.currentThread().isInterrupted) {
                        logger.warning("Redis sub koptu, 5 saniye sonra yeniden denenecek... (${e.message})")
                        try {
                            Thread.sleep(5000)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            }
        }
    }

    fun publishUpdate(uuid: UUID) {
        if (!config.enabled || jedisPool == null) return
        
        thread(start = true, isDaemon = true) {
            try {
                jedisPool?.resource?.use { jedis ->
                    jedis.publish(channelName, uuid.toString())
                }
            } catch (e: Exception) {
                logger.warning("Redis mesajı gönderilemedi: ${e.message}")
            }
        }
    }

    fun shutdown() {
        pubSubThread?.interrupt()
        jedisPubSub?.unsubscribe()
        
        jedisPool?.close()
        logger.info("Redis bağlantıları kapatıldı.")
    }
}
