package com.worldscript.modules.l2.rpg

import com.worldscript.foundation.model.ComparisonOperator
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionType
import com.worldscript.foundation.model.ConditionMode
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.Lang
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class ConditionEvaluator(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) {
    private val lang = Lang(plugin)

    fun allMet(player: Player, regionId: String, conditions: List<ConditionDefinition>, mode: ConditionMode = ConditionMode.AND): Boolean =
        if (conditions.isEmpty()) true
        else if (mode == ConditionMode.OR) conditions.any { evaluate(player, regionId, it) }
        else conditions.all { evaluate(player, regionId, it) }

    fun firstFailure(player: Player, regionId: String, conditions: List<ConditionDefinition>, mode: ConditionMode = ConditionMode.AND): ConditionDefinition? =
        if (allMet(player, regionId, conditions, mode)) null
        else if (mode == ConditionMode.OR) conditions.firstOrNull { !evaluate(player, regionId, it) } ?: conditions.firstOrNull()
        else conditions.firstOrNull { !evaluate(player, regionId, it) }

    fun describe(condition: ConditionDefinition): String = when (condition.type) {
        ConditionType.PLAYER_LEVEL -> lang.text("condition-reason-player-level", "player level conditions are disabled")
        ConditionType.PERMISSION -> lang.text("condition-reason-permission", "permission %key%", "key" to condition.key)
        ConditionType.ITEM -> lang.text(
            "condition-reason-item",
            "item %key% x%amount%",
            "key" to condition.key.ifBlank { condition.value },
            "amount" to condition.amount,
        )
        ConditionType.VARIABLE -> lang.text(
            "condition-reason-variable",
            "variable %key% %operator% %value%",
            "key" to condition.key,
            "operator" to condition.operator.name.lowercase(),
            "value" to condition.value,
        )
        ConditionType.REGION_STATUS -> lang.text(
            "condition-reason-region-status",
            "region %key% status %value%",
            "key" to condition.key,
            "value" to condition.value,
        )
        ConditionType.PLAYER_REGION_STATUS -> lang.text(
            "condition-reason-player-region-status",
            "player region %key% status %value%",
            "key" to condition.key,
            "value" to condition.value,
        )
    }

    private fun evaluate(player: Player, regionId: String, condition: ConditionDefinition): Boolean = when (condition.type) {
        ConditionType.PLAYER_LEVEL -> false
        ConditionType.PERMISSION -> comparePermission(player.hasPermission(condition.key), condition.operator)
        ConditionType.ITEM -> {
            Material.matchMaterial(condition.key.ifBlank { condition.value })?.let { material ->
                player.inventory.containsAtLeast(ItemStack(material), condition.amount)
            } ?: false
        }
        ConditionType.VARIABLE -> {
            val current = if (condition.key.contains('%')) {
                if (plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) resolvePlaceholder(player, condition.key) else null
            } else if (condition.key.startsWith("region.", true)) {
                regions.variable(regionId, condition.key.substringAfter('.'))
            } else {
                state.variable(player, condition.key)
            }
            compare(current, condition.value, condition.operator)
        }
        ConditionType.REGION_STATUS -> {
            val targetRegion = condition.key.ifBlank { regionId }
            GlobalRegionStatus.parse(condition.value)?.let { status ->
                status in (regions.effective(targetRegion)?.statuses ?: emptySet())
            } ?: false
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

    private fun resolvePlaceholder(player: Player, value: String): String? = runCatching {
        val type = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
        (type.getMethod("setPlaceholders", Player::class.java, String::class.java)
            .invoke(null, player, value) as String).takeUnless { it.equals(value, true) }
    }.getOrNull()

    private fun compare(current: String?, expected: String, operator: ComparisonOperator): Boolean {
        if (operator == ComparisonOperator.EXISTS) return !current.isNullOrBlank()
        // Missing/unresolved values must fail closed. Otherwise a typo or a
        // missing PlaceholderAPI expansion would satisfy `!=` entry rules.
        if (current.isNullOrBlank() || current.contains('%')) return false
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
