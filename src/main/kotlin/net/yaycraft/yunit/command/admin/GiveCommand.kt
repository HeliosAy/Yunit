package net.yaycraft.yunit.command.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.MessageUtil
import net.yaycraft.yunit.util.format
import net.yaycraft.yunit.util.toBigDecimalSafe
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

class GiveCommand(
    private val economyService: IEconomyService,
    private val config: PluginConfig,
    private val messageUtil: MessageUtil,
    private val scope: CoroutineScope
) : ISubCommand {

    override val name = "give"
    override val usage = "/yunitadmin give <oyuncu> <miktar>"
    override val permission = "yunit.admin"

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            messageUtil.sendError(sender, "Kullanım: $usage")
            return
        }

        val targetName = args[0]
        val amount = args[1].toBigDecimalSafe()
        if (amount == null) {
            messageUtil.sendError(sender, "Geçersiz miktar: ${args[1]}")
            return
        }

        @Suppress("DEPRECATION")
        val target = Bukkit.getOfflinePlayer(targetName)

        scope.launch {
            try {
                economyService.getOrCreateAccount(target.uniqueId, target.name ?: targetName)

                val result = economyService.deposit(
                    target.uniqueId, amount, TransactionType.ADMIN_GIVE,
                    "Admin Ekleme", sender.name
                )

                when (result) {
                    is TransactionResult.Success ->
                        messageUtil.sendSuccess(sender, "$targetName yeni bakiye: ${result.account.balance.format()} ${config.currencySymbol}")
                    is TransactionResult.Failure ->
                        messageUtil.sendError(sender, "İşlem başarısız: ${result.message}")
                }
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
