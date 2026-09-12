package net.yaycraft.yunit.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.yaycraft.yunit.config.LangManager
import org.bukkit.command.CommandSender

class MessageUtil(private val langManager: LangManager) {

    private val miniMessage = MiniMessage.miniMessage()
    
    fun send(sender: CommandSender, message: String) {
        val prefix = langManager.getPrefix()
        val parsed = miniMessage.deserialize("$prefix$message")
        sender.sendMessage(parsed)
    }
    
    fun sendRaw(sender: CommandSender, message: String) {
        val parsed = miniMessage.deserialize(message)
        sender.sendMessage(parsed)
    }
    
    fun sendError(sender: CommandSender, message: String) {
        val prefix = langManager.getPrefix()
        val parsed = miniMessage.deserialize("$prefix<red>$message</red>")
        sender.sendMessage(parsed)
    }
    
    fun sendSuccess(sender: CommandSender, message: String) {
        val prefix = langManager.getPrefix()
        val parsed = miniMessage.deserialize("$prefix<green>$message</green>")
        sender.sendMessage(parsed)
    }
    
    fun getMessage(path: String, default: String): String {
        return langManager.getMessage(path, default)
    }
}
