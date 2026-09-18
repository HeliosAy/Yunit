package net.yaycraft.yunit.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

class LangManager(private val plugin: JavaPlugin) {

    @Volatile
    private var langConfig: FileConfiguration = YamlConfiguration()

    /**
     * lang.yml'i (yeniden) yükler. Dosyada hata varsa mevcut mesajlar korunur.
     * @return başarılıysa true
     */
    fun load(): Boolean {
        val langFile = File(plugin.dataFolder, "lang.yml")
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false)
        }

        val loaded = YamlConfiguration()
        try {
            loaded.load(langFile)
        } catch (e: Exception) {
            plugin.logger.severe("lang.yml okunamadı, önceki mesajlar kullanılmaya devam ediyor: ${e.message}")
            return false
        }

        plugin.getResource("lang.yml")?.use { stream ->
            loaded.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8)))
        }
        langConfig = loaded
        return true
    }

    fun getPrefix(): String {
        return langConfig.getString("prefix") ?: "<gold><b>YUNIT</b></gold> <dark_gray>│</dark_gray> <gray>"
    }

    fun getMessage(path: String, default: String): String {
        return langConfig.getString("messages.$path") ?: default
    }
}
