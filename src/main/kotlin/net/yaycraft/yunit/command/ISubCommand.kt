package net.yaycraft.yunit.command

import org.bukkit.command.CommandSender

/**
 * Tüm alt komutlar (sub-command)
 * Hem player hem admin komutları bu arayüzü implement eder.
 */
interface ISubCommand {
    /** Alt komutun adı */
    val name: String
    
    /** Alt komutun kullanım açıklaması */
    val usage: String
    
    /** Alt komutun gerektirdiği yetki */
    val permission: String
    
    /** Komutu çalıştırır */
    fun execute(sender: CommandSender, args: List<String>)
    
    /** Tab-completion önerileri */
    fun tabComplete(sender: CommandSender, args: List<String>): List<String>?
}
