package net.yaycraft.yunit.command.admin

import kotlinx.coroutines.CoroutineScope
import net.yaycraft.yunit.command.PlayerResolver
import net.yaycraft.yunit.command.ResolvedPlayer
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.model.TransactionType
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.MessageUtil
import org.bukkit.command.CommandSender
import java.math.BigDecimal

class GiveCommand(
    economyService: IEconomyService,
    config: PluginConfig,
    messageUtil: MessageUtil,
    resolver: PlayerResolver,
    scope: CoroutineScope
) : AmountCommand(economyService, config, messageUtil, resolver, scope) {

    override val name = "give"
    override val usage = "/yunitadmin give <oyuncu> <miktar>"

    override suspend fun perform(sender: CommandSender, target: ResolvedPlayer, amount: BigDecimal): TransactionResult {
        return economyService.deposit(
            target.uuid, amount, TransactionType.ADMIN_GIVE,
            "Admin Ekleme", sender.name
        )
    }
}
