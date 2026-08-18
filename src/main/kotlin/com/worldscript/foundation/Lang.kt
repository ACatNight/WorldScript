package com.worldscript.foundation

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Lang(private val plugin: JavaPlugin) {
    private val fallbackFile by lazy { YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lang/en_US.yml")) }
    private var loadedLanguage: String? = null
    private var languageFile: YamlConfiguration? = null

    fun text(key: String, fallback: String = key): String =
        selectedLanguageFile().getString(key) ?: fallbackFile.getString(key) ?: fallback

    /** Lets feature-specific defaults keep the selected language during upgrades. */
    fun textWithLocalFallback(key: String, fallback: String): String =
        selectedLanguageFile().getString(key) ?: fallback

    fun send(sender: CommandSender, key: String, vararg replacements: Pair<String, Any?>) =
        send(sender, key, true, *replacements)

    fun send(sender: CommandSender, key: String, includePrefix: Boolean, vararg replacements: Pair<String, Any?>) {
        var text = text(key)
        replacements.forEach { (name, value) -> text = text.replace("%$name%", value?.toString() ?: "") }
        val prefix = if (includePrefix) this.text("prefix", "") else ""
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + text))
    }

    private fun selectedLanguageFile(): YamlConfiguration {
        val language = plugin.config.getString("language", "en_US")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
            ?: "en_US"
        if (language != loadedLanguage) {
            loadedLanguage = language
            languageFile = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lang/$language.yml"))
        }
        return requireNotNull(languageFile) { "Language file was not initialized." }
    }
}
