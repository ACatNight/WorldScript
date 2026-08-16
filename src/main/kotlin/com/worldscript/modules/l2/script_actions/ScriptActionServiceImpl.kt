package com.worldscript.modules.l2.script_actions

import com.worldscript.foundation.api.ScriptActionService
import com.worldscript.foundation.Lang
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RewardDefinition
import com.worldscript.foundation.model.RewardType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_events.RegionEnterEvent
import com.worldscript.modules.l1.region_events.RegionBlockClickEvent
import com.worldscript.modules.l1.region_events.RegionInteractEvent
import com.worldscript.modules.l1.region_events.RegionLeaveEvent
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import com.worldscript.modules.l2.rpg.RewardService
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions
import taboolib.platform.type.BukkitPlayer

class ScriptActionServiceImpl(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
    private val conditions: ConditionEvaluator,
    private val rewards: RewardService,
) : ScriptActionService, Listener {
    private val lastExecution = mutableMapOf<String, Long>()

    fun reset() = lastExecution.clear()

    @EventHandler fun onEnter(event: RegionEnterEvent) = executeEvent(event.player, event.regionId, RegionEventType.ENTER)
    @EventHandler fun onLeave(event: RegionLeaveEvent) = executeEvent(event.player, event.regionId, RegionEventType.LEAVE)
    @EventHandler fun onInteract(event: RegionInteractEvent) = executeEvent(event.player, event.regionId, RegionEventType.INTERACT)
    @EventHandler fun onBlockClick(event: RegionBlockClickEvent) = executeEvent(event.player, event.regionId, event.type)

    private fun executeEvent(player: Player, regionId: String, eventType: RegionEventType) {
        val script = regions.effective(regionId)?.events?.get(eventType) ?: return
        if (!script.enabled) return
        val firstEntry = eventType == RegionEventType.ENTER && !state.hasEnteredRegion(player, regionId)
        if (script.firstEntryOnly && !firstEntry) return
        if (script.repeatEntryOnly && firstEntry) return
        conditions.firstFailure(player, regionId, script.conditions)?.let { failed ->
            Lang(plugin).send(player, "condition-failed", "region" to regionId, "reason" to conditions.describe(failed))
            return
        }
        val key = "${player.uniqueId}:$regionId:${eventType.name}"
        val now = System.currentTimeMillis()
        if (script.cooldownSeconds > 0 && now - (lastExecution[key] ?: 0) < script.cooldownSeconds * 1000) return
        lastExecution[key] = now
        if (firstEntry) state.markRegionEntered(player, regionId)
        execute(player, regionId, script.actions)
        rewards.grant(player, regionId, script.rewards, key)
    }

    override fun execute(player: Player, regionId: String, actions: List<ActionDefinition>) {
        actions.forEach { action ->
            val value = action.value
                .replace("%player%", player.name)
                .replace("%player_name%", player.name)
                .replace("%uuid%", player.uniqueId.toString())
                .replace("%region%", regionId)
                .replace("%world%", player.world.name)
                .replace("%region_role%", regions.effective(regionId)?.role?.name?.lowercase() ?: "")
                .replace("%content_id%", regions.effective(regionId)?.contentId ?: "")
                .let { expanded ->
                    regions.effective(regionId)?.variables?.entries?.fold(expanded) { text, (key, variable) ->
                        text.replace("%var.$key%", variable)
                    } ?: expanded
                }
            runCatching {
                when (action.type) {
                    ActionType.KETHER -> executeKether(player, regionId, value)
                    ActionType.PLAYER_COMMAND -> player.performCommand(value.removePrefix("/"))
                    ActionType.CONSOLE_COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.removePrefix("/"))
                    ActionType.MESSAGE -> player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', value))
                    ActionType.TELEPORT -> teleport(player, value)
                    ActionType.SET_VARIABLE -> setPlayerVariable(player, value)
                    ActionType.SET_REGION_STATUS -> setRegionStatus(player, value)
                    ActionType.GIVE_ITEM -> rewards.grant(player, regionId, listOf(RewardDefinition(RewardType.ITEM, value)))
                    ActionType.GIVE_EXPERIENCE -> rewards.grant(player, regionId, listOf(RewardDefinition(RewardType.EXPERIENCE, value)))
                    ActionType.GIVE_MONEY -> rewards.grant(player, regionId, listOf(RewardDefinition(RewardType.MONEY, value)))
                    ActionType.UNLOCK_REGION -> state.unlockRegion(player, value)
                    ActionType.COMPLETE_REGION -> state.markRegionCompleted(player, value)
                }
            }.onFailure { plugin.logger.warning("Failed to execute ${action.type} in region $regionId: ${it.message}") }
        }
    }

    private fun executeKether(player: Player, regionId: String, script: String) {
        val region = regions.effective(regionId)
        val scriptVars = linkedMapOf<String, Any?>(
            "player" to player.name,
            "uuid" to player.uniqueId.toString(),
            "region" to regionId,
            "world" to player.world.name,
        )
        state.variables(player).forEach { (key, value) -> scriptVars["ws_var_$key"] = value }
        region?.variables?.forEach { (key, value) -> scriptVars["ws_region_var_$key"] = value }
        KetherShell.eval(
            script,
            ScriptOptions.builder()
                .sender(BukkitPlayer(player))
                .vars(*scriptVars.map { (key, value) -> key to value }.toTypedArray())
                .detailError(true)
                .build(),
        ).exceptionally { error ->
            plugin.logger.warning("Failed to execute Kether in region $regionId: ${error.message}")
            null
        }
    }

    private fun setPlayerVariable(player: Player, value: String) {
        val parts = value.split('=', limit = 2)
        if (parts.size == 2) state.setVariable(player, parts[0].trim(), parts[1])
    }

    private fun setRegionStatus(player: Player, value: String) {
        val parts = value.split(',', limit = 2)
        if (parts.size != 2) return
        val status = GlobalRegionStatus.parse(parts[1]) ?: return
        regions.setStatus(parts[0].trim(), status, true)
    }

    private fun teleport(player: Player, value: String) {
        val parts = value.split(',').map { it.trim() }
        if (parts.size < 4) return
        val world = Bukkit.getWorld(parts[0]) ?: return
        val location = Location(world, parts[1].toDoubleOrNull() ?: return, parts[2].toDoubleOrNull() ?: return, parts[3].toDoubleOrNull() ?: return)
        if (parts.size > 4) location.yaw = parts[4].toFloatOrNull() ?: location.yaw
        if (parts.size > 5) location.pitch = parts[5].toFloatOrNull() ?: location.pitch
        player.teleport(location)
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        lastExecution.keys.removeIf { it.startsWith("${event.player.uniqueId}:") }
    }
}
