package com.worldscript.foundation

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Lang(private val plugin: JavaPlugin) {
    private val file by lazy { YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lang/zh_CN.yml")) }
    fun text(key: String, fallback: String = key): String = file.getString(key, fallback) ?: fallback
    fun send(sender: CommandSender, key: String, vararg replacements: Pair<String, Any?>) {
        var text = file.getString(key, key) ?: key
        replacements.forEach { (name, value) -> text = text.replace("%$name%", value?.toString() ?: "") }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', (file.getString("prefix", "") ?: "") + text))
    }
}
