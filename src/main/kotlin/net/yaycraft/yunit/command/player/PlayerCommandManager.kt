package net.yaycraft.yunit.command.player

import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.util.MessageUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * /yunit komutu için ana yönetici.
 * Oyuncu komutlarını alt komutlara yönlendirir.
 * Argümansız kullanımda varsayılan olarak bakiye gösterir.
 */
class PlayerCommandManager(
    private val messageUtil: MessageUtil,
    subCommands: List<ISubCommand>
) : CommandExecutor, TabCompleter {

    private val subCommandMap: Map<String, ISubCommand> = subCommands.associateBy { it.name }
    private val defaultCommand: ISubCommand? = subCommandMap["balance"]

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            messageUtil.sendRaw(sender, messageUtil.getMessage("player-only", "<red>Bu komutu sadece oyuncular kullanabilir.</red>"))
            return true
        }

        if (!sender.hasPermission("yunit.use")) {
            messageUtil.sendRaw(sender, messageUtil.getMessage("no-permission", "<red>Bunun için yetkiniz yok.</red>"))
            return true
        }

        // Argüman yoksa varsayılan komut (bakiye) çalıştır
        if (args.isEmpty()) {
            defaultCommand?.execute(sender, emptyList())
                ?: messageUtil.sendError(sender, "Kullanım: /yunit <balance>")
            return true
        }

        val subCommandName = args[0].lowercase()
        val subCommand = subCommandMap[subCommandName]

        if (subCommand == null) {
            // Bilinmeyen alt komut varsayılan olarak bakiyeyi göster
            defaultCommand?.execute(sender, emptyList())
                ?: messageUtil.sendError(sender, "Bilinmeyen komut.")
            return true
        }

        if (!sender.hasPermission(subCommand.permission)) {
            messageUtil.sendRaw(sender, messageUtil.getMessage("no-permission", "<red>Bunun için yetkiniz yok.</red>"))
            return true
        }

        subCommand.execute(sender, args.drop(1))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (sender !is Player || !sender.hasPermission("yunit.use")) return emptyList()

        if (args.size == 1) {
            return subCommandMap.keys
                .filter { sender.hasPermission(subCommandMap[it]!!.permission) }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }

        val subCommand = subCommandMap[args[0].lowercase()]
        if (subCommand != null && sender.hasPermission(subCommand.permission)) {
            return subCommand.tabComplete(sender, args.drop(1))
        }

        return emptyList()
    }
}
