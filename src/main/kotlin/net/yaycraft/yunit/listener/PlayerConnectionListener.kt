package net.yaycraft.yunit.listener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.yaycraft.yunit.cache.IAccountCache
import net.yaycraft.yunit.service.IEconomyService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerConnectionListener(
    private val economyService: IEconomyService,
    private val cache: IAccountCache,
    private val scope: CoroutineScope
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val username = player.name

        // Arka planda hesabı yükle/oluştur
        scope.launch {
            try {
                economyService.getOrCreateAccount(uuid, username)
            } catch (e: Exception) {
                // Hata durumunda logla (satın alım yaparken hata alacaktır)
                player.server.logger.warning("Oyuncu hesabı yüklenirken hata oluştu: ${player.name} - ${e.message}")
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cache.invalidate(uuid)
    }
}
