package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.EquipmentSlot

class RegionEnterEvent(val player: Player, val regionId: String) : Event() {
    override fun getHandlers() = handlerList
    companion object { private val handlerList = HandlerList(); @JvmStatic fun getHandlerList() = handlerList }
}

class RegionLeaveEvent(val player: Player, val regionId: String) : Event() {
    override fun getHandlers() = handlerList
    companion object { private val handlerList = HandlerList(); @JvmStatic fun getHandlerList() = handlerList }
}

class RegionInteractEvent(val player: Player, val regionId: String) : Event() {
    override fun getHandlers() = handlerList
    companion object { private val handlerList = HandlerList(); @JvmStatic fun getHandlerList() = handlerList }
}

class RegionEventServiceImpl(
    private val plugin: org.bukkit.plugin.java.JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) : org.bukkit.event.Listener {
    private val current = mutableMapOf<java.util.UUID, Set<String>>()

    @org.bukkit.event.EventHandler
    fun onMove(event: org.bukkit.event.player.PlayerMoveEvent) {
        val to = event.to ?: return
        val sameWorld = event.from.world?.uid == to.world?.uid
        if (sameWorld && event.from.blockX == to.blockX && event.from.blockY == to.blockY && event.from.blockZ == to.blockZ) return
        updateRegions(event.player, to)
    }

    @org.bukkit.event.EventHandler
    fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) {
        // A player can join inside a region without producing a movement event.
        plugin.server.scheduler.runTask(plugin, Runnable { updateRegions(event.player, event.player.location) })
    }

    private fun updateRegions(player: Player, location: org.bukkit.Location) {
        val next = regions.regionsAt(location) { id -> regions.isAccessible(id, state.isRegionUnlocked(player, id)) }.map { it.id }.toSet()
        val previous = current[player.uniqueId] ?: emptySet()
        if (previous == next) {
            if (next.isEmpty()) current.remove(player.uniqueId) else current[player.uniqueId] = next
            return
        }
        previous.filter { it !in next }.sortedByDescending { regions.depth(it) }.forEach { id ->
            regions.find(id)?.takeIf { canDispatchNestedEvent(it.id, RegionEventType.LEAVE) }
                ?.let { region -> regions.effective(region.id)?.takeIf { it.events[RegionEventType.LEAVE]?.enabled != false }?.let { plugin.server.pluginManager.callEvent(RegionLeaveEvent(player, region.id)) } }
        }
        next.filter { it !in previous }.sortedBy { regions.depth(it) }.forEach { id ->
            regions.find(id)?.takeIf { canDispatchNestedEvent(it.id, RegionEventType.ENTER) }
                ?.let { region -> regions.effective(region.id)?.takeIf { it.events[RegionEventType.ENTER]?.enabled != false }?.let { plugin.server.pluginManager.callEvent(RegionEnterEvent(player, region.id)) } }
        }
        if (next.isEmpty()) current.remove(player.uniqueId) else current[player.uniqueId] = next
    }

    @org.bukkit.event.EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) { current.remove(event.player.uniqueId) }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    fun onInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
        if (!RegionInteractionPolicy.shouldDispatch(event.hand == EquipmentSlot.HAND, event.action == Action.RIGHT_CLICK_BLOCK, event.isCancelled)) return
        val selectionTool = org.bukkit.Material.matchMaterial(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE") ?: org.bukkit.Material.GOLDEN_AXE
        if (event.item?.type == selectionTool) return
        val block = event.clickedBlock ?: return
        val region = regions.regionsAt(block.location) { id -> regions.isAccessible(id, state.isRegionUnlocked(event.player, id)) }.lastOrNull() ?: return
        if (region.events[RegionEventType.INTERACT]?.enabled != false) {
            plugin.server.pluginManager.callEvent(RegionInteractEvent(event.player, region.id))
        }
    }

    @org.bukkit.event.EventHandler
    fun onEnter(event: RegionEnterEvent) { com.worldscript.foundation.Lang(plugin).send(event.player, "region-enter", "region" to event.regionId) }
    @org.bukkit.event.EventHandler
    fun onLeave(event: RegionLeaveEvent) { com.worldscript.foundation.Lang(plugin).send(event.player, "region-leave", "region" to event.regionId) }

    private fun canDispatchNestedEvent(regionId: String, eventType: RegionEventType): Boolean {
        val region = regions.find(regionId) ?: return false
        return region.parentId == null || region.events[eventType]?.overrideParent == true
    }
}
