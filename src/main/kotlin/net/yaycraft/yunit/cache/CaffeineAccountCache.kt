package net.yaycraft.yunit.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.yaycraft.yunit.config.CacheConfig
import net.yaycraft.yunit.model.YunitAccount
import java.util.UUID
import java.util.concurrent.TimeUnit

class CaffeineAccountCache(config: CacheConfig) : IAccountCache {

    private val cache: Cache<UUID, YunitAccount> = Caffeine.newBuilder()
        .expireAfterWrite(config.expireAfterWriteSeconds, TimeUnit.SECONDS)
        .maximumSize(config.maximumSize)
        .recordStats()
        .build()

    override fun get(uuid: UUID): YunitAccount? {
        return cache.getIfPresent(uuid)
    }

    override fun put(uuid: UUID, account: YunitAccount) {
        cache.put(uuid, account)
    }

    override fun invalidate(uuid: UUID) {
        cache.invalidate(uuid)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun stats(): String {
        val stats = cache.stats()
        return "CacheStats(hitRate=${stats.hitRate()}, evictionCount=${stats.evictionCount()})"
    }
}
