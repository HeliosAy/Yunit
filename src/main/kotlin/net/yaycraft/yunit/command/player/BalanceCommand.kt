package net.yaycraft.yunit.command.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.MessageUtil
import net.yaycraft.yunit.util.format
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * /yunit balance — Oyuncunun kendi bakiyesini gösterir.
 * /yunit komutu argümansız kullanıldığında da bu çalışır.
 */
class BalanceCommand(
    private val economyService: IEconomyService,
    private val config: PluginConfig,
    private val messageUtil: MessageUtil,
    private val scope: CoroutineScope
) : ISubCommand {

    override val name = "balance"
    override val usage = "/yunit [balance]"
    override val permission = "yunit.use"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (sender !is Player) return

        scope.launch {
            try {
                val balance = economyService.getBalance(sender.uniqueId)

                val msgTemplate = messageUtil.getMessage("balance", "Bakiyen: <green>{balance}</green> {currency}")
                val finalMsg = msgTemplate
                    .replace("{balance}", balance.format())
                    .replace("{currency}", config.currencySymbol)

                messageUtil.send(sender, finalMsg)
            } catch (e: Exception) {
                val msgTemplate = messageUtil.getMessage("error-occurred", "<red>Bir hata oluştu: {error}</red>")
                messageUtil.sendRaw(sender, msgTemplate.replace("{error}", e.message ?: "Bilinmeyen hata"))
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String>? = emptyList()
}
