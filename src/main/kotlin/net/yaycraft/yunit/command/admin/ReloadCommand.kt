package net.yaycraft.yunit.command.admin

import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.config.ConfigManager
import net.yaycraft.yunit.config.LangManager
import net.yaycraft.yunit.config.PluginConfig
import net.yaycraft.yunit.util.MessageUtil
import org.bukkit.command.CommandSender

/**
 * /yunitadmin reload — oyun içinde görünen metinleri yeniler:
 * - lang.yml (tüm mesajlar ve prefix)
 * - config.yml -> currency.symbol / currency.name
 *
 * Veritabanı, Redis, önbellek ve sunucu adı gibi ayarlar çalışırken değiştirilemez; restart gerekir.
 */
class ReloadCommand(
    private val configManager: ConfigManager,
    private val langManager: LangManager,
    private val config: PluginConfig,
    private val messageUtil: MessageUtil
) : ISubCommand {

    override val name = "reload"
    override val usage = "/yunitadmin reload"
    override val permission = "yunit.admin"

    override fun execute(sender: CommandSender, args: List<String>) {
        val langOk = langManager.load()

        val fresh = configManager.load()
        config.currencySymbol = fresh.currencySymbol
        config.currencyName = fresh.currencyName

        if (langOk) {
            messageUtil.sendSuccess(sender, messageUtil.getMessage("reloaded", "Mesajlar ve görünüm ayarları yenilendi."))
        } else {
            messageUtil.sendError(sender, messageUtil.getMessage("reload-failed", "lang.yml okunamadı, konsolu kontrol edin."))
        }
        messageUtil.send(sender, messageUtil.getMessage("reload-note", "<gray>Veritabanı/Redis ayarları için sunucuyu yeniden başlatın."))
    }

    override fun tabComplete(sender: CommandSender, args: List<String>): List<String> = emptyList()
}
