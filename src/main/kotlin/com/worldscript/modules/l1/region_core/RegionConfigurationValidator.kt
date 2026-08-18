package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RewardDefinition
import com.worldscript.foundation.model.RewardType
import org.bukkit.Material

internal class RegionConfigurationValidator(
    private val findRegion: (String) -> RegionDefinition?,
    private val hasParentCycle: (String) -> Boolean,
) {
    fun validate(regions: Collection<RegionDefinition>, loadIssues: Collection<String>): List<String> {
        val issues = loadIssues.toMutableList()
        regions.forEach { region ->
            validateRegion(region, issues)
        }
        return issues
    }

    private fun validateRegion(region: RegionDefinition, issues: MutableList<String>) {
        val prefix = region.id
        if (region.parentId != null && findRegion(region.parentId) == null) {
            issues += "$prefix: parent-id '${region.parentId}' does not exist"
        }
        region.parentId?.let { parentId ->
            findRegion(parentId)?.let { parent ->
                when {
                    parent.worldName != region.worldName -> issues += "$prefix: parent '$parentId' is in another world"
                    !RegionGeometry.encloses(parent.bounds, region.bounds) -> issues += "$prefix: bounds are not fully inside parent '$parentId'"
                }
            }
        }
        if (hasParentCycle(region.id)) issues += "$prefix: parent-id creates a cycle"
        region.events.forEach { (eventType, script) ->
            val eventPrefix = "$prefix.events.${eventType.name.lowercase()}"
            if (script.firstEntryOnly && script.repeatEntryOnly) {
                issues += "$eventPrefix: first-entry-only and repeat-entry-only cannot both be true"
            }
            script.conditions.forEachIndexed { index, condition -> validateCondition(issues, eventPrefix, index, condition, region.id) }
            script.rewards.forEachIndexed { index, reward -> validateReward(issues, eventPrefix, index, reward) }
            script.actions.forEachIndexed { index, action -> validateAction(issues, eventPrefix, index, action) }
        }
    }

    private fun validateCondition(issues: MutableList<String>, prefix: String, index: Int, condition: ConditionDefinition, regionId: String) {
        val path = "$prefix.conditions[$index]"
        when (condition.type) {
            ConditionType.PLAYER_LEVEL -> issues += "$path: player_level is not available in this version"
            ConditionType.PERMISSION -> if (condition.key.isBlank()) issues += "$path: permission key is empty"
            ConditionType.ITEM -> if (Material.matchMaterial(condition.key.ifBlank { condition.value }) == null) issues += "$path: item material is invalid"
            ConditionType.VARIABLE -> if (condition.key.isBlank()) issues += "$path: variable key is empty"
            ConditionType.REGION_STATUS -> {
                if (condition.key.isNotBlank() && findRegion(condition.key) == null) issues += "$path: target region '${condition.key}' does not exist"
                if (GlobalRegionStatus.parse(condition.value) == null) issues += "$path: global region status '${condition.value}' is invalid"
            }
            ConditionType.PLAYER_REGION_STATUS -> {
                if (condition.key.isNotBlank() && findRegion(condition.key) == null) issues += "$path: target region '${condition.key}' does not exist"
                if (condition.value.uppercase() !in PLAYER_REGION_STATUSES) issues += "$path: player region status must be unlocked, entered, or completed"
            }
        }
        if (condition.type == ConditionType.ITEM && condition.amount < 1) issues += "$path: item amount must be at least 1"
        if (condition.type == ConditionType.VARIABLE && condition.key.startsWith("region.", true) && condition.key.substringAfter('.').isBlank()) issues += "$path: region variable name is empty"
        if (regionId.isBlank()) issues += "$prefix: region id is empty"
    }

    private fun validateReward(issues: MutableList<String>, prefix: String, index: Int, reward: RewardDefinition) {
        val path = "$prefix.rewards[$index]"
        when (reward.type) {
            RewardType.ITEM -> if (Material.matchMaterial(reward.value) == null) issues += "$path: item material '${reward.value}' is invalid"
            RewardType.COMMAND, RewardType.MESSAGE -> if (reward.value.isBlank()) issues += "$path: value is empty"
            RewardType.UNLOCK_REGION, RewardType.COMPLETE_REGION -> if (findRegion(reward.value) == null) issues += "$path: target region '${reward.value}' does not exist"
            RewardType.SET_VARIABLE -> if (reward.value.split('=', limit = 2).size != 2) issues += "$path: value must use key=value"
            RewardType.SET_REGION_STATUS -> validateRegionStatusValue(issues, path, reward.value)
            RewardType.EXPERIENCE, RewardType.MONEY -> if (reward.amount < 0) issues += "$path: amount cannot be negative"
        }
    }

    private fun validateAction(issues: MutableList<String>, prefix: String, index: Int, action: ActionDefinition) {
        val path = "$prefix.actions[$index]"
        fun value(name: String): String = action.parameters[name]?.takeIf { it.isNotBlank() } ?: action.value
        when (action.type) {
            ActionType.KETHER -> if (action.value.isBlank()) issues += "$path: script is empty"
            ActionType.TEXT_DISPLAY -> if (value("title").isBlank()) issues += "$path: title is empty"
            ActionType.SOUND -> if (value("sound").isBlank()) issues += "$path: sound is empty"
            ActionType.TELEPORT -> {
                val parts = value("location").split(',').map(String::trim)
                val parameters = listOf(value("world"), value("x"), value("y"), value("z"))
                if ((parts.size < 4 || parts[1].toDoubleOrNull() == null || parts[2].toDoubleOrNull() == null || parts[3].toDoubleOrNull() == null) &&
                    (parameters[0].isBlank() || parameters.drop(1).any { it.toDoubleOrNull() == null })
                ) issues += "$path: teleport must provide world,x,y,z"
            }
            ActionType.SET_VARIABLE -> if (action.parameters.isNotEmpty()) {
                if (value("key").isBlank()) issues += "$path: variable key is empty"
            } else if (action.value.split('=', limit = 2).size != 2) issues += "$path: value must use key=value"
            ActionType.SET_REGION_STATUS -> if (action.parameters.isNotEmpty()) {
                val status = GlobalRegionStatus.parse(value("status"))
                if (findRegion(value("region")) == null) issues += "$path: target region '${value("region")}' does not exist"
                if (status == null) issues += "$path: region status '${value("status")}' is invalid"
            } else validateRegionStatusValue(issues, path, action.value)
            ActionType.UNLOCK_REGION, ActionType.COMPLETE_REGION -> if (findRegion(value("region")) == null) issues += "$path: target region '${value("region")}' does not exist"
            ActionType.GIVE_ITEM -> if (Material.matchMaterial(value("material")) == null) issues += "$path: item material '${value("material")}' is invalid"
            ActionType.GIVE_EXPERIENCE, ActionType.GIVE_MONEY -> if (value("amount").toDoubleOrNull() == null) issues += "$path: value must be numeric"
            ActionType.PLAYER_COMMAND, ActionType.CONSOLE_COMMAND -> if (value("command").isBlank()) issues += "$path: command is empty"
            ActionType.MESSAGE -> if (value("text").isBlank()) issues += "$path: message is empty"
        }
    }

    private fun validateRegionStatusValue(issues: MutableList<String>, path: String, value: String) {
        val parts = value.split(',', limit = 2)
        if (parts.size != 2) {
            issues += "$path: value must use region,status"
            return
        }
        if (findRegion(parts[0].trim()) == null) issues += "$path: target region '${parts[0].trim()}' does not exist"
        if (GlobalRegionStatus.parse(parts[1].trim()) == null) issues += "$path: global region status '${parts[1].trim()}' is invalid"
    }

    private companion object {
        val PLAYER_REGION_STATUSES = setOf("UNLOCKED", "ENTERED", "COMPLETED")
    }
}
