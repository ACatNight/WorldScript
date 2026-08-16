package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

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

class RegionBlockClickEvent(val player: Player, val regionId: String, val type: RegionEventType) : Event() {
    override fun getHandlers() = handlerList
    companion object { private val handlerList = HandlerList(); @JvmStatic fun getHandlerList() = handlerList }
}

class RegionEventServiceImpl(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) : Listener {
    private val current = mutableMapOf<UUID, Set<String>>()

    fun reset() = current.clear()

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        val sameWorld = event.from.world?.uid == to.world?.uid
        if (sameWorld && event.from.blockX == to.blockX && event.from.blockY == to.blockY && event.from.blockZ == to.blockZ) return
        updateRegions(event.player, to)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { updateRegions(event.player, event.player.location) })
    }

    private fun updateRegions(player: Player, location: Location) {
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

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        current.remove(event.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (!RegionInteractionPolicy.shouldDispatch(event.hand == EquipmentSlot.HAND, event.isCancelled)) return
        val selectionTool = MaterialResolver.find(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE", "GOLD_AXE") ?: Material.STICK
        if (event.item?.type == selectionTool) return
        val block = event.clickedBlock ?: return
        val region = regions.regionsAt(block.location) { id -> regions.isAccessible(id, state.isRegionUnlocked(event.player, id)) }.lastOrNull() ?: return
        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> dispatchBlockClick(event.player, region.id, RegionEventType.LEFT_CLICK)
            Action.RIGHT_CLICK_BLOCK -> {
                val rightClick = regions.effective(region.id)?.events?.get(RegionEventType.RIGHT_CLICK)
                if (rightClick?.enabled == true) dispatchBlockClick(event.player, region.id, RegionEventType.RIGHT_CLICK)
                else if (region.events[RegionEventType.INTERACT]?.enabled != false) plugin.server.pluginManager.callEvent(RegionInteractEvent(event.player, region.id))
            }
            else -> Unit
        }
    }

    private fun dispatchBlockClick(player: Player, regionId: String, type: RegionEventType) {
        if (regions.effective(regionId)?.events?.get(type)?.enabled == true) {
            plugin.server.pluginManager.callEvent(RegionBlockClickEvent(player, regionId, type))
        }
    }

    @EventHandler
    fun onEnter(event: RegionEnterEvent) {
        Lang(plugin).send(event.player, "region-enter", "region" to event.regionId)
    }

    @EventHandler
    fun onLeave(event: RegionLeaveEvent) {
        Lang(plugin).send(event.player, "region-leave", "region" to event.regionId)
    }

    private fun canDispatchNestedEvent(regionId: String, eventType: RegionEventType): Boolean {
        val region = regions.find(regionId) ?: return false
        return region.parentId == null || region.events[eventType]?.overrideParent == true
    }
}
