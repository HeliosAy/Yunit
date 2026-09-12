package net.yaycraft.yunit.command.admin

import net.yaycraft.yunit.command.ISubCommand
import net.yaycraft.yunit.util.MessageUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * /yunitadmin komutu için ana yönetici.
 * Alt komutları (give, take, set, lookup) yönlendirir.
 */
class AdminCommandManager(
    private val messageUtil: MessageUtil,
    subCommands: List<ISubCommand>
) : CommandExecutor, TabCompleter {

    private val subCommandMap: Map<String, ISubCommand> = subCommands.associateBy { it.name }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("yunit.admin")) {
            messageUtil.sendRaw(sender, messageUtil.getMessage("no-permission", "<red>Bunun için yetkiniz yok.</red>"))
            return true
        }

        if (args.isEmpty()) {
            messageUtil.send(sender, "Kullanılabilir komutlar: <yellow>${subCommandMap.keys.joinToString(", ")}</yellow>")
            return true
        }

        val subCommandName = args[0].lowercase()
        val subCommand = subCommandMap[subCommandName]

        if (subCommand == null) {
            messageUtil.sendError(sender, "Bilinmeyen komut: <white>$subCommandName</white>")
            messageUtil.send(sender, "Kullanılabilir: <yellow>${subCommandMap.keys.joinToString(", ")}</yellow>")
            return true
        }

        subCommand.execute(sender, args.drop(1))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (!sender.hasPermission("yunit.admin")) return emptyList()

        if (args.size == 1) {
            return subCommandMap.keys.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        val subCommand = subCommandMap[args[0].lowercase()]
        if (subCommand != null) {
            return subCommand.tabComplete(sender, args.drop(1))
        }

        return emptyList()
    }
}
