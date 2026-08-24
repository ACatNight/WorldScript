@file:Suppress("DEPRECATION") // Bukkit event APIs here must remain compatible with Paper 1.12.2.

package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.rpg.PlayerVariableService
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import net.md_5.bungee.api.ChatColor

class RegionEnterEvent(val player: Player, val regionId: String) : Event() {
    override fun getHandlers() = handlerList
    companion object { private val handlerList = HandlerList(); @JvmStatic fun getHandlerList() = handlerList }
}

class RegionDiscoverEvent(val player: Player, val regionId: String) : Event() {
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
    private val conditions: ConditionEvaluator,
    private val conditionFailureFeedback: (Player, String, String, List<com.worldscript.foundation.model.ActionDefinition>) -> Unit,
) : Listener {
    private val lang = Lang(plugin)
    private val current = mutableMapOf<UUID, Set<String>>()
    private val deniedNoticeAt = mutableMapOf<UUID, Long>()

    fun reset() = current.clear()

    /** Re-evaluates a player's location after an external progress update. */
    fun refresh(player: Player) {
        discoverAt(player, player.location)
        updateRegions(player, player.location)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to
        val sameWorld = event.from.world?.uid == to.world?.uid
        if (sameWorld && event.from.blockX == to.blockX && event.from.blockY == to.blockY && event.from.blockZ == to.blockZ) return
        discoverAt(event.player, to)
        if (plugin.config.getBoolean("conditions.enabled", false)) {
            val fromIds = regions.regionsAt(event.from).map { it.id }.toSet()
            val blocked = regions.regionsAt(to)
                .asReversed()
                .firstOrNull { region ->
                    if (region.id in fromIds) return@firstOrNull false
                    val script = regions.effective(region.id)?.events?.get(RegionEventType.ENTER) ?: return@firstOrNull false
                    script.conditionsEnabled && script.conditions.isNotEmpty() && !conditions.allMet(event.player, region.id, script.conditions, script.conditionMode)
                }
            if (blocked != null) {
                event.isCancelled = true
                val now = System.currentTimeMillis()
                if (now - (deniedNoticeAt[event.player.uniqueId] ?: 0L) >= 1000L) {
                    deniedNoticeAt[event.player.uniqueId] = now
                    conditions.firstFailure(
                        event.player,
                        blocked.id,
                        regions.effective(blocked.id)?.events?.get(RegionEventType.ENTER)?.conditions.orEmpty(),
                        regions.effective(blocked.id)?.events?.get(RegionEventType.ENTER)?.conditionMode ?: com.worldscript.foundation.model.ConditionMode.AND,
                    )?.let { failed ->
                        val reason = conditions.describe(failed)
                        conditionFailureFeedback(event.player, blocked.id, reason, regions.effective(blocked.id)?.events?.get(RegionEventType.ENTER)?.conditionFailureActions.orEmpty())
                    }
                }
                return
            }
        }
        updateRegions(event.player, to)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            discoverAt(event.player, event.player.location)
            updateRegions(event.player, event.player.location)
        })
    }

    /** Teleports do not always produce a block-change move event. Re-run the
     * same discovery/entry pipeline after the destination is committed so
     * commands, portals and scripted teleports behave like walking in. */
    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        if (event.isCancelled) return
        val destination = event.to
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!event.player.isOnline) return@Runnable
            discoverAt(event.player, destination)
            updateRegions(event.player, destination)
        })
    }

    private fun updateRegions(player: Player, location: Location) {
        val next = regions.regionsAt(location) { id ->
            if (!regions.isAccessible(id, state.isRegionUnlocked(player, id))) return@regionsAt false
            if (!plugin.config.getBoolean("conditions.enabled", false)) return@regionsAt true
            val script = regions.effective(id)?.events?.get(RegionEventType.ENTER) ?: return@regionsAt true
            !script.conditionsEnabled || conditions.allMet(player, id, script.conditions, script.conditionMode)
        }.map { it.id }.toSet()
        val previous = current[player.uniqueId] ?: emptySet()
        if (previous == next) {
            if (next.isEmpty()) current.remove(player.uniqueId) else current[player.uniqueId] = next
            return
        }
        previous.filter { it !in next }.sortedByDescending { regions.depth(it) }.forEach { id ->
            regions.find(id)?.let { region ->
                regions.effective(region.id)?.takeIf { it.events[RegionEventType.LEAVE]?.enabled != false }
                    ?.let { plugin.server.pluginManager.callEvent(RegionLeaveEvent(player, region.id)) }
            }
        }
        next.filter { it !in previous }.sortedBy { regions.depth(it) }.forEach { id ->
            regions.find(id)?.let { region ->
                regions.effective(region.id)?.takeIf { it.events[RegionEventType.ENTER]?.enabled != false }
                    ?.let { plugin.server.pluginManager.callEvent(RegionEnterEvent(player, region.id)) }
            }
        }
        if (next.isEmpty()) current.remove(player.uniqueId) else current[player.uniqueId] = next
    }

    private fun discoverAt(player: Player, location: Location) {
        regions.regionsAt(location).asReversed()
            .filter { region ->
                if (state.isRegionUnlocked(player, region.id)) return@filter false
                if (!plugin.config.getBoolean("conditions.enabled", false)) return@filter true
                val script = regions.effective(region.id)?.events?.get(RegionEventType.ENTER) ?: return@filter true
                !script.conditionsEnabled || script.conditions.isEmpty() || conditions.allMet(player, region.id, script.conditions, script.conditionMode)
            }
            .forEach { plugin.server.pluginManager.callEvent(RegionDiscoverEvent(player, it.id)) }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        current.remove(event.player.uniqueId)
        deniedNoticeAt.remove(event.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (!RegionInteractionPolicy.shouldDispatch(event.hand == EquipmentSlot.HAND, event.isCancelled)) return
        val selectionTool = MaterialResolver.find(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE", "GOLD_AXE") ?: Material.STICK
        if (event.item?.type == selectionTool) return
        val location = event.clickedBlock?.location ?: event.player.location
        val region = regions.regionsAt(location) { id ->
            regions.isAccessible(id, state.isRegionUnlocked(event.player, id))
        }.lastOrNull() ?: return
        when (val type = RegionInteractionPolicy.eventType(event.action)) {
            RegionEventType.LEFT_CLICK,
            RegionEventType.RIGHT_CLICK -> dispatchBlockClick(event.player, region.id, type)
            RegionEventType.INTERACT -> dispatchInteract(event.player, region.id)
            else -> Unit
        }
    }

    private fun dispatchBlockClick(player: Player, regionId: String, type: RegionEventType) {
        if (regions.effective(regionId)?.events?.get(type)?.enabled == true) {
            plugin.server.pluginManager.callEvent(RegionBlockClickEvent(player, regionId, type))
        }
    }

    private fun dispatchInteract(player: Player, regionId: String) {
        if (regions.effective(regionId)?.events?.get(RegionEventType.INTERACT)?.enabled != false) {
            plugin.server.pluginManager.callEvent(RegionInteractEvent(player, regionId))
        }
    }

    private fun color(value: String): String =
        ChatColor.translateAlternateColorCodes('&', value)

    @EventHandler
    fun onEnter(event: RegionEnterEvent) {
        if (plugin.config.getBoolean("messages.region-enter-enabled", false)) {
            lang.send(event.player, "region-enter", "region" to event.regionId)
        }
    }

    @EventHandler
    fun onLeave(event: RegionLeaveEvent) {
        if (plugin.config.getBoolean("messages.region-leave-enabled", false)) {
            lang.send(event.player, "region-leave", "region" to event.regionId)
        }
    }
}
