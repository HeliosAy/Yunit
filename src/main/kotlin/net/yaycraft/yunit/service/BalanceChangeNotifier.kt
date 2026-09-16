package net.yaycraft.yunit.service

import net.yaycraft.yunit.cache.IAccountCache
import net.yaycraft.yunit.redis.RedisManager
import java.util.UUID

/**
 * Bakiye değiştiğinde yerel önbelleği temizler ve diğer sunuculara haber verir.
 * MUTLAKA transaction commit edildikten SONRA çağrılmalıdır; aksi halde
 * commit öncesi eski değer tekrar önbelleğe yazılabilir.
 */
class BalanceChangeNotifier(
    private val cache: IAccountCache,
    private val redisManager: RedisManager?
) {
    fun notifyChanged(uuid: UUID) {
        cache.invalidate(uuid)
        redisManager?.publishUpdate(uuid)
    }
}
