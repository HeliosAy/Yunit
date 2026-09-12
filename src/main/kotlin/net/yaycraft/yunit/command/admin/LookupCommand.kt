package net.yaycraft.yunit.command.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.MessageUtil
import net.yaycraft.yunit.util.format
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * /yunitadmin lookup <oyuncu> — Başka bir oyuncunun bakiyesini sorgular.
 */
class LookupCommand(
    private val economyService: IEconomyService,
    private val config: PluginConfig,
    private val messageUtil: MessageUtil,
    private val scope: CoroutineScope
) : ISubCommand {

    override val name = "lookup"
    override val usage = "/yunitadmin lookup <oyuncu>"
    override val permission = "yunit.admin"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.isEmpty()) {
            messageUtil.sendError(sender, "Kullanım: $usage")
            return
        }

        val targetName = args[0]

        @Suppress("DEPRECATION")
        val target = Bukkit.getOfflinePlayer(targetName)

        scope.launch {
            try {
                val balance = economyService.getBalance(target.uniqueId)
                messageUtil.send(sender, "$targetName bakiyesi: <green>${balance.format()}</green> ${config.currencySymbol}")
            } catch (e: Exception) {
                messageUtil.sendError(sender, "Hata: ${e.message}")
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String>? {
        if (args.size == 1) return null
        return emptyList()
    }
}
