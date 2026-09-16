package net.yaycraft.yunit.hook.papi

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.format
import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit


class YunitPlaceholderExpansion(
    private val economyService: IEconomyService,
    private val config: PluginConfig,
    private val scope: CoroutineScope,
    private val pluginVersion: String
) : PlaceholderExpansion() {

    private val lastKnown: Cache<UUID, BigDecimal> = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build()

    private val recentLoads: Cache<UUID, Boolean> = Caffeine.newBuilder()
        .expireAfterWrite(3, TimeUnit.SECONDS)
        .maximumSize(10_000)
        .build()

    override fun getIdentifier(): String = "yunit"

    override fun getAuthor(): String = "HeliosAy"

    override fun getVersion(): String = pluginVersion

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return when (params.lowercase()) {
            "balance" -> player?.let { balanceOf(it.uniqueId)?.format() ?: "..." }
            "balance_raw" -> player?.let { balanceOf(it.uniqueId)?.toPlainString() ?: "0" }
            "currency_name" -> config.currencyName
            "currency_symbol" -> config.currencySymbol
            else -> null
        }
    }

    private fun balanceOf(uuid: UUID): BigDecimal? {
        val cached = economyService.getCachedBalance(uuid)
        if (cached != null) {
            lastKnown.put(uuid, cached)
            return cached
        }

        if (recentLoads.asMap().putIfAbsent(uuid, true) == null) {
            scope.launch {
                try {
                    lastKnown.put(uuid, economyService.getBalance(uuid))
                } catch (ignored: Exception) {
                }
            }
        }
        return lastKnown.getIfPresent(uuid)
    }
}
