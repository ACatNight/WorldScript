@file:Suppress("DEPRECATION") // Bukkit event APIs here must remain compatible with Paper 1.12.2.

package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.Lang
import com.worldscript.foundation.api.RegionCoreService
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.EditorToolService
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.Location
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
    private val regions: RegionCoreService,
    private val state: PlayerVariableService,
    private val conditions: ConditionEvaluator,
    private val conditionFailureFeedback: (Player, String, String, List<com.worldscript.foundation.model.ActionDefinition>) -> Unit,
    private val editorTools: EditorToolService,
    private val access: RegionAccessService = RegionAccessService(plugin, regions, state, conditions),
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
        run {
            // Include inaccessible regions in the lookup. The default regionsAt
            // query filters them out, which made locked regions impossible to
            // detect here and allowed players to walk into them.
            val fromIds = regions.regionsAt(event.from) { true }.map { it.id }.toSet()
            val blocked = regions.regionsAt(to) { true }
                .asReversed()
                .firstOrNull { region ->
                    if (region.id in fromIds) return@firstOrNull false
                    access.checkEnter(event.player, region) !is RegionAccessService.AccessResult.Allowed
                }
            if (blocked != null) {
                event.isCancelled = true
                val now = System.currentTimeMillis()
                val cooldown = plugin.config.getLong("conditions.deny.notice-cooldown-millis", 1000L).coerceAtLeast(0L)
                if (now - (deniedNoticeAt[event.player.uniqueId] ?: 0L) >= cooldown) {
                    deniedNoticeAt[event.player.uniqueId] = now
                    when (val result = access.checkEnter(event.player, blocked)) {
                        is RegionAccessService.AccessResult.DeniedCondition ->
                            conditionFailureFeedback(event.player, blocked.id, result.reason, result.actions)
                        else -> Unit
                    }
                }
                return
            }
        }
        discoverAt(event.player, to)
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
        val fromIds = regions.regionsAt(event.from) { true }.map { it.id }.toSet()
        val blocked = regions.regionsAt(destination) { true }
            .asReversed()
            .firstOrNull { region ->
                if (region.id in fromIds) return@firstOrNull false
                access.checkEnter(event.player, region) !is RegionAccessService.AccessResult.Allowed
            }
        if (blocked != null) {
            event.isCancelled = true
            when (val result = access.checkEnter(event.player, blocked)) {
                is RegionAccessService.AccessResult.DeniedCondition ->
                    conditionFailureFeedback(event.player, blocked.id, result.reason, result.actions)
                else -> Unit
            }
            return
        }
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!event.player.isOnline) return@Runnable
            discoverAt(event.player, destination)
            updateRegions(event.player, destination)
        })
    }

    private fun updateRegions(player: Player, location: Location) {
        val next = regions.regionsAt(location) { id ->
            val region = regions.find(id) ?: return@regionsAt false
            access.checkEnter(player, region) is RegionAccessService.AccessResult.Allowed
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
                access.canDiscover(player, region)
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
        val selectionTool = editorTools.selectionTool()
        val polygonTool = editorTools.polygonTool()
        // Editor tools must never dispatch gameplay region interactions. The
        // selection listener may cancel later in the same tick, so filter both
        // materials here as well to avoid order-dependent side effects.
        if (event.item?.type == selectionTool || event.item?.type == polygonTool) return
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
