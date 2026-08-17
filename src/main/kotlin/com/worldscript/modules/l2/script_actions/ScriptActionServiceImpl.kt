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
import org.bukkit.Sound
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
            val values = action.parameters.mapValues { (_, raw) -> expand(raw, player, regionId) }
            val value = expand(action.value, player, regionId)
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
                    ActionType.TEXT_DISPLAY -> player.sendTitle(
                        color(values["title"] ?: value),
                        color(values["subtitle"] ?: ""),
                        values["fade-in"]?.toIntOrNull() ?: 0,
                        values["stay"]?.toIntOrNull() ?: 20,
                        values["fade-out"]?.toIntOrNull() ?: 0,
                    )
                    ActionType.SOUND -> playSound(player, values["sound"] ?: value, values["volume"]?.toFloatOrNull() ?: 1.0f, values["pitch"]?.toFloatOrNull() ?: 1.0f)
                    ActionType.PLAYER_COMMAND -> player.performCommand((values["command"] ?: value).removePrefix("/"))
                    ActionType.CONSOLE_COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), (values["command"] ?: value).removePrefix("/"))
                    ActionType.MESSAGE -> player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', values["text"] ?: value))
                    ActionType.TELEPORT -> teleport(player, values["location"] ?: value.ifBlank {
                        listOf(values["world"], values["x"], values["y"], values["z"]).joinToString(",")
                    })
                    ActionType.SET_VARIABLE -> {
                        val variable = values["key"]?.let { "$it=${values["value"].orEmpty()}" } ?: value
                        setPlayerVariable(player, variable)
                    }
                    ActionType.SET_REGION_STATUS -> {
                        val status = values["region"]?.let { "$it,${values["status"].orEmpty()}" } ?: value
                        setRegionStatus(player, status)
                    }
                    ActionType.GIVE_ITEM -> rewards.grant(player, regionId, listOf(RewardDefinition(RewardType.ITEM, values["material"] ?: value, values["amount"]?.toDoubleOrNull() ?: 1.0)))
                    ActionType.GIVE_EXPERIENCE -> rewards.grant(player, regionId, listOf(RewardDefinition(RewardType.EXPERIENCE, values["amount"] ?: value)))
                    ActionType.GIVE_MONEY -> rewards.grant(player, regionId, listOf(RewardDefinition(RewardType.MONEY, values["amount"] ?: value)))
                    ActionType.UNLOCK_REGION -> state.unlockRegion(player, values["region"] ?: value)
                    ActionType.COMPLETE_REGION -> state.markRegionCompleted(player, values["region"] ?: value)
                }
            }.onFailure { plugin.logger.warning("Failed to execute ${action.type} in region $regionId: ${it.message}") }
        }
    }

    private fun expand(value: String, player: Player, regionId: String): String = value
        .replace("%player%", player.name)
        .replace("%player_name%", player.name)
        .replace("%uuid%", player.uniqueId.toString())
        .replace("%region%", regionId)
        .replace("%world%", player.world.name)
        .replace("%region_role%", regions.effective(regionId)?.role?.name?.lowercase() ?: "")
        .replace("%content_id%", regions.effective(regionId)?.contentId ?: "")
        .let { expanded -> regions.effective(regionId)?.variables?.entries?.fold(expanded) { text, (key, variable) -> text.replace("%var.$key%", variable) } ?: expanded }

    private fun executeKether(player: Player, regionId: String, script: String) {
        if (executeTitleCompat(player, script)) return
        val normalizedScript = script.replace('&', '\u00a7')
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
            normalizedScript,
            ScriptOptions.builder()
                .sender(BukkitPlayer(player))
                .vars(*scriptVars.map { (key, value) -> key to value }.toTypedArray())
                .detailError(true)
                .build(),
        ).exceptionally { error ->
            val message = error.cause?.message ?: error.message ?: error.javaClass.simpleName
            val hint = if (Regex("\\btitle\\s+color\\b", RegexOption.IGNORE_CASE).containsMatchIn(script)) {
                " Use title \"...\" subtitle \"...\" by fadeIn stay fadeOut; put color codes inside the quoted text."
            } else ""
            plugin.logger.warning("Failed to execute Kether in region $regionId: $message.$hint")
            null
        }
    }

    /** Keeps the common title form stable across TabooLib parser variants. */
    private fun executeTitleCompat(player: Player, script: String): Boolean {
        val match = Regex(
            "^\\s*title\\s+\"([^\"]*)\"(?:\\s+subtitle\\s+\"([^\"]*)\")?(?:\\s+(?:by|with)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+))?\\s*$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(script) ?: return false
        val groups = match.groupValues
        player.sendTitle(
            color(groups[1]),
            color(groups.getOrNull(2).orEmpty()),
            groups.getOrNull(3)?.toIntOrNull() ?: 0,
            groups.getOrNull(4)?.toIntOrNull() ?: 20,
            groups.getOrNull(5)?.toIntOrNull() ?: 0,
        )
        return true
    }

    private fun color(value: String): String = org.bukkit.ChatColor.translateAlternateColorCodes('&', value)

    private fun playSound(player: Player, name: String, volume: Float, pitch: Float) {
        val sound = resolveSound(name) ?: run {
            plugin.logger.warning("Unsupported sound '$name'; action was skipped.")
            return
        }
        player.playSound(player.location, sound, volume, pitch)
    }

    private fun resolveSound(value: String): Sound? {
        val name = value.trim().uppercase()
        return runCatching { Sound.valueOf(name) }.getOrNull() ?: when (name) {
            "BLOCK_NOTE_BLOCK_PLING" -> runCatching { Sound.valueOf("BLOCK_NOTE_PLING") }.getOrNull()
            "BLOCK_NOTE_PLING" -> runCatching { Sound.valueOf("BLOCK_NOTE_BLOCK_PLING") }.getOrNull()
            else -> null
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
