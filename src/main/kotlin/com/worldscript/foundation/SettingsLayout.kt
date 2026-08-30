package com.worldscript.foundation

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.WeakHashMap

/** Keeps operator settings split by feature while retaining one runtime lookup tree. */
object SettingsLayout {
    private val groups = listOf("selection", "gui", "discovery", "conditions", "messages", "placeholders", "editor", "economy", "modules")
    private val loaded = WeakHashMap<JavaPlugin, MutableMap<String, FileConfiguration>>()

    fun initialize(plugin: JavaPlugin) {
        val legacy = plugin.config.getInt("config-version", 0) < 3
        val files = loaded.getOrPut(plugin) { linkedMapOf() }
        groups.forEach { group ->
            val file = File(plugin.dataFolder, "settings/$group.yml")
            if (!file.isFile) plugin.saveResource("settings/$group.yml", false)
            val config = YamlConfiguration.loadConfiguration(file)
            val defaults = loadDefault(plugin, group)
            val legacySection = plugin.config.getConfigurationSection(group)
            var changed = false
            if (defaults != null) {
                changed = mergeMissing(config, defaults)
            }
            if (legacy && legacySection != null) {
                legacySection.getValues(true)
                    .filterValues { it !is ConfigurationSection }
                    .forEach { (path, value) -> config.set(path, value) }
                changed = true
            }
            if (changed) {
                config.save(file)
            }
            files[group] = config
            plugin.config.set(group, config)
        }
        if (legacy) {
            plugin.config.set("config-version", 3)
            groups.forEach { plugin.config.set(it, null) }
            saveRoot(plugin)
        }
        merge(plugin)
    }

    fun reload(plugin: JavaPlugin) {
        loaded.remove(plugin)
        initialize(plugin)
    }

    fun save(plugin: JavaPlugin, group: String) {
        val config = plugin.config.getConfigurationSection(group) ?: return
        val file = File(plugin.dataFolder, "settings/$group.yml")
        runCatching {
            file.parentFile?.mkdirs()
            YamlConfiguration().also { target ->
                config.getValues(true)
                    .filterValues { it !is ConfigurationSection }
                    .forEach { (path, value) -> target.set(path, value) }
            }.save(file)
        }.onFailure { plugin.logger.warning("Could not save settings/$group.yml: ${it.message}") }
    }

    fun saveForPath(plugin: JavaPlugin, path: String) = save(plugin, path.substringBefore('.'))

    /** Persist only the intentionally small root configuration contract. */
    fun saveRoot(plugin: JavaPlugin) {
        val root = YamlConfiguration()
        root.set("config-version", plugin.config.getInt("config-version", 3))
        root.set("language", plugin.config.getString("language", "en_US"))
        runCatching { root.save(File(plugin.dataFolder, "config.yml")) }
            .onFailure { plugin.logger.warning("Could not save root config.yml: ${it.message}") }
    }

    private fun merge(plugin: JavaPlugin) {
        loaded[plugin].orEmpty().forEach { (group, config) -> plugin.config.set(group, config) }
    }

    private fun loadDefault(plugin: JavaPlugin, group: String): YamlConfiguration? {
        val stream = plugin.getResource("settings/$group.yml") ?: return null
        return stream.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8))
        }
    }

    private fun mergeMissing(target: FileConfiguration, defaults: FileConfiguration): Boolean {
        var changed = false
        defaults.getValues(true)
            .filterValues { it !is ConfigurationSection }
            .forEach { (path, value) ->
                if (!target.contains(path)) {
                    target.set(path, value)
                    changed = true
                }
            }
        return changed
    }
}
