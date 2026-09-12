package net.yaycraft.yunit.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class LangManager(private val plugin: JavaPlugin) {

    private lateinit var langConfig: FileConfiguration
    
    fun load() {
        val langFile = File(plugin.dataFolder, "lang.yml")
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false)
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile)
    }

    fun getPrefix(): String {
        return langConfig.getString("prefix") ?: "<gold><b>YUNIT</b></gold> <dark_gray>│</dark_gray> <gray>"
    }

    fun getMessage(path: String, default: String): String {
        return langConfig.getString("messages.$path") ?: default
    }
}
