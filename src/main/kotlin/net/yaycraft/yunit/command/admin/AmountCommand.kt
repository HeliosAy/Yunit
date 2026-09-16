package net.yaycraft.yunit.command.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.command.PlayerResolver
import net.yaycraft.yunit.command.ResolvedPlayer
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.model.TransactionResult
import net.yaycraft.yunit.service.IEconomyService
import net.yaycraft.yunit.util.MessageUtil
import net.yaycraft.yunit.util.format
import net.yaycraft.yunit.util.toBigDecimalSafe
import org.bukkit.command.CommandSender
import java.math.BigDecimal

/**
 * <Admin set / give / take> komutlarının ortak akışı:
 * argüman kontrolü > oyuncuyu güvenle çözümle > hesabı hazırla > işlemi yap > sonucu bildir.
 */
abstract class AmountCommand(
    protected val economyService: IEconomyService,
    protected val config: PluginConfig,
    protected val messageUtil: MessageUtil,
    private val resolver: PlayerResolver,
    private val scope: CoroutineScope
) : ISubCommand {

    override val permission = "yunit.admin"

    /** set komutu 0'a izin verir, diğerleri vermez */
    protected open val allowZero = false

    protected abstract suspend fun perform(sender: CommandSender, target: ResolvedPlayer, amount: BigDecimal): TransactionResult

    override fun execute(sender: CommandSender, args: List<String>) {
        if (args.size < 2) {
            messageUtil.sendError(sender, "Kullanım: $usage")
            return
        }

        val amount = args[1].toBigDecimalSafe()
        if (amount == null || (!allowZero && amount.signum() == 0)) {
            messageUtil.sendError(sender, "Geçersiz miktar: ${messageUtil.escape(args[1])} (pozitif, en fazla 2 ondalık)")
            return
        }

        val lookup = resolver.prepare(args[0])

        scope.launch {
            try {
                val target = resolver.resolve(lookup)
                if (target == null) {
                    messageUtil.sendError(sender, "Oyuncu bulunamadı: ${messageUtil.escape(args[0])} (sunucuya hiç girmemiş olabilir)")
                    return@launch
                }

                economyService.getOrCreateAccount(target.uuid, target.name)

                when (val result = perform(sender, target, amount)) {
                    is TransactionResult.Success ->
                        messageUtil.sendSuccess(sender, "${messageUtil.escape(target.name)} yeni bakiye: ${result.account.balance.format()} ${config.currencySymbol}")
                    is TransactionResult.Failure ->
                        messageUtil.sendError(sender, "İşlem başarısız: ${result.message}")
                }
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
