package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.api.RegionCoreService
import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.ComparisonOperator
import com.worldscript.foundation.model.ConditionMode
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
import com.worldscript.foundation.model.PolygonPoint
import com.worldscript.foundation.model.RegionShape
import com.worldscript.foundation.model.DiscoveryDefinition
import com.worldscript.foundation.model.ScriptDefinition
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

private data class SpatialKey(val world: String, val cellX: Int, val cellZ: Int)

class RegionCoreServiceImpl(private val plugin: JavaPlugin) : RegionCoreService {
    private val regions = linkedMapOf<String, RegionDefinition>()
    private var spatialIndex: Map<SpatialKey, List<RegionDefinition>>? = null
    private val loadIssues = mutableListOf<String>()
    private val regionDirectory = File(plugin.dataFolder, "regions")

    fun load() {
        regions.clear()
        spatialIndex = null
        loadIssues.clear()
        if (!regionDirectory.exists()) regionDirectory.mkdirs()
        migrateLegacyConfig()
        regionDirectory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                readRegion(YamlConfiguration.loadConfiguration(file), file.nameWithoutExtension, "regions/${file.name}")?.let { region ->
                    val key = region.id.lowercase()
                    if (regions.containsKey(key)) {
                        loadIssue("regions/${file.name}", "id", "duplicate region id '${region.id}'; file was ignored")
                    } else {
                        regions[key] = region
                    }
                }
            }
    }

    override fun find(id: String): RegionDefinition? = regions[id.trim().lowercase()]

    override fun effective(id: String): RegionDefinition? = resolve(id.trim().lowercase(), emptySet())

    override fun all(): Collection<RegionDefinition> = regions.values.toList()

    override fun regionsAt(location: Location): List<RegionDefinition> = regionsAt(location, ::isAccessible)

    fun regionsAt(location: Location, accessible: (String) -> Boolean): List<RegionDefinition> = indexedCandidates(location)
        .filter { region -> region.worldName == location.world?.name && RegionGeometry.contains(region, location.toBlockPosition()) }
        .filter { accessible(it.id) }
        .sortedWith(compareBy<RegionDefinition> { depth(it.id) }.thenBy { it.priority }.thenBy { it.id.lowercase() })

    private fun indexedCandidates(location: Location): List<RegionDefinition> {
        val world = location.world?.name ?: return emptyList()
        val key = SpatialKey(world, Math.floorDiv(location.blockX, INDEX_CELL_BLOCKS), Math.floorDiv(location.blockZ, INDEX_CELL_BLOCKS))
        return spatialIndexOrBuild()[key].orEmpty()
    }

    private fun spatialIndexOrBuild(): Map<SpatialKey, List<RegionDefinition>> {
        spatialIndex?.let { return it }
        val built = linkedMapOf<SpatialKey, MutableList<RegionDefinition>>()
        regions.values.forEach { region ->
            val minCellX = Math.floorDiv(region.bounds.min.x.toInt(), INDEX_CELL_BLOCKS)
            val maxCellX = Math.floorDiv(region.bounds.max.x.toInt(), INDEX_CELL_BLOCKS)
            val minCellZ = Math.floorDiv(region.bounds.min.z.toInt(), INDEX_CELL_BLOCKS)
            val maxCellZ = Math.floorDiv(region.bounds.max.z.toInt(), INDEX_CELL_BLOCKS)
            for (cellX in minCellX..maxCellX) for (cellZ in minCellZ..maxCellZ) {
                built.getOrPut(SpatialKey(region.worldName, cellX, cellZ), ::mutableListOf).add(region)
            }
        }
        return built.mapValues { it.value.toList() }.also { spatialIndex = it }
    }

    private fun invalidateSpatialIndex() { spatialIndex = null }

    override fun save(region: RegionDefinition) {
        if (!isValidId(region.id)) return
        invalidateSpatialIndex()
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
        when (val shape = region.shape) {
            RegionShape.Cuboid -> data.set("location.shape.type", "cuboid")
            is RegionShape.Polygon -> {
                data.set("location.shape.type", "polygon")
                data.set("location.shape.points", shape.points.map { mapOf("x" to it.x, "z" to it.z) })
                shape.cuboidBounds?.let { cuboidBounds ->
                    writePosition(data, "location.shape.cuboid-bounds.min", cuboidBounds.min)
                    writePosition(data, "location.shape.cuboid-bounds.max", cuboidBounds.max)
                }
            }
        }
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
        region.discovery?.canonicalized()?.let { discovery ->
            data.set("discovery.enabled", discovery.enabled)
            data.set("discovery.toast.enabled", discovery.toastEnabled)
            data.set("discovery.toast.title", discovery.toastTitle)
            data.set("discovery.toast.description", discovery.toastDescription)
            data.set("discovery.toast.icon", discovery.toastIcon)
            data.set("discovery.title.enabled", discovery.titleEnabled)
            data.set("discovery.title.text", discovery.title)
            data.set("discovery.title.subtitle", discovery.subtitle)
            data.set("discovery.title.fade-in", discovery.fadeIn)
            data.set("discovery.title.stay", discovery.stay)
            data.set("discovery.title.fade-out", discovery.fadeOut)
            data.set("discovery.sound.enabled", discovery.soundEnabled)
            data.set("discovery.sound.type", discovery.sound)
            data.set("discovery.sound.volume", discovery.volume)
            data.set("discovery.sound.pitch", discovery.pitch)
            data.set("discovery.reward.enabled", discovery.rewardEnabled)
            data.set("discovery.actions", discovery.actions.map(::actionMap))
            // The old reward list is read for compatibility only. Saving a
            // region completes the migration and removes that YAML path.
        }
        writePosition(data, "location.min", region.bounds.min)
        writePosition(data, "location.max", region.bounds.max)
        region.events.forEach { (type, script) ->
            val eventPath = "events.${type.name.lowercase()}"
            data.set("$eventPath.enabled", script.enabled)
            data.set("$eventPath.inherit", !script.overrideParent)
            data.set("$eventPath.cooldown-seconds", script.cooldownSeconds)
            data.set("$eventPath.mode", eventMode(script))
            data.set("$eventPath.actions", script.actions.map(::actionMap))
            data.set("$eventPath.conditions", script.conditions.map { conditionMap(it) })
            data.set("$eventPath.condition-failure-actions", script.conditionFailureActions.map(::actionMap))
            data.set("$eventPath.condition-mode", script.conditionMode.name.lowercase())
            data.set("$eventPath.conditions-enabled", script.conditionsEnabled)
            data.set("$eventPath.rewards", script.rewards.map { rewardMap(it) })
        }
        data.save(File(regionDirectory, "${region.id}.yml"))
    }

    override fun delete(id: String): Boolean {
        val removed = regions.remove(id.trim().lowercase()) ?: return false
        invalidateSpatialIndex()
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
        invalidateSpatialIndex()
        save(updated)
        return true
    }

    override fun setVariable(id: String, key: String, value: String): Boolean {
        val region = find(id) ?: return false
        if (key.isBlank()) return false
        val updated = region.copy(variables = region.variables + (key.trim() to value))
        regions[region.id.lowercase()] = updated
        invalidateSpatialIndex()
        save(updated)
        return true
    }

    fun removeVariable(id: String, key: String): Boolean {
        val region = find(id) ?: return false
        val existing = region.variables.keys.firstOrNull { it.equals(key.trim(), true) } ?: return false
        val updated = region.copy(variables = region.variables.filterKeys { !it.equals(existing, true) })
        regions[region.id.lowercase()] = updated
        invalidateSpatialIndex()
        save(updated)
        return true
    }

    fun variableSource(id: String, key: String): String? {
        var current = find(id)
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current.id.lowercase())) {
            if (current.variables.keys.any { it.equals(key, true) }) return current.id
            current = if (current.inheritParent) current.parentId?.let(::find) else null
        }
        return null
    }

    fun isValidRegionId(id: String): Boolean = isValidId(id.trim())

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

    fun updatePolygon(id: String, points: List<PolygonPoint>): Boolean {
        val region = find(id) ?: return false
        val bounds = RegionGeometry.polygonBounds(points, region.bounds.min.y, region.bounds.max.y) ?: return false
        val cuboidBounds = (region.shape as? RegionShape.Polygon)?.cuboidBounds ?: region.bounds
        val updated = region.copy(bounds = bounds, shape = RegionShape.Polygon(points.toList(), cuboidBounds))
        regions[region.id.lowercase()] = updated
        save(updated)
        return true
    }

    fun resetPolygon(id: String): Boolean {
        val region = find(id) ?: return false
        val shape = region.shape as? RegionShape.Polygon ?: return false
        val updated = region.copy(bounds = shape.cuboidBounds ?: region.bounds, shape = RegionShape.Cuboid)
        regions[region.id.lowercase()] = updated
        save(updated)
        return true
    }

    fun updateDiscovery(id: String, update: (DiscoveryDefinition) -> DiscoveryDefinition): Boolean {
        val region = find(id) ?: return false
        // Edit against the effective definition so a child region does not
        // accidentally replace inherited discovery settings with defaults.
        val base = region.discovery ?: effective(id)?.discovery ?: DiscoveryDefinition()
        val updated = region.copy(discovery = update(base.canonicalized()).canonicalized())
        regions[region.id.lowercase()] = updated
        save(updated)
        return true
    }

    fun toggleEvent(id: String, type: RegionEventType): Boolean {
        val region = find(id) ?: return false
        val current = effective(id)?.events?.get(type) ?: region.events[type] ?: ScriptDefinition()
        val updated = region.copy(events = region.events + (type to current.copy(enabled = !current.enabled, overrideParent = true)))
        regions[id.lowercase()] = updated
        save(updated)
        return updated.events[type]?.enabled == true
    }

    override fun updateEvent(id: String, type: RegionEventType, update: (ScriptDefinition) -> ScriptDefinition): Boolean {
        val region = find(id) ?: return false
        val current = effective(id)?.events?.get(type) ?: region.events[type] ?: ScriptDefinition()
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
        return RegionConfigurationValidator(::find, ::hasParentCycle).validate(all(), loadIssues)
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

    fun variable(id: String, key: String): String? =
        effective(id)?.variables?.entries?.firstOrNull { it.key.equals(key.trim(), true) }?.value

    fun setDisplayName(id: String, displayName: String): Boolean {
        val region = find(id) ?: return false
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return false
        regions[region.id.lowercase()] = region.copy(displayName = cleanName)
        save(regions[region.id.lowercase()]!!)
        return true
    }

    /** Builds the configured display-name hierarchy from the outermost parent to this region. */
    fun displayPath(id: String): String {
        val chain = mutableListOf<RegionDefinition>()
        var current = find(id)
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current.id.lowercase())) {
            chain += current
            current = current.parentId?.let(::find)
        }
        return chain.asReversed().joinToString(" / ") { it.displayName.ifBlank { it.id } }
    }

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
                .filter { it.worldName == firstWorld.name && RegionGeometry.encloses(it.bounds, bounds) }
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
            discovery = region.discovery ?: parent.discovery,
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
        val bounds = RegionGeometry.from(readPosition(section, "location.min", "min", source), readPosition(section, "location.max", "max", source))
        val shape = readShape(section, bounds, source)
        val effectiveBounds = (shape as? RegionShape.Polygon)
            ?.let { RegionGeometry.polygonBounds(it.points, bounds.min.y, bounds.max.y) }
            ?: bounds
        return RegionDefinition(
            id = id,
            displayName = section.getString("identity.name") ?: section.getString("display-name", id) ?: id,
            worldId = section.getString("location.world-id") ?: section.getString("world-id", worldName) ?: worldName,
            worldName = worldName,
            bounds = effectiveBounds,
            shape = shape,
            role = role ?: RegionRole.OPEN_ZONE,
            contentId = section.getString("identity.content-id") ?: section.getString("content-id", "") ?: "",
            priority = if (section.contains("location.priority")) section.getInt("location.priority") else section.getInt("priority", 0),
            events = RegionEventType.entries.associateWith { type -> readScript(section, type, source) },
            parentId = parentId,
            inheritParent = if (section.contains("state.inherit")) section.getBoolean("state.inherit") else section.getBoolean("inherit-parent", true),
            variables = section.getConfigurationSection("variables")?.getKeys(false)?.associateWith { key -> section.getString("variables.$key", "") ?: "" } ?: emptyMap(),
            statuses = statuses,
            particle = readParticle(section, source),
            discovery = readDiscovery(section, source),
        )
    }

    private fun readShape(section: ConfigurationSection, bounds: RegionBounds, source: String): RegionShape {
        val type = section.getString("location.shape.type", "cuboid")?.trim()?.lowercase() ?: "cuboid"
        if (type == "cuboid") return RegionShape.Cuboid
        if (type != "polygon") {
            loadIssue(source, "location.shape.type", "unknown shape '$type'; using cuboid")
            return RegionShape.Cuboid
        }
        val points = section.getMapList("location.shape.points").mapIndexedNotNull { index, raw ->
            val x = (raw["x"] as? Number)?.toInt() ?: raw["x"]?.toString()?.toIntOrNull()
            val z = (raw["z"] as? Number)?.toInt() ?: raw["z"]?.toString()?.toIntOrNull()
            if (x == null || z == null) {
                loadIssue(source, "location.shape.points[$index]", "x and z must be integers")
                null
            } else PolygonPoint(x, z)
        }
        if (!RegionGeometry.isValidPolygon(points)) {
            loadIssue(source, "location.shape.points", "polygon needs at least three distinct, non-collinear points; using cuboid")
            return RegionShape.Cuboid
        }
        val calculated = RegionGeometry.polygonBounds(points, bounds.min.y, bounds.max.y)
        if (calculated != bounds) {
            loadIssue(source, "location.shape.points", "polygon bounds differ from location min/max; calculated bounds are used for detection")
        }
        return RegionShape.Polygon(points, readPolygonCuboidBounds(section, bounds))
    }

    private fun readPolygonCuboidBounds(section: ConfigurationSection, fallback: RegionBounds): RegionBounds {
        val root = "location.shape.cuboid-bounds"
        if (!section.isConfigurationSection(root)) return fallback
        fun point(path: String): BlockPosition? {
            val x = section.get("$path.x") as? Number
            val y = section.get("$path.y") as? Number
            val z = section.get("$path.z") as? Number
            return if (x == null || y == null || z == null) null else BlockPosition(x.toInt(), y.toInt(), z.toInt())
        }
        val min = point("$root.min") ?: return fallback
        val max = point("$root.max") ?: return fallback
        return RegionGeometry.from(min, max)
    }

    private fun readDiscovery(section: ConfigurationSection, source: String): DiscoveryDefinition? {
        if (!section.isConfigurationSection("discovery")) return null
        val sound = section.getString("discovery.sound.type", "ENTITY_PLAYER_LEVELUP") ?: "ENTITY_PLAYER_LEVELUP"
        if (section.getBoolean("discovery.sound.enabled", false) && BukkitCompatibility.resolveSound(sound) == null) {
            loadIssue(source, "discovery.sound.type", "unknown sound '$sound'")
        }
        fun readActions(path: String) = section.getMapList(path).mapIndexedNotNull { index, raw ->
            val rawType = raw["type"]?.toString() ?: raw["preset"]?.toString()
            val preset = raw["preset"]?.toString()?.trim()?.lowercase()
            val actionType = parseActionType(rawType, preset)
                ?: return@mapIndexedNotNull loadIssue(source, "$path[$index].type", "unknown action type '${rawType ?: "missing"}'").let { null }
            val value = raw["value"]?.toString() ?: ""
            val parameters = raw.entries.filter { it.key.toString() !in setOf("type", "preset", "value") }.associate { it.key.toString() to (it.value?.toString() ?: "") }
            ActionDefinition(actionType, value, parameters, preset)
        }
        val legacyActions = readActions("discovery.reward.actions")
        val actions = readActions("discovery.actions")
        return DiscoveryDefinition(
            enabled = section.getBoolean("discovery.enabled", false),
            toastEnabled = section.getBoolean("discovery.toast.enabled", true),
            toastTitle = section.getString("discovery.toast.title", "") ?: "",
            toastDescription = section.getString("discovery.toast.description", "") ?: "",
            toastIcon = section.getString("discovery.toast.icon", "") ?: "",
            titleEnabled = section.getBoolean("discovery.title.enabled", false),
            title = section.getString("discovery.title.text", "") ?: "",
            subtitle = section.getString("discovery.title.subtitle", "") ?: "",
            fadeIn = section.getInt("discovery.title.fade-in", 10).coerceAtLeast(0),
            stay = section.getInt("discovery.title.stay", 50).coerceAtLeast(0),
            fadeOut = section.getInt("discovery.title.fade-out", 10).coerceAtLeast(0),
            soundEnabled = section.getBoolean("discovery.sound.enabled", false),
            sound = sound,
            volume = section.getDouble("discovery.sound.volume", 1.0).toFloat().coerceAtLeast(0f),
            pitch = section.getDouble("discovery.sound.pitch", 1.0).toFloat().coerceAtLeast(0f),
            rewardEnabled = section.getBoolean("discovery.reward.enabled", false),
            actions = actions,
            rewardActions = legacyActions,
        )
    }

    private fun readParticle(section: ConfigurationSection, source: String): RegionParticleDefinition? {
        if (!section.isConfigurationSection("particle")) return null
        val enabled = section.getBoolean("particle.enabled", true)
        val type = section.getString("particle.type", "END_ROD") ?: "END_ROD"
        if (enabled && BukkitCompatibility.resolveParticle(type) == null) {
            loadIssue(source, "particle.type", "unknown particle '$type'")
            return null
        }
        return RegionParticleDefinition(
            enabled = enabled,
            preset = section.getString("particle.preset", "AMBIENT")?.uppercase()?.let { preset ->
                if (preset in PARTICLE_PRESETS) preset else {
                    loadIssue(source, "particle.preset", "unknown preset '$preset'; using AMBIENT")
                    "AMBIENT"
                }
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
        const val INDEX_CELL_BLOCKS = 128
        val PARTICLE_PRESETS = setOf("AMBIENT", "BORDER", "PORTAL", "ENTRANCE", "WARNING")
    }

    private fun readScript(section: ConfigurationSection, type: RegionEventType, source: String): ScriptDefinition {
        val path = "events.${type.name.lowercase()}"
        val mode = section.getString("$path.mode")?.trim()?.lowercase()
        val actions = section.getMapList("$path.actions").mapIndexedNotNull { index, raw ->
            val rawType = raw["type"]?.toString() ?: raw["preset"]?.toString()
            val preset = raw["preset"]?.toString()?.trim()?.lowercase()
            val actionType = parseActionType(rawType, preset)
                ?: return@mapIndexedNotNull loadIssue(source, "$path.actions[$index].type", "unknown action type '${rawType ?: "missing"}'").let { null }
            val value = raw["value"]?.toString() ?: ""
            val parameters = raw.entries
                .filter { it.key.toString() !in setOf("type", "preset", "value") }
                .associate { it.key.toString() to (it.value?.toString() ?: "") }
            val cleanedParameters = cleanPresetExamples(preset, parameters)
            if (actionType == ActionType.SET_REGION_STATUS && legacyCompletionRegion(value) != null) {
                ActionDefinition(ActionType.COMPLETE_REGION, legacyCompletionRegion(value)!!)
            } else {
                ActionDefinition(actionType, value, cleanedParameters, preset)
            }
        }
        val failureActions = section.getMapList("$path.condition-failure-actions").mapIndexedNotNull { index, raw ->
            val rawType = raw["type"]?.toString() ?: raw["preset"]?.toString()
            val actionType = parseActionType(rawType, raw["preset"]?.toString()?.trim()?.lowercase())
                ?: return@mapIndexedNotNull loadIssue(source, "$path.condition-failure-actions[$index].type", "unknown action type '${rawType ?: "missing"}'").let { null }
            val value = raw["value"]?.toString() ?: ""
            val parameters = raw.entries
                .filter { it.key.toString() !in setOf("type", "preset", "value") }
                .associate { it.key.toString() to (it.value?.toString() ?: "") }
            ActionDefinition(actionType, value, parameters, raw["preset"]?.toString())
        }
        return ScriptDefinition(
            // New click events stay disabled until explicitly configured; legacy events retain their enabled-by-default behavior.
            enabled = if (type == RegionEventType.LEFT_CLICK || type == RegionEventType.RIGHT_CLICK) section.getBoolean("$path.enabled", false) else section.getBoolean("$path.enabled", true),
            cooldownSeconds = section.getLong("$path.cooldown-seconds", 0).coerceAtLeast(0),
            actions = actions,
            conditions = readConditions(section.getMapList("$path.conditions"), source, "$path.conditions"),
            conditionFailureActions = failureActions,
            conditionMode = section.getString("$path.condition-mode", "and")?.uppercase()?.let { runCatching { ConditionMode.valueOf(it) }.getOrNull() } ?: ConditionMode.AND,
            conditionsEnabled = section.getBoolean("$path.conditions-enabled", true),
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

    private fun actionMap(action: ActionDefinition): Map<String, Any> = linkedMapOf<String, Any>().apply {
        if (action.preset != null) put("preset", action.preset)
        else put("type", ActionType.yamlName(action.type))
        if (action.value.isNotBlank()) put("value", action.value)
        putAll(action.parameters)
    }

    private fun cleanPresetExamples(preset: String?, parameters: Map<String, String>): Map<String, String> {
        if (preset == "text-display" || preset == "title") {
            return parameters.mapValues { (key, value) ->
                if ((key == "title" && value == "&b区域标题") ||
                    (key == "subtitle" && value == "&f区域副标题")) "" else value
            }
        }
        if (preset == "message" && parameters["text"] == "&7区域消息") {
            return parameters + ("text" to "")
        }
        return parameters
    }

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

    private fun parseActionType(rawType: String?, preset: String?): ActionType? = when (preset) {
        "title", "text-display" -> ActionType.TEXT_DISPLAY
        "sound" -> ActionType.SOUND
        "message" -> ActionType.MESSAGE
        "player-command" -> ActionType.PLAYER_COMMAND
        "console-command" -> ActionType.CONSOLE_COMMAND
        "teleport" -> ActionType.TELEPORT
        "set-variable" -> ActionType.SET_VARIABLE
        "set-region-status" -> ActionType.SET_REGION_STATUS
        "give-item" -> ActionType.GIVE_ITEM
        "give-experience" -> ActionType.GIVE_EXPERIENCE
        "give-money" -> ActionType.GIVE_MONEY
        "unlock-region" -> ActionType.UNLOCK_REGION
        "complete-region" -> ActionType.COMPLETE_REGION
        else -> ActionType.parseYaml(rawType)
    }

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
