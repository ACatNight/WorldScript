package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.api.RegionCoreService
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** Single source of truth for region unlock and entry-condition checks. */
class RegionAccessService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreService,
    private val state: PlayerVariableService,
    private val conditions: ConditionEvaluator,
) {
    sealed class AccessResult {
        object Allowed : AccessResult()
        object DeniedLocked : AccessResult()
        data class DeniedCondition(
            val reason: String,
            val actions: List<ActionDefinition>,
        ) : AccessResult()
    }

    fun checkEnter(player: Player, region: RegionDefinition): AccessResult {
        if (!regions.isAccessible(region.id, state.isRegionUnlocked(player, region.id))) {
            return AccessResult.DeniedLocked
        }
        if (!plugin.config.getBoolean("conditions.enabled", false)) return AccessResult.Allowed
        val script = regions.effective(region.id)?.events?.get(RegionEventType.ENTER)
            ?: return AccessResult.Allowed
        if (!script.conditionsEnabled || script.conditions.isEmpty() ||
            conditions.allMet(player, region.id, script.conditions, script.conditionMode)
        ) return AccessResult.Allowed
        val failure = conditions.firstFailure(player, region.id, script.conditions, script.conditionMode)
        return AccessResult.DeniedCondition(
            failure?.let(conditions::describe).orEmpty(),
            script.conditionFailureActions,
        )
    }

    fun canDiscover(player: Player, region: RegionDefinition): Boolean {
        if (state.isRegionUnlocked(player, region.id)) return false
        if (!plugin.config.getBoolean("conditions.enabled", false)) return true
        val script = regions.effective(region.id)?.events?.get(RegionEventType.ENTER)
            ?: return true
        return !script.conditionsEnabled || script.conditions.isEmpty() ||
            conditions.allMet(player, region.id, script.conditions, script.conditionMode)
    }
}
