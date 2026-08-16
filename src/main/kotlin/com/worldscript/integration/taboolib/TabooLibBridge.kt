package com.worldscript.integration.taboolib

import org.bukkit.plugin.java.JavaPlugin

/** Reports the TabooLib runtime bundled into WorldScript. */
class TabooLibBridge(private val plugin: JavaPlugin) {
    val available: Boolean = true

    fun report() {
        plugin.logger.info("Embedded TabooLib 6.3.0 runtime enabled.")
    }
}
