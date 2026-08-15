package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.api.RegionCoreService
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.ComparisonOperator
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionType
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionRole
import com.worldscript.foundation.model.RewardDefinition
import com.worldscript.foundation.model.RewardType
import com.worldscript.foundation.model.ScriptDefinition
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class RegionCoreServiceImpl(private val plugin: JavaPlugin) : RegionCoreService {
    private val regions = linkedMapOf<String, RegionDefinition>()
    private val regionDirectory = File(plugin.dataFolder, "regions")

    fun load() {
        regions.clear()
        if (!regionDirectory.exists()) regionDirectory.mkdirs()
        migrateLegacyConfig()
        regionDirectory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file -> readRegion(YamlConfiguration.loadConfiguration(file), file.nameWithoutExtension)?.let { region -> regions[region.id.lowercase()] = region } }
    }

    override fun find(id: String): RegionDefinition? = regions[id.trim().lowercase()]

    override fun effective(id: String): RegionDefinition? = resolve(id.trim().lowercase(), emptySet())

    override fun all(): Collection<RegionDefinition> = regions.values.toList()

    override fun regionsAt(location: Location): List<RegionDefinition> = regionsAt(location, ::isAccessible)

    fun regionsAt(location: Location, accessible: (String) -> Boolean): List<RegionDefinition> = regions.values
        .filter { region -> region.worldName == location.world?.name && RegionGeometry.contains(region.bounds, location.toBlockPosition()) }
        .filter { accessible(it.id) }
        .sortedWith(compareBy<RegionDefinition> { depth(it.id) }.thenBy { it.priority }.thenBy { it.id.lowercase() })

    override fun save(region: RegionDefinition) {
        if (!isValidId(region.id)) return
        if (!regionDirectory.exists()) regionDirectory.mkdirs()
        val data = YamlConfiguration()
        data.set("id", region.id)
        data.set("display-name", region.displayName)
        data.set("world-id", region.worldId)
        data.set("world-name", region.worldName)
        data.set("role", region.role.name.lowercase())
        data.set("content-id", region.contentId)
        data.set("priority", region.priority)
        data.set("parent-id", region.parentId)
        data.set("inherit-parent", region.inheritParent)
        data.set("variables", region.variables)
        data.set("statuses", region.statuses.map { it.name.lowercase() })
        writePosition(data, "min", region.bounds.min)
        writePosition(data, "max", region.bounds.max)
        region.events.forEach { (type, script) ->
            val eventPath = "events.${type.name.lowercase()}"
            data.set("$eventPath.enabled", script.enabled)
            data.set("$eventPath.override-parent", script.overrideParent)
            data.set("$eventPath.cooldown-seconds", script.cooldownSeconds)
            data.set("$eventPath.first-entry-only", script.firstEntryOnly)
            data.set("$eventPath.repeat-entry-only", script.repeatEntryOnly)
            data.set("$eventPath.actions", script.actions.map { mapOf("type" to it.type.name.lowercase(), "value" to it.value) })
            data.set("$eventPath.conditions", script.conditions.map { conditionMap(it) })
            data.set("$eventPath.rewards", script.rewards.map { rewardMap(it) })
        }
        data.save(File(regionDirectory, "${region.id}.yml"))
    }

    override fun delete(id: String): Boolean {
        val removed = regions.remove(id.trim().lowercase()) ?: return false
        File(regionDirectory, "${removed.id}.yml").delete()
        regions.values.filter { it.parentId.equals(removed.id, true) }.toList().forEach { child ->
            val detached = child.copy(parentId = null)
            regions[detached.id.lowercase()] = detached
            save(detached)
        }
        return true
    }

    override fun setParent(id: String, parentId: String?): Boolean {
        val region = find(id) ?: return false
        val normalizedParent = parentId?.trim()?.takeUnless { it.isEmpty() || it.equals("none", true) }
        if (normalizedParent != null && find(normalizedParent) == null) return false
        if (normalizedParent != null && (normalizedParent.equals(region.id, true) || createsCycle(region.id, normalizedParent))) return false
        val updated = region.copy(parentId = normalizedParent)
        regions[region.id.lowercase()] = updated
        save(updated)
        return true
    }

    override fun setVariable(id: String, key: String, value: String): Boolean {
        val region = find(id) ?: return false
        if (key.isBlank()) return false
        val updated = region.copy(variables = region.variables + (key.trim() to value))
        regions[region.id.lowercase()] = updated
        save(updated)
        return true
    }

    override fun setStatus(id: String, status: GlobalRegionStatus, enabled: Boolean): Boolean {
        val region = find(id) ?: return false
        val statuses = region.statuses.toMutableSet()
        if (enabled) {
            statuses.add(status)
            if (status == GlobalRegionStatus.LOCKED) statuses.remove(GlobalRegionStatus.OPEN)
            if (status == GlobalRegionStatus.OPEN) statuses.remove(GlobalRegionStatus.LOCKED)
        } else {
            statuses.remove(status)
        }
        val updated = region.copy(statuses = statuses)
        regions[region.id.lowercase()] = updated
        save(updated)
        return true
    }

    fun toggleEvent(id: String, type: RegionEventType): Boolean {
        val region = find(id) ?: return false
        val current = region.events[type] ?: ScriptDefinition()
        val updated = region.copy(events = region.events + (type to current.copy(enabled = !current.enabled, overrideParent = true)))
        regions[id.lowercase()] = updated
        save(updated)
        return updated.events[type]?.enabled == true
    }

    override fun updateEvent(id: String, type: RegionEventType, update: (ScriptDefinition) -> ScriptDefinition): Boolean {
        val region = find(id) ?: return false
        val current = region.events[type] ?: ScriptDefinition()
        val updated = region.copy(events = region.events + (type to update(current).copy(overrideParent = true)))
        regions[id.lowercase()] = updated
        save(updated)
        return true
    }

    override fun addAction(id: String, type: RegionEventType, action: ActionDefinition): Boolean =
        updateEvent(id, type) { it.copy(actions = it.actions + action) }

    override fun updateAction(id: String, type: RegionEventType, index: Int, action: ActionDefinition): Boolean =
        updateEvent(id, type) { script ->
            if (index !in script.actions.indices) script
            else script.copy(actions = script.actions.toMutableList().also { it[index] = action })
        }

    override fun removeAction(id: String, type: RegionEventType, index: Int): Boolean =
        updateEvent(id, type) { script ->
            if (index !in script.actions.indices) script
            else script.copy(actions = script.actions.toMutableList().also { it.removeAt(index) })
        }

    fun isAccessible(id: String): Boolean {
        val statuses = effective(id)?.statuses ?: return false
        return GlobalRegionStatus.LOCKED !in statuses || GlobalRegionStatus.OPEN in statuses
    }

    fun isAccessible(id: String, playerUnlocked: Boolean): Boolean = playerUnlocked || isAccessible(id)

    fun validate(): List<String> {
        val issues = mutableListOf<String>()
        all().forEach { region ->
            val prefix = "${region.id}"
            if (region.parentId != null && find(region.parentId) == null) {
                issues += "$prefix: parent-id '${region.parentId}' does not exist"
            }
            if (hasParentCycle(region.id)) issues += "$prefix: parent-id creates a cycle"
            region.events.forEach { (eventType, script) ->
                val eventPrefix = "$prefix.events.${eventType.name.lowercase()}"
                if (script.firstEntryOnly && script.repeatEntryOnly) issues += "$eventPrefix: first-entry-only and repeat-entry-only cannot both be true"
                script.conditions.forEachIndexed { index, condition -> validateCondition(issues, eventPrefix, index, condition, region.id) }
                script.rewards.forEachIndexed { index, reward -> validateReward(issues, eventPrefix, index, reward) }
                script.actions.forEachIndexed { index, action -> validateAction(issues, eventPrefix, index, action) }
            }
        }
        return issues
    }

    fun depth(id: String): Int {
        var current = find(id)
        var depth = 0
        val visited = mutableSetOf<String>()
        while (current?.parentId != null && visited.add(current.id.lowercase())) {
            depth++
            current = find(current.parentId!!)
        }
        return depth
    }

    fun variable(id: String, key: String): String? = effective(id)?.variables?.get(key)

    fun create(id: String, displayName: String, first: Location, second: Location): Boolean {
        val cleanId = id.trim()
        if (!isValidId(cleanId) || regions.containsKey(cleanId.lowercase()) || first.world == null || second.world == null) return false
        if (first.world!!.uid != second.world!!.uid) return false
        val region = RegionDefinition(
            id = cleanId,
            displayName = displayName.ifBlank { cleanId },
            worldId = first.world!!.uid.toString(),
            worldName = first.world!!.name,
            bounds = RegionGeometry.from(first.toBlockPosition(), second.toBlockPosition()),
            events = RegionEventType.entries.associateWith { ScriptDefinition() },
        )
        regions[cleanId.lowercase()] = region
        save(region)
        return true
    }

    private fun resolve(id: String, visited: Set<String>): RegionDefinition? {
        val region = find(id) ?: return null
        if (!region.inheritParent || region.parentId == null || region.id.lowercase() in visited) return region
        val parent = resolve(region.parentId.lowercase(), visited + region.id.lowercase()) ?: return region
        val inheritedEvents = parent.events + region.events.filterValues { it.overrideParent }
        return region.copy(
            displayName = region.displayName.ifBlank { parent.displayName },
            events = inheritedEvents,
            variables = parent.variables + region.variables,
            statuses = parent.statuses + region.statuses,
        )
    }

    private fun createsCycle(id: String, parentId: String): Boolean {
        var current = find(parentId)
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current.id.lowercase())) {
            if (current.id.equals(id, true)) return true
            current = current.parentId?.let { find(it) }
        }
        return false
    }

    private fun hasParentCycle(id: String): Boolean {
        var current = find(id)
        val visited = mutableSetOf<String>()
        while (current != null) {
            if (!visited.add(current.id.lowercase())) return true
            current = current.parentId?.let(::find)
        }
        return false
    }

    private fun validateCondition(issues: MutableList<String>, prefix: String, index: Int, condition: ConditionDefinition, regionId: String) {
        val path = "$prefix.conditions[$index]"
        when (condition.type) {
            ConditionType.PLAYER_LEVEL -> issues += "$path: player_level is not available in this version"
            ConditionType.PERMISSION -> if (condition.key.isBlank()) issues += "$path: permission key is empty"
            ConditionType.ITEM -> if (Material.matchMaterial(condition.key.ifBlank { condition.value }) == null) issues += "$path: item material is invalid"
            ConditionType.VARIABLE -> if (condition.key.isBlank()) issues += "$path: variable key is empty"
            ConditionType.REGION_STATUS -> {
                if (condition.key.isNotBlank() && find(condition.key) == null) issues += "$path: target region '${condition.key}' does not exist"
                if (parseGlobalStatus(condition.value) == null) issues += "$path: global region status '${condition.value}' is invalid"
            }
            ConditionType.PLAYER_REGION_STATUS -> {
                if (condition.key.isNotBlank() && find(condition.key) == null) issues += "$path: target region '${condition.key}' does not exist"
                if (condition.value.uppercase() !in setOf("UNLOCKED", "ENTERED", "COMPLETED")) issues += "$path: player region status must be unlocked, entered, or completed"
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
            RewardType.UNLOCK_REGION, RewardType.COMPLETE_REGION -> if (find(reward.value) == null) issues += "$path: target region '${reward.value}' does not exist"
            RewardType.SET_VARIABLE -> if (reward.value.split('=', limit = 2).size != 2) issues += "$path: value must use key=value"
            RewardType.SET_REGION_STATUS -> validateRegionStatusValue(issues, path, reward.value)
            RewardType.EXPERIENCE, RewardType.MONEY -> if (reward.amount < 0) issues += "$path: amount cannot be negative"
        }
    }

    private fun validateAction(issues: MutableList<String>, prefix: String, index: Int, action: ActionDefinition) {
        val path = "$prefix.actions[$index]"
        when (action.type) {
            ActionType.TELEPORT -> {
                val parts = action.value.split(',').map(String::trim)
                if (parts.size < 4 || parts[1].toDoubleOrNull() == null || parts[2].toDoubleOrNull() == null || parts[3].toDoubleOrNull() == null) issues += "$path: teleport must use world,x,y,z"
            }
            ActionType.SET_VARIABLE -> if (action.value.split('=', limit = 2).size != 2) issues += "$path: value must use key=value"
            ActionType.SET_REGION_STATUS -> validateRegionStatusValue(issues, path, action.value)
            ActionType.UNLOCK_REGION, ActionType.COMPLETE_REGION -> if (find(action.value) == null) issues += "$path: target region '${action.value}' does not exist"
            ActionType.GIVE_ITEM -> if (Material.matchMaterial(action.value) == null) issues += "$path: item material '${action.value}' is invalid"
            ActionType.GIVE_EXPERIENCE, ActionType.GIVE_MONEY -> if (action.value.toDoubleOrNull() == null) issues += "$path: value must be numeric"
            ActionType.PLAYER_COMMAND, ActionType.CONSOLE_COMMAND, ActionType.MESSAGE -> if (action.value.isBlank()) issues += "$path: value is empty"
        }
    }

    private fun validateRegionStatusValue(issues: MutableList<String>, path: String, value: String) {
        val parts = value.split(',', limit = 2)
        if (parts.size != 2) {
            issues += "$path: value must use region,status"
            return
        }
        if (find(parts[0].trim()) == null) issues += "$path: target region '${parts[0].trim()}' does not exist"
        if (parseGlobalStatus(parts[1].trim()) == null) issues += "$path: global region status '${parts[1].trim()}' is invalid"
    }

    private fun readRegion(section: ConfigurationSection, fallbackId: String): RegionDefinition? {
        val id = section.getString("id", fallbackId)?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val worldName = section.getString("world-name") ?: return null
        return RegionDefinition(
            id = id,
            displayName = section.getString("display-name", id) ?: id,
            worldId = section.getString("world-id", worldName) ?: worldName,
            worldName = worldName,
            bounds = RegionGeometry.from(readPosition(section, "min"), readPosition(section, "max")),
            role = parseEnum<RegionRole>(section.getString("role")) ?: RegionRole.OPEN_ZONE,
            contentId = section.getString("content-id", "") ?: "",
            priority = section.getInt("priority", 0),
            events = RegionEventType.entries.associateWith { type -> readScript(section, type) },
            parentId = section.getString("parent-id")?.takeUnless { it.isBlank() },
            inheritParent = section.getBoolean("inherit-parent", true),
            variables = section.getConfigurationSection("variables")?.getKeys(false)?.associateWith { key -> section.getString("variables.$key", "") ?: "" } ?: emptyMap(),
            statuses = section.getStringList("statuses").mapNotNull(::parseGlobalStatus).toSet(),
        )
    }

    private fun readScript(section: ConfigurationSection, type: RegionEventType): ScriptDefinition {
        val path = "events.${type.name.lowercase()}"
        val actions = section.getMapList("$path.actions").mapNotNull { raw ->
            val actionType = parseEnum<ActionType>(raw["type"]?.toString()) ?: return@mapNotNull null
            val value = raw["value"]?.toString() ?: ""
            if (actionType == ActionType.SET_REGION_STATUS && legacyCompletionRegion(value) != null) {
                ActionDefinition(ActionType.COMPLETE_REGION, legacyCompletionRegion(value)!!)
            } else {
                ActionDefinition(actionType, value)
            }
        }
        return ScriptDefinition(
            enabled = section.getBoolean("$path.enabled", true),
            cooldownSeconds = section.getLong("$path.cooldown-seconds", 0).coerceAtLeast(0),
            actions = actions,
            conditions = readConditions(section.getMapList("$path.conditions")),
            rewards = readRewards(section.getMapList("$path.rewards")),
            overrideParent = section.getBoolean("$path.override-parent", false),
            firstEntryOnly = section.getBoolean("$path.first-entry-only", false),
            repeatEntryOnly = section.getBoolean("$path.repeat-entry-only", false),
        )
    }

    private fun readConditions(raw: List<Map<*, *>>): List<ConditionDefinition> = raw.mapNotNull { item ->
        val type = parseEnum<ConditionType>(item["type"]?.toString()) ?: return@mapNotNull null
        ConditionDefinition(
            type = type,
            key = item["key"]?.toString() ?: "",
            value = item["value"]?.toString() ?: "",
            operator = parseEnum<ComparisonOperator>(item["operator"]?.toString()) ?: ComparisonOperator.EQUALS,
            amount = item["amount"]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        )
    }

    private fun readRewards(raw: List<Map<*, *>>): List<RewardDefinition> = raw.mapNotNull { item ->
        val type = parseEnum<RewardType>(item["type"]?.toString()) ?: return@mapNotNull null
        val value = item["value"]?.toString() ?: ""
        RewardDefinition(
            if (type == RewardType.SET_REGION_STATUS && legacyCompletionRegion(value) != null) RewardType.COMPLETE_REGION else type,
            legacyCompletionRegion(value) ?: value,
            item["amount"]?.toString()?.toDoubleOrNull() ?: 1.0,
            item["once"]?.toString()?.toBooleanStrictOrNull() ?: false,
        )
    }

    private fun conditionMap(condition: ConditionDefinition) = mapOf(
        "type" to condition.type.name.lowercase(), "key" to condition.key, "value" to condition.value,
        "operator" to condition.operator.name.lowercase(), "amount" to condition.amount,
    )

    private fun rewardMap(reward: RewardDefinition) = mapOf(
        "type" to reward.type.name.lowercase(), "value" to reward.value, "amount" to reward.amount,
        "once" to reward.once,
    )

    private inline fun <reified T : Enum<T>> parseEnum(value: String?): T? = value?.trim()?.uppercase()?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun parseGlobalStatus(value: String?): GlobalRegionStatus? = GlobalRegionStatus.parse(value)

    private fun legacyCompletionRegion(value: String): String? {
        val parts = value.split(',', limit = 2)
        return parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
            ?.takeIf { parts.getOrNull(1)?.trim()?.equals("completed", true) == true }
    }

    private fun readPosition(section: ConfigurationSection, key: String) = BlockPosition(
        section.getInt("$key.x"), section.getInt("$key.y"), section.getInt("$key.z"),
    )

    private fun writePosition(data: YamlConfiguration, key: String, position: BlockPosition) {
        data.set("$key.x", position.x)
        data.set("$key.y", position.y)
        data.set("$key.z", position.z)
    }

    private fun isValidId(id: String): Boolean = id.isNotBlank() && id != "." && id != ".." && !id.contains('/') && !id.contains('\\') && !id.contains(' ')

    private fun migrateLegacyConfig() {
        if (regionDirectory.listFiles { file -> file.extension.equals("yml", true) }?.isNotEmpty() == true) return
        val legacy = plugin.config.getConfigurationSection("regions") ?: return
        legacy.getKeys(false).forEach { id ->
            val value = legacy.getConfigurationSection(id) ?: return@forEach
            readRegion(value, id)?.let { save(it) }
        }
        if (legacy.getKeys(false).isNotEmpty()) {
            plugin.config.set("regions", null)
            plugin.saveConfig()
        }
    }

    private fun Location.toBlockPosition() = BlockPosition(blockX, blockY, blockZ)
}
