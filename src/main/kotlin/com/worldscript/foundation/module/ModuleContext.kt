package com.worldscript.foundation.module

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.Listener
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Logger

class ModuleContext(
    val plugin: JavaPlugin,
    val moduleId: String,
    internal val services: ServiceRegistry,
) {
    val logger: Logger = plugin.logger
    private val listeners = mutableListOf<Listener>()

    fun registerListener(listener: Listener) {
        plugin.server.pluginManager.registerEvents(listener, plugin)
        listeners += listener
    }

    fun config(): YamlConfiguration {
        return config(moduleId)
    }

    fun config(moduleId: String): YamlConfiguration {
        return YamlConfiguration.loadConfiguration(configFile(moduleId))
    }

    fun configFile(): File {
        return configFile(moduleId)
    }

    fun configFile(moduleId: String): File {
        val directory = File(plugin.dataFolder, "modules/$moduleId")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, "config.yml")
    }

    fun saveConfig(config: YamlConfiguration) {
        config.save(configFile())
    }

    internal fun unregisterListeners() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }
}

class ServiceRegistry {
    private val services = linkedMapOf<Class<*>, Any>()

    internal fun <T : Any> register(type: Class<T>, service: T) {
        services[type] = service
    }

    fun <T : Any> get(type: Class<T>): T? {
        return type.cast(services[type] ?: return null)
    }
}
