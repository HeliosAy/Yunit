package net.yaycraft.yunit.hook

import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.hook.papi.YunitPlaceholderExpansion
import net.yaycraft.yunit.service.IEconomyService
import org.bukkit.Server
import java.util.logging.Logger

class HookManager(
    private val server: Server,
    private val logger: Logger,
    private val economyService: IEconomyService,
    private val pluginConfig: PluginConfig
) {
    /**
     * Tüm harici eklentileri sırasıyla başlatır.
     */
    fun registerHooks() {
        registerPlaceholderAPI()
    }



    private fun registerPlaceholderAPI() {
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            try {
                YunitPlaceholderExpansion(economyService, pluginConfig).register()
            } catch (e: Exception) {
                logger.warning("PlaceholderAPI hook başlatılırken hata oluştu: ${e.message}")
            }
        }
    }

    
}
