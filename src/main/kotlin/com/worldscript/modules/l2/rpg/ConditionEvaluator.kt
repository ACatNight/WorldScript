package com.worldscript.modules.l2.rpg

import com.worldscript.foundation.model.ComparisonOperator
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ConditionEvaluator(
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) {
    fun allMet(player: Player, regionId: String, conditions: List<ConditionDefinition>): Boolean =
        conditions.all { evaluate(player, regionId, it) }

    fun firstFailure(player: Player, regionId: String, conditions: List<ConditionDefinition>): ConditionDefinition? =
        conditions.firstOrNull { !evaluate(player, regionId, it) }

    fun describe(condition: ConditionDefinition): String = when (condition.type) {
        ConditionType.PLAYER_LEVEL -> "player level conditions are disabled"
        ConditionType.PERMISSION -> "permission ${condition.key}"
        ConditionType.ITEM -> "item ${condition.key.ifBlank { condition.value }} x${condition.amount}"
        ConditionType.VARIABLE -> "variable ${condition.key} ${condition.operator.name.lowercase()} ${condition.value}"
        ConditionType.REGION_STATUS -> "region ${condition.key} status ${condition.value}"
        ConditionType.PLAYER_REGION_STATUS -> "player region ${condition.key} status ${condition.value}"
    }

    private fun evaluate(player: Player, regionId: String, condition: ConditionDefinition): Boolean = when (condition.type) {
        ConditionType.PLAYER_LEVEL -> false
        ConditionType.PERMISSION -> comparePermission(player.hasPermission(condition.key), condition.operator)
        ConditionType.ITEM -> {
            val material = Material.matchMaterial(condition.key.ifBlank { condition.value }) ?: return false
            player.inventory.containsAtLeast(ItemStack(material), condition.amount)
        }
        ConditionType.VARIABLE -> {
            val current = if (condition.key.startsWith("region.", true)) {
                regions.variable(regionId, condition.key.substringAfter('.'))
            } else {
                state.variable(player, condition.key)
            }
            compare(current, condition.value, condition.operator)
        }
        ConditionType.REGION_STATUS -> {
            val targetRegion = condition.key.ifBlank { regionId }
            val status = GlobalRegionStatus.parse(condition.value) ?: return false
            status in (regions.effective(targetRegion)?.statuses ?: emptySet())
        }
        ConditionType.PLAYER_REGION_STATUS -> {
            val targetRegion = condition.key.ifBlank { regionId }
            when (condition.value.uppercase()) {
                "UNLOCKED" -> state.isRegionUnlocked(player, targetRegion)
                "ENTERED" -> state.hasEnteredRegion(player, targetRegion)
                "COMPLETED" -> state.isRegionCompleted(player, targetRegion)
                else -> false
            }
        }
    }

    private fun comparePermission(current: Boolean, operator: ComparisonOperator): Boolean = when (operator) {
        ComparisonOperator.NOT_EQUALS -> !current
        ComparisonOperator.EXISTS, ComparisonOperator.EQUALS -> current
        else -> current
    }

    private fun compare(current: String?, expected: String, operator: ComparisonOperator): Boolean {
        if (operator == ComparisonOperator.EXISTS) return !current.isNullOrBlank()
        if (current == null) return operator == ComparisonOperator.NOT_EQUALS
        val numberCurrent = current.toDoubleOrNull()
        val numberExpected = expected.toDoubleOrNull()
        if (numberCurrent != null && numberExpected != null) return when (operator) {
            ComparisonOperator.EQUALS -> numberCurrent == numberExpected
            ComparisonOperator.NOT_EQUALS -> numberCurrent != numberExpected
            ComparisonOperator.GREATER -> numberCurrent > numberExpected
            ComparisonOperator.GREATER_OR_EQUAL -> numberCurrent >= numberExpected
            ComparisonOperator.LESS -> numberCurrent < numberExpected
            ComparisonOperator.LESS_OR_EQUAL -> numberCurrent <= numberExpected
            ComparisonOperator.EXISTS -> true
        }
        return when (operator) {
            ComparisonOperator.EQUALS -> current.equals(expected, true)
            ComparisonOperator.NOT_EQUALS -> !current.equals(expected, true)
            ComparisonOperator.GREATER -> current > expected
            ComparisonOperator.GREATER_OR_EQUAL -> current >= expected
            ComparisonOperator.LESS -> current < expected
            ComparisonOperator.LESS_OR_EQUAL -> current <= expected
            ComparisonOperator.EXISTS -> true
        }
    }
}
