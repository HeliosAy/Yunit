package net.yaycraft.yunit.hook.papi

import kotlinx.coroutines.runBlocking
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.service.IEconomyService
import org.bukkit.OfflinePlayer
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class YunitPlaceholderExpansion(
    private val economyService: IEconomyService,
    private val config: PluginConfig
) : PlaceholderExpansion() {

    private val decimalFormat: DecimalFormat

    init {
        val symbols = DecimalFormatSymbols(Locale.US)
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        decimalFormat = DecimalFormat("#,##0.00", symbols)
    }

    override fun getIdentifier(): String {
        return "yunit"
    }

    override fun getAuthor(): String {
        return "HeliosAy"
    }

    override fun getVersion(): String {
        return "1.0"
    }

    override fun persist(): Boolean {
        return true
    }

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null

        return when (params.lowercase()) {
            "balance" -> {
                try {
                    val balance = runBlocking { economyService.getBalance(player.uniqueId) }
                    decimalFormat.format(balance)
                } catch (e: Exception) {
                    "0,00"
                }
            }
            "balance_raw" -> {
                try {
                    val balance = runBlocking { economyService.getBalance(player.uniqueId) }
                    balance.toPlainString()
                } catch (e: Exception) {
                    "0"
                }
            }
            "currency_name" -> {
                config.currencyName
            }
            "currency_symbol" -> {
                config.currencySymbol
            }
            else -> null
        }
    }
}
