package com.worldscript.integration.taboolib

import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin

/** Keeps the region core independent from the optional TabooLib runtime. */
class TabooLibBridge(
    private val plugin: JavaPlugin,
    private val pluginManager: PluginManager,
) {
    val available: Boolean
        get() = pluginManager.isPluginEnabled(PLUGIN_NAME)

    fun report() {
        if (available) {
            plugin.logger.info("TabooLib detected. Kether bridge is available for the next integration stage.")
        }
    }

    fun plugin(): Plugin? = pluginManager.getPlugin(PLUGIN_NAME)

    private companion object {
        const val PLUGIN_NAME = "TabooLib"
    }
}
