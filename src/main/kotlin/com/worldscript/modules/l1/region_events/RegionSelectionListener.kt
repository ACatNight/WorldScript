package com.worldscript.modules.l1.region_events

import com.worldscript.command.WsCommand
import com.worldscript.modules.l1.region_core.SelectionService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class RegionSelectionListener(
    private val plugin: org.bukkit.plugin.java.JavaPlugin,
    private val selection: SelectionService,
    private val command: WsCommand,
) : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val configured = org.bukkit.Material.matchMaterial(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE") ?: org.bukkit.Material.GOLDEN_AXE
        if (item.type != configured || event.clickedBlock == null || !event.player.hasPermission("worldscript.admin")) return
        val index = when (event.action) { Action.LEFT_CLICK_BLOCK -> 1; Action.RIGHT_CLICK_BLOCK -> 2; else -> return }
        event.isCancelled = true
        selection.set(event.player, index, event.clickedBlock!!.location)
        com.worldscript.foundation.Lang(plugin).send(event.player, "selection-set", "position" to index, "x" to event.clickedBlock!!.x, "y" to event.clickedBlock!!.y, "z" to event.clickedBlock!!.z)
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) { selection.clear(event.player.uniqueId) }
}
