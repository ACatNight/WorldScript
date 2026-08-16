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
import com.worldscript.foundation.model.RegionBounds
import com.worldscript.foundation.model.RegionParticleDefinition
import com.worldscript.foundation.model.ScriptDefinition
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class RegionCoreServiceImpl(private val plugin: JavaPlugin) : RegionCoreService {
    private val regions = linkedMapOf<String, RegionDefinition>()
    private val loadIssues = mutableListOf<String>()
    private val regionDirectory = File(plugin.dataFolder, "regions")

    fun load() {
        regions.clear()
        loadIssues.clear()
        if (!regionDirectory.exists()) regionDirectory.mkdirs()
        migrateLegacyConfig()
        regionDirectory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file -> readRegion(YamlConfiguration.loadConfiguration(file), file.nameWithoutExtension, "regions/${file.name}")?.let { region -> regions[region.id.lowercase()] = region } }
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
        data.set("schema", 2)
        data.set("id", region.id)
        data.set("identity.name", region.displayName)
        data.set("identity.role", region.role.name.lowercase())
        data.set("identity.content-id", region.contentId)
        data.set("identity.parent", region.parentId)
        data.set("location.world", region.worldName)
        data.set("location.world-id", region.worldId)
        data.set("location.priority", region.priority)
        data.set("state.inherit", region.inheritParent)
        data.set("state.statuses", region.statuses.map { it.name.lowercase() })
        data.set("variables", region.variables)
        region.particle?.let { particle ->
            data.set("particle.enabled", particle.enabled)
            data.set("particle.preset", particle.preset)
            data.set("particle.type", particle.type)
            data.set("particle.count", particle.count)
            data.set("particle.interval-ticks", particle.intervalTicks)
            data.set("particle.spread.x", particle.spreadX)
            data.set("particle.spread.y", particle.spreadY)
            data.set("particle.spread.z", particle.spreadZ)
            data.set("particle.speed", particle.speed)
        }
        writePosition(data, "location.min", region.bounds.min)
        writePosition(data, "location.max", region.bounds.max)
        region.events.forEach { (type, script) ->
            val eventPath = "events.${type.name.lowercase()}"
            data.set("$eventPath.enabled", script.enabled)
            data.set("$eventPath.inherit", !script.overrideParent)
            data.set("$eventPath.cooldown-seconds", script.cooldownSeconds)
            data.set("$eventPath.mode", eventMode(script))
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

    override fun updateParticle(id: String, particle: RegionParticleDefinition?): Boolean {
        val region = find(id) ?: return false
        val updated = region.copy(particle = particle)
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
        val issues = loadIssues.toMutableList()
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
        val firstWorld = first.world ?: return false
        val secondWorld = second.world ?: return false
        if (!isValidId(cleanId) || regions.containsKey(cleanId.lowercase())) return false
        if (firstWorld.uid != secondWorld.uid) return false
        val bounds = RegionGeometry.from(first.toBlockPosition(), second.toBlockPosition())
        val region = RegionDefinition(
            id = cleanId,
            displayName = displayName.ifBlank { cleanId },
            worldId = firstWorld.uid.toString(),
            worldName = firstWorld.name,
            bounds = bounds,
            parentId = regions.values
                .asSequence()
                .filter { it.worldName == firstWorld.name && contains(it.bounds, bounds) }
                .minByOrNull { volume(it.bounds) }
                ?.id,
            events = RegionEventType.entries.associateWith { type ->
                ScriptDefinition(enabled = type != RegionEventType.LEFT_CLICK && type != RegionEventType.RIGHT_CLICK)
            },
        )
        regions[cleanId.lowercase()] = region
        save(region)
        return true
    }

    private fun contains(outer: RegionBounds, inner: RegionBounds): Boolean =
        RegionGeometry.contains(outer, inner.min) && RegionGeometry.contains(outer, inner.max)

    private fun volume(bounds: com.worldscript.foundation.model.RegionBounds): Long =
        (bounds.max.x.toLong() - bounds.min.x + 1) *
            (bounds.max.y.toLong() - bounds.min.y + 1) *
            (bounds.max.z.toLong() - bounds.min.z + 1)

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
            particle = region.particle ?: parent.particle,
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
            ActionType.KETHER -> if (action.value.isBlank()) issues += "$path: script is empty"
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

    private fun readRegion(section: ConfigurationSection, fallbackId: String, source: String): RegionDefinition? {
        val id = section.getString("id", fallbackId)?.trim().takeUnless { it.isNullOrBlank() }
            ?: return loadIssue(source, "id", "is required").let { null }
        if (!isValidId(id)) loadIssue(source, "id", "'$id' is not a valid region id")
        val worldName = (section.getString("location.world") ?: section.getString("world-name"))?.takeUnless { it.isBlank() }
            ?: return loadIssue(source, "location.world", "is required").let { null }
        val roleValue = section.getString("identity.role") ?: section.getString("role")
        val role = parseEnum<RegionRole>(roleValue)
        if (!roleValue.isNullOrBlank() && role == null) loadIssue(source, "role", "unknown role '$roleValue'")
        val statusPath = if (section.contains("state.statuses")) "state.statuses" else "statuses"
        val statuses = section.getStringList(statusPath).mapIndexedNotNull { index, value ->
            parseGlobalStatus(value) ?: run {
                loadIssue(source, "$statusPath[$index]", "unknown global status '$value'")
                null
            }
        }.toSet()
        val parentId = (section.getString("identity.parent") ?: section.getString("parent-id"))?.takeUnless { it.isBlank() }
        return RegionDefinition(
            id = id,
            displayName = section.getString("identity.name") ?: section.getString("display-name", id) ?: id,
            worldId = section.getString("location.world-id") ?: section.getString("world-id", worldName) ?: worldName,
            worldName = worldName,
            bounds = RegionGeometry.from(readPosition(section, "location.min", "min", source), readPosition(section, "location.max", "max", source)),
            role = role ?: RegionRole.OPEN_ZONE,
            contentId = section.getString("identity.content-id") ?: section.getString("content-id", "") ?: "",
            priority = if (section.contains("location.priority")) section.getInt("location.priority") else section.getInt("priority", 0),
            events = RegionEventType.entries.associateWith { type -> readScript(section, type, source) },
            parentId = parentId,
            inheritParent = if (section.contains("state.inherit")) section.getBoolean("state.inherit") else section.getBoolean("inherit-parent", true),
            variables = section.getConfigurationSection("variables")?.getKeys(false)?.associateWith { key -> section.getString("variables.$key", "") ?: "" } ?: emptyMap(),
            statuses = statuses,
            particle = readParticle(section, source),
        )
    }

    private fun readParticle(section: ConfigurationSection, source: String): RegionParticleDefinition? {
        if (!section.isConfigurationSection("particle")) return null
        val enabled = section.getBoolean("particle.enabled", true)
        val type = section.getString("particle.type", "END_ROD") ?: "END_ROD"
        if (enabled && runCatching { org.bukkit.Particle.valueOf(type.uppercase()) }.isFailure) {
            loadIssue(source, "particle.type", "unknown particle '$type'")
            return null
        }
        return RegionParticleDefinition(
            enabled = enabled,
            preset = section.getString("particle.preset", "AMBIENT")?.uppercase()?.let { preset ->
                if (preset in PARTICLE_PRESETS) preset else "AMBIENT"
            } ?: "AMBIENT",
            type = type.uppercase(),
            count = section.getInt("particle.count", 2).coerceIn(1, 64),
            intervalTicks = section.getLong("particle.interval-ticks", 20).coerceAtLeast(1),
            spreadX = section.getDouble("particle.spread.x", 1.5).coerceIn(0.0, 16.0),
            spreadY = section.getDouble("particle.spread.y", 0.8).coerceIn(0.0, 16.0),
            spreadZ = section.getDouble("particle.spread.z", 1.5).coerceIn(0.0, 16.0),
            speed = section.getDouble("particle.speed", 0.0).coerceIn(0.0, 4.0),
        )
    }

    private companion object {
        val PARTICLE_PRESETS = setOf("AMBIENT", "BORDER", "PORTAL", "ENTRANCE", "WARNING")
    }

    private fun readScript(section: ConfigurationSection, type: RegionEventType, source: String): ScriptDefinition {
        val path = "events.${type.name.lowercase()}"
        val mode = section.getString("$path.mode")?.trim()?.lowercase()
        val actions = section.getMapList("$path.actions").mapIndexedNotNull { index, raw ->
            val rawType = raw["type"]?.toString()
            val actionType = parseEnum<ActionType>(rawType)
                ?: return@mapIndexedNotNull loadIssue(source, "$path.actions[$index].type", "unknown action type '${rawType ?: "missing"}'").let { null }
            val value = raw["value"]?.toString() ?: ""
            if (actionType == ActionType.SET_REGION_STATUS && legacyCompletionRegion(value) != null) {
                ActionDefinition(ActionType.COMPLETE_REGION, legacyCompletionRegion(value)!!)
            } else {
                ActionDefinition(actionType, value)
            }
        }
        return ScriptDefinition(
            // New click events stay disabled until explicitly configured; legacy events retain their enabled-by-default behavior.
            enabled = if (type == RegionEventType.LEFT_CLICK || type == RegionEventType.RIGHT_CLICK) section.getBoolean("$path.enabled", false) else section.getBoolean("$path.enabled", true),
            cooldownSeconds = section.getLong("$path.cooldown-seconds", 0).coerceAtLeast(0),
            actions = actions,
            conditions = readConditions(section.getMapList("$path.conditions"), source, "$path.conditions"),
            rewards = readRewards(section.getMapList("$path.rewards"), source, "$path.rewards"),
            overrideParent = if (section.contains("$path.inherit")) !section.getBoolean("$path.inherit") else section.getBoolean("$path.override-parent", false),
            firstEntryOnly = when (mode) { "first" -> true; "repeat", "always" -> false; else -> section.getBoolean("$path.first-entry-only", false) },
            repeatEntryOnly = when (mode) { "repeat" -> true; "first", "always" -> false; else -> section.getBoolean("$path.repeat-entry-only", false) },
        )
    }

    private fun readConditions(raw: List<Map<*, *>>, source: String, path: String): List<ConditionDefinition> = raw.mapIndexedNotNull { index, item ->
        val rawType = item["type"]?.toString()
        val type = parseEnum<ConditionType>(rawType)
            ?: return@mapIndexedNotNull loadIssue(source, "$path[$index].type", "unknown condition type '${rawType ?: "missing"}'").let { null }
        val rawOperator = item["operator"]?.toString()
        val operator = parseEnum<ComparisonOperator>(rawOperator)
        if (!rawOperator.isNullOrBlank() && operator == null) loadIssue(source, "$path[$index].operator", "unknown operator '$rawOperator'")
        ConditionDefinition(
            type = type,
            key = item["key"]?.toString() ?: "",
            value = item["value"]?.toString() ?: "",
            operator = operator ?: ComparisonOperator.EQUALS,
            amount = item["amount"]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        )
    }

    private fun readRewards(raw: List<Map<*, *>>, source: String, path: String): List<RewardDefinition> = raw.mapIndexedNotNull { index, item ->
        val rawType = item["type"]?.toString()
        val type = parseEnum<RewardType>(rawType)
            ?: return@mapIndexedNotNull loadIssue(source, "$path[$index].type", "unknown reward type '${rawType ?: "missing"}'").let { null }
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

    private fun eventMode(script: ScriptDefinition): String = when {
        script.firstEntryOnly -> "first"
        script.repeatEntryOnly -> "repeat"
        else -> "always"
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?): T? = value?.trim()?.uppercase()?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun parseGlobalStatus(value: String?): GlobalRegionStatus? = GlobalRegionStatus.parse(value)

    private fun legacyCompletionRegion(value: String): String? {
        val parts = value.split(',', limit = 2)
        return parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
            ?.takeIf { parts.getOrNull(1)?.trim()?.equals("completed", true) == true }
    }

    private fun readPosition(section: ConfigurationSection, primaryKey: String, legacyKey: String, source: String): BlockPosition {
        val key = if (section.contains(primaryKey)) primaryKey else legacyKey
        listOf("x", "y", "z").forEach { coordinate ->
            if (!section.isInt("$key.$coordinate")) loadIssue(source, "$key.$coordinate", "must be an integer")
        }
        return BlockPosition(section.getInt("$key.x"), section.getInt("$key.y"), section.getInt("$key.z"))
    }

    private fun loadIssue(source: String, path: String, message: String) {
        loadIssues += "$source.$path: $message"
    }

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
            readRegion(value, id, "config.yml.regions.$id")?.let { save(it) }
        }
        if (legacy.getKeys(false).isNotEmpty()) {
            plugin.config.set("regions", null)
            plugin.saveConfig()
        }
    }

    private fun Location.toBlockPosition() = BlockPosition(blockX, blockY, blockZ)
}
