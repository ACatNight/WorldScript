package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.Lang
import com.worldscript.modules.l1.region_core.SelectionService
import org.bukkit.Material
import com.worldscript.foundation.MaterialResolver
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin

class RegionSelectionListener(
    private val plugin: JavaPlugin,
    private val selection: SelectionService,
) : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val configured = MaterialResolver.find(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE", "GOLD_AXE") ?: Material.STICK
        if (item.type != configured || event.clickedBlock == null || !event.player.hasPermission("worldscript.admin")) return
        val index = when (event.action) { Action.LEFT_CLICK_BLOCK -> 1; Action.RIGHT_CLICK_BLOCK -> 2; else -> return }
        event.isCancelled = true
        val block = event.clickedBlock ?: return
        selection.set(event.player, index, block.location)
        Lang(plugin).send(event.player, "selection-set", "position" to index, "x" to block.x, "y" to block.y, "z" to block.z)
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) { selection.clear(event.player.uniqueId) }
}
