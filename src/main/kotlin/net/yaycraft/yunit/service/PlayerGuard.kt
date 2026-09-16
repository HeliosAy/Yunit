package net.yaycraft.yunit.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.sync.Mutex
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Oyuncu bazlı Mutex ve cooldown yönetimini sağlar.
 *
 */
class PlayerGuard(private val cooldownMs: Long) {

    private val locks: Cache<UUID, Mutex> = Caffeine.newBuilder()
        .weakValues()
        .build()

    private val lastActions: Cache<UUID, Long> = Caffeine.newBuilder()
        .expireAfterWrite(cooldownMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        .build()

    fun lockFor(uuid: UUID): Mutex =
        locks.get(uuid) { Mutex() }

    /**
     * Oyuncunun cooldown'unu almaya çalışır.
     *
     * @return Cooldown alınabiliyorsa `true`, hala aktifse `false`.
     */
    fun tryAcquireCooldown(uuid: UUID): Boolean {
        if (cooldownMs <= 0) return true

        val now = System.currentTimeMillis()
        var allowed = false

        lastActions.asMap().compute(uuid) { _, last ->
            if (last == null || now - last >= cooldownMs) {
                allowed = true
                now
            } else {
                last
            }
        }

        return allowed
    }
}