package net.yaycraft.yunit.util

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
    
    /** Kullanıcıdan gelen metindeki MiniMessage etiketlerini etkisiz hale getirir */
    fun escape(text: String): String = miniMessage.escapeTags(text)

    fun getMessage(path: String, default: String): String {
        return langManager.getMessage(path, default)
    }
}
