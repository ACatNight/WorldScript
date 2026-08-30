package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.MaterialResolver
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/**
 * Resolves editor tools from one configuration boundary.
 *
 * Selection and polygon editing intentionally use separate materials so an
 * editor action cannot be interpreted as a gameplay interaction or another
 * editor mode.
 */
class EditorToolService(private val plugin: JavaPlugin) {
    fun selectionTool(): Material =
        resolve("selection.tool", "GOLDEN_AXE", "GOLD_AXE", Material.STICK)

    fun polygonTool(): Material =
        resolve("selection.polygon.tool", "STICK", "STICK", Material.STICK)

    fun isSelectionTool(item: ItemStack?): Boolean =
        item?.type == selectionTool()

    fun isPolygonTool(item: ItemStack?): Boolean =
        item?.type == polygonTool()

    private fun resolve(path: String, fallback: String, legacy: String, default: Material): Material {
        return MaterialResolver.find(plugin.config.getString(path, fallback) ?: fallback, legacy) ?: default
    }
}
