package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.Lang
import com.worldscript.modules.l1.region_core.SelectionService
import com.worldscript.modules.l1.region_core.PolygonEditingService
import org.bukkit.Material
import com.worldscript.foundation.MaterialResolver
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin

class RegionSelectionListener(
    private val plugin: JavaPlugin,
    private val selection: SelectionService,
    private val polygons: PolygonEditingService,
    private val editorTools: com.worldscript.modules.l1.region_core.EditorToolService,
) : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != null && event.hand != EquipmentSlot.HAND) return
        if (handlePolygon(event)) return
        val item = event.item ?: return
        if (!editorTools.isSelectionTool(item) || event.clickedBlock == null || !event.player.hasPermission("worldscript.admin")) return
        val index = when (event.action) { Action.LEFT_CLICK_BLOCK -> 1; Action.RIGHT_CLICK_BLOCK -> 2; else -> return }
        event.isCancelled = true
        val block = event.clickedBlock ?: return
        selection.set(event.player, index, block.location)
        Lang(plugin).send(event.player, "selection-set", "position" to index, "x" to block.x, "y" to block.y, "z" to block.z)
    }

    @EventHandler
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        if (!event.player.isSneaking || !event.player.hasPermission("worldscript.admin")) return
        if (!polygons.isEditingTool(event.player, event.offHandItem) &&
            !polygons.isEditingTool(event.player, event.mainHandItem)) return
        event.isCancelled = true
        polygons.finish(event.player)
    }

    private fun handlePolygon(event: PlayerInteractEvent): Boolean {
        val player = event.player
        if (!player.hasPermission("worldscript.admin") || !polygons.isEditingTool(player, event.item)) return false
        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                event.isCancelled = true
                val block = event.clickedBlock
                if (block == null) { if (player.isSneaking) polygons.undo(player) }
                else {
                    val point = polygons.nearestPoint(player, block.x, block.z)
                    if (player.isSneaking && point != null) polygons.deletePoint(player, point)
                    else if (!player.isSneaking && point != null) polygons.selectPoint(player, point)
                    else if (!player.isSneaking) polygons.addPoint(player, block.x, block.z)
                }
            }
            Action.RIGHT_CLICK_AIR -> {
                event.isCancelled = true
                polygons.finish(player)
            }
            Action.RIGHT_CLICK_BLOCK -> {
                event.isCancelled = true
                if (player.isSneaking) {
                    polygons.finish(player)
                    return true
                }
                val block = event.clickedBlock ?: return true
                val selected = polygons.activeSelectedPoint(player)
                if (selected != null) polygons.moveSelected(player, block.x, block.z)
                else polygons.nearestPoint(player, block.x, block.z)?.let { polygons.selectPoint(player, it) }
            }
            else -> return false
        }
        return true
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        selection.clear(event.player.uniqueId)
        polygons.disconnect(event.player)
    }
}
