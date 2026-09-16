package net.yaycraft.yunit.command.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.command.PlayerResolver
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.MessageUtil
import net.yaycraft.yunit.util.format
import org.bukkit.command.CommandSender

/**
 * /yunitadmin lookup <oyuncu> — Başka bir oyuncunun bakiyesini sorgular.
 */
class LookupCommand(
    private val economyService: IEconomyService,
    private val config: PluginConfig,
    private val messageUtil: MessageUtil,
    private val resolver: PlayerResolver,
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

        val lookup = resolver.prepare(args[0])

        scope.launch {
            try {
                val target = resolver.resolve(lookup)
                if (target == null) {
                    messageUtil.sendError(sender, "Oyuncu bulunamadı: ${messageUtil.escape(args[0])}")
                    return@launch
                }

                val account = economyService.findAccount(target.uuid)
                if (account == null) {
                    messageUtil.send(sender, "${messageUtil.escape(target.name)} için henüz Yunit hesabı yok.")
                    return@launch
                }
                messageUtil.send(
                    sender,
                    "${messageUtil.escape(account.username)} bakiyesi: <green>${account.balance.format()}</green> ${config.currencySymbol} <dark_gray>(${account.uuid})</dark_gray>"
                )
            } catch (e: Exception) {
                messageUtil.sendError(sender, "Hata: ${messageUtil.escape(e.message ?: "Bilinmeyen hata")}")
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String>? {
        if (args.size == 1) return null
        return emptyList()
    }
}
