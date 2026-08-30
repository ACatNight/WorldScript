@file:Suppress("DEPRECATION")

package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.TextFormatter
import com.worldscript.foundation.model.PolygonPoint
import com.worldscript.foundation.model.RegionShape
import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot

class PolygonEditingService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val editorTools: EditorToolService,
) {
    private data class Session(
        val regionId: String,
        val points: MutableList<PolygonPoint>,
        val toolMarker: String,
        val undoHistory: MutableList<List<PolygonPoint>> = mutableListOf(),
        val redoHistory: MutableList<List<PolygonPoint>> = mutableListOf(),
        var selectedIndex: Int? = null,
    )

    private val sessions = mutableMapOf<UUID, Session>()
    private val previewTasks = mutableMapOf<UUID, BukkitTask>()
    private val draftWrites = mutableMapOf<UUID, BukkitTask>()
    private val draftFile by lazy { File(plugin.dataFolder, "data/polygon-drafts.yml") }
    private val drafts by lazy {
        val source = if (draftFile.isFile) draftFile else File(plugin.dataFolder, "polygon-drafts.yml")
        YamlConfiguration.loadConfiguration(source)
    }
    private val lang = Lang(plugin)

    fun start(player: Player, regionId: String): Boolean {
        sessions[player.uniqueId]?.let { active ->
            lang.send(player, "polygon-session-active", "region" to active.regionId)
            return false
        }
        val region = regions.find(regionId) ?: return false
        val draft = loadDraft(player.uniqueId, region.id)
        val points = draft ?: when (val shape = region.shape) {
            is RegionShape.Polygon -> shape.points.toMutableList()
            RegionShape.Cuboid -> mutableListOf(
                PolygonPoint(region.bounds.min.x.toInt(), region.bounds.min.z.toInt()),
                PolygonPoint(region.bounds.max.x.toInt(), region.bounds.min.z.toInt()),
                PolygonPoint(region.bounds.max.x.toInt(), region.bounds.max.z.toInt()),
                PolygonPoint(region.bounds.min.x.toInt(), region.bounds.max.z.toInt()),
            )
        }
        val session = Session(region.id, points, TOOL_MARKER_PREFIX + UUID.randomUUID())
        if (player.inventory.addItem(tool(region.id, points.size, session.toolMarker)).isNotEmpty()) {
            lang.send(player, "polygon-tool-inventory-full")
            return false
        }
        sessions[player.uniqueId] = session
        if (draft != null) lang.send(player, "polygon-draft-restored", "region" to region.id, "count" to points.size)
        lang.send(player, "polygon-started", "region" to region.id, "count" to points.size)
        lang.send(player, "polygon-free-edit-hint")
        restartPreview(player)
        return true
    }

    fun isEditing(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    fun activeRegion(player: Player): String? = sessions[player.uniqueId]?.regionId

    fun activeSelectedPoint(player: Player): Int? = sessions[player.uniqueId]?.selectedIndex

    fun nearestPoint(player: Player, x: Int, z: Int): Int? {
        val session = sessions[player.uniqueId] ?: return null
        val radius = plugin.config.getDouble("selection.polygon.point-select-radius", 1.0).coerceIn(0.25, 8.0)
        val radiusSquared = radius * radius
        return session.points.withIndex()
            .map { indexed -> indexed to ((indexed.value.x - x).toDouble() * (indexed.value.x - x) + (indexed.value.z - z).toDouble() * (indexed.value.z - z)) }
            .filter { it.second <= radiusSquared }
            .minByOrNull { it.second }?.first?.index
    }

    fun selectPoint(player: Player, index: Int): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        if (index !in session.points.indices) return false
        session.selectedIndex = index
        lang.send(player, "polygon-point-selected", "index" to index + 1, "x" to session.points[index].x, "z" to session.points[index].z)
        restartPreview(player)
        return true
    }

    fun moveSelected(player: Player, x: Int, z: Int): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        val index = session.selectedIndex ?: return false
        if (index !in session.points.indices) return false
        val point = PolygonPoint(x, z)
        if (session.points.withIndex().any { it.index != index && it.value == point }) return false
        remember(session)
        session.points[index] = point
        saveDraft(player.uniqueId, session)
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-moved-to", "index" to index + 1, "x" to x, "z" to z)
        sendShapeStatus(player, session)
        restartPreview(player)
        return true
    }

    fun deletePoint(player: Player, index: Int): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        if (index !in session.points.indices) return false
        remember(session)
        val removed = session.points.removeAt(index)
        session.selectedIndex = if (session.selectedIndex == index) null else session.selectedIndex?.let { if (it > index) it - 1 else it }
        saveDraft(player.uniqueId, session)
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-removed", "index" to index + 1, "x" to removed.x, "z" to removed.z, "count" to session.points.size)
        sendShapeStatus(player, session)
        restartPreview(player)
        return true
    }

    fun isEditingTool(player: Player, item: ItemStack?): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        return item?.type == editorTools.polygonTool() && item.itemMeta?.localizedName == session.toolMarker
    }

    fun addPoint(player: Player, x: Int, z: Int) {
        val session = sessions[player.uniqueId] ?: return
        val region = regions.find(session.regionId) ?: return cancelMissingRegion(player)
        if (player.world.name != region.worldName) {
            lang.send(player, "polygon-wrong-world", "world" to region.worldName)
            return
        }
        val point = PolygonPoint(x, z)
        if (point in session.points) {
            lang.send(player, "polygon-duplicate-point", "x" to x, "z" to z)
            return
        }
        val maximum = plugin.config.getInt("selection.polygon.max-points", DEFAULT_MAX_POINTS).coerceIn(3, MAX_POINTS_LIMIT)
        if (session.points.size >= maximum) {
            lang.send(player, "polygon-point-limit", "limit" to maximum)
            return
        }
        remember(session)
        session.points += point
        saveDraft(player.uniqueId, session)
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-added", "count" to session.points.size, "x" to x, "z" to z)
        sendShapeStatus(player, session)
        restartPreview(player)
    }

    fun undo(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val previous = session.undoHistory.removeLastOrNull()
        if (previous == null) {
            lang.send(player, "polygon-undo-empty")
            return
        }
        session.redoHistory += session.points.toList()
        session.points.clear()
        session.points += previous
        session.selectedIndex = session.selectedIndex?.takeIf { it in session.points.indices }
        refreshHeldTool(player, session)
        saveDraft(player.uniqueId, session)
        lang.send(player, "polygon-undone", "count" to session.points.size)
        sendShapeStatus(player, session)
        restartPreview(player)
    }

    fun redo(player: Player): Boolean {
        val session = sessions[player.uniqueId] ?: return missingSession(player)
        val next = session.redoHistory.removeLastOrNull()
        if (next == null) {
            lang.send(player, "polygon-redo-empty")
            return false
        }
        session.undoHistory += session.points.toList()
        session.points.clear()
        session.points += next
        session.selectedIndex = session.selectedIndex?.takeIf { it in session.points.indices }
        refreshHeldTool(player, session)
        saveDraft(player.uniqueId, session)
        lang.send(player, "polygon-redone", "count" to session.points.size)
        sendShapeStatus(player, session)
        restartPreview(player)
        return true
    }

    fun finish(player: Player): Boolean {
        val session = sessions[player.uniqueId] ?: run {
            lang.send(player, "polygon-not-editing")
            return false
        }
        if (RegionGeometry.isSelfIntersecting(session.points)) {
            lang.send(player, "polygon-self-intersection")
            return false
        }
        if (!RegionGeometry.isValidPolygon(session.points)) {
            lang.send(player, "polygon-invalid", "count" to session.points.distinct().size)
            showInsufficientPoints(player)
            return false
        }
        if (!regions.updatePolygon(session.regionId, session.points)) {
            lang.send(player, "polygon-save-failed", "region" to session.regionId)
            return false
        }
        sessions.remove(player.uniqueId)
        previewTasks.remove(player.uniqueId)?.cancel()
        clearDraft(player.uniqueId)
        removeTools(player, session.toolMarker)
        lang.send(player, "polygon-saved", "region" to session.regionId, "count" to session.points.size)
        return true
    }

    fun cancel(player: Player, notify: Boolean = true): Boolean {
        val removed = sessions.remove(player.uniqueId) ?: return false
        previewTasks.remove(player.uniqueId)?.cancel()
        clearDraft(player.uniqueId)
        removeTools(player, removed.toolMarker)
        if (notify) lang.send(player, "polygon-cancelled", "region" to removed.regionId)
        return true
    }

    fun status(player: Player) {
        val session = sessions[player.uniqueId]
        if (session == null) lang.send(player, "polygon-not-editing")
        else {
            lang.send(player, "polygon-status", "region" to session.regionId, "count" to session.points.size)
            session.points.forEachIndexed { index, point ->
                lang.send(player, "polygon-point-status", "index" to index + 1, "x" to point.x, "z" to point.z)
            }
        }
    }

    fun removePoint(player: Player, number: Int): Boolean {
        val session = sessions[player.uniqueId] ?: return missingSession(player)
        val index = number - 1
        if (index !in session.points.indices) return invalidIndex(player, session)
        remember(session)
        val removed = session.points.removeAt(index)
        refreshHeldTool(player, session)
        saveDraft(player.uniqueId, session)
        lang.send(player, "polygon-point-removed", "index" to number, "x" to removed.x, "z" to removed.z, "count" to session.points.size)
        restartPreview(player)
        return true
    }

    fun movePoint(player: Player, fromNumber: Int, toNumber: Int): Boolean {
        val session = sessions[player.uniqueId] ?: return missingSession(player)
        val from = fromNumber - 1
        val to = toNumber - 1
        if (from !in session.points.indices || to !in session.points.indices) return invalidIndex(player, session)
        remember(session)
        val point = session.points.removeAt(from)
        session.points.add(to, point)
        refreshHeldTool(player, session)
        saveDraft(player.uniqueId, session)
        lang.send(player, "polygon-point-moved", "from" to fromNumber, "to" to toNumber)
        restartPreview(player)
        return true
    }

    fun preview(player: Player, notify: Boolean = true): Boolean {
        val shown = drawPreview(player, notify)
        if (shown) restartPreview(player)
        return shown
    }

    private fun drawPreview(player: Player, notify: Boolean = true): Boolean {
        val session = sessions[player.uniqueId] ?: run {
            if (notify) lang.send(player, "polygon-not-editing")
            return false
        }
        if (session.points.isEmpty()) {
            if (notify) lang.send(player, "polygon-no-points")
            return false
        }
        val particleName = if (RegionGeometry.isSelfIntersecting(session.points)) {
            plugin.config.getString("selection.polygon.preview.invalid-particle", DEFAULT_INVALID_PARTICLE) ?: DEFAULT_INVALID_PARTICLE
        } else plugin.config.getString("selection.polygon.preview.particle", DEFAULT_PARTICLE) ?: DEFAULT_PARTICLE
        val particle = BukkitCompatibility.resolveParticle(particleName)
        if (particle == null) {
            lang.send(player, "polygon-preview-unavailable", "particle" to particleName)
            return false
        }
        val spacing = plugin.config.getDouble("selection.polygon.preview.spacing", DEFAULT_SPACING).coerceIn(0.1, 4.0)
        val maxParticles = plugin.config.getInt("selection.polygon.preview.max-particles", DEFAULT_MAX_PARTICLES).coerceIn(8, 4096)
        val count = plugin.config.getInt("selection.polygon.preview.count", 1).coerceIn(1, 16)
        val offset = plugin.config.getDouble("selection.polygon.preview.y-offset", 0.15).coerceIn(-4.0, 4.0)
        val speed = plugin.config.getDouble("selection.polygon.preview.speed", 0.0).coerceIn(0.0, 4.0)
        val regionY = regions.find(session.regionId)?.bounds?.min?.y?.toDouble()
        val y = (regionY ?: player.location.y) + 1.0 + offset
        val vertexParticle = BukkitCompatibility.resolveParticle(plugin.config.getString("selection.polygon.preview.vertex-particle", particleName) ?: particleName)
        val selectedParticle = BukkitCompatibility.resolveParticle(plugin.config.getString("selection.polygon.preview.selected-particle", "GLOW") ?: "GLOW")
        session.points.forEachIndexed { index, point ->
            val effect = if (session.selectedIndex == index) selectedParticle ?: vertexParticle else vertexParticle
            effect?.let { player.world.spawnParticle(it, point.x + 0.5, y, point.z + 0.5, count + if (session.selectedIndex == index) 2 else 0, 0.0, 0.0, 0.0, speed) }
        }
        var remaining = maxParticles
        val segments = session.points.zipWithNext().toMutableList()
        if (session.points.size >= 3) segments.add(session.points.last() to session.points.first())
        if (segments.isEmpty()) {
            val point = session.points.first()
            player.world.spawnParticle(particle, point.x + 0.5, y, point.z + 0.5, count, 0.0, 0.0, 0.0, speed)
        } else segments.forEach { (first, second) ->
            if (remaining <= 0) return@forEach
            val distance = hypot((second.x - first.x).toDouble(), (second.z - first.z).toDouble())
            val samples = ceil(distance / spacing).toInt().coerceAtLeast(1).coerceAtMost(remaining)
            for (index in 0..samples) {
                if (remaining-- <= 0) break
                val progress = index.toDouble() / samples
                val x = first.x + (second.x - first.x) * progress + 0.5
                val z = first.z + (second.z - first.z) * progress + 0.5
                player.world.spawnParticle(particle, x, y, z, count, 0.0, 0.0, 0.0, speed)
            }
        }
        if (notify) lang.send(player, "polygon-preview-shown", "count" to session.points.size)
        return true
    }

    fun reset(player: Player, regionId: String): Boolean {
        val region = regions.find(regionId) ?: return false
        if (!regions.resetPolygon(region.id)) {
            lang.send(player, "polygon-already-cuboid", "region" to region.id)
            return false
        }
        sessions[player.uniqueId]?.takeIf { it.regionId.equals(region.id, true) }?.let { session ->
            sessions.remove(player.uniqueId)
            previewTasks.remove(player.uniqueId)?.cancel()
            removeTools(player, session.toolMarker)
        }
        clearDraftsForRegion(region.id)
        lang.send(player, "polygon-reset", "region" to region.id)
        return true
    }

    /** Keep unfinished work through reloads; only the temporary editing tools are discarded. */
    fun clear() {
        draftWrites.values.forEach { it.cancel() }
        draftWrites.clear()
        saveDraftsNow()
        previewTasks.values.forEach { it.cancel() }
        previewTasks.clear()
        sessions.forEach { (playerId, session) ->
            plugin.server.getPlayer(playerId)?.let { removeTools(it, session.toolMarker) }
        }
        sessions.clear()
    }

    fun close() {
        clear()
    }

    fun disconnect(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        previewTasks.remove(player.uniqueId)?.cancel()
        saveDraft(player.uniqueId, session)
        removeTools(player, session.toolMarker)
    }

    private fun saveDraft(playerId: UUID, session: Session) {
        if (!plugin.config.getBoolean("selection.polygon.drafts.enabled", true)) return
        val path = "drafts.$playerId"
        drafts.set("$path.region", session.regionId)
        drafts.set("$path.points", session.points.map { mapOf("x" to it.x, "z" to it.z) })
        draftWrites.remove(playerId)?.cancel()
        val delay = plugin.config.getLong("selection.polygon.drafts.save-delay-ticks", 20L).coerceAtLeast(1L)
        draftWrites[playerId] = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            draftWrites.remove(playerId)
            saveDraftsNow()
        }, delay)
    }

    private fun loadDraft(playerId: UUID, regionId: String): MutableList<PolygonPoint>? {
        if (!plugin.config.getBoolean("selection.polygon.drafts.enabled", true)) return null
        val path = "drafts.$playerId"
        if (!drafts.getString("$path.region").equals(regionId, true)) return null
        val points = drafts.getMapList("$path.points").mapNotNull { point ->
            val x = (point["x"] as? Number)?.toInt()
            val z = (point["z"] as? Number)?.toInt()
            if (x == null || z == null) null else PolygonPoint(x, z)
        }.distinct().toMutableList()
        // An existing empty draft is meaningful: it represents an intentional
        // undo-to-empty state and must not resurrect the saved polygon.
        return if (drafts.contains(path)) points else null
    }

    private fun clearDraft(playerId: UUID) {
        draftWrites.remove(playerId)?.cancel()
        drafts.set("drafts.$playerId", null)
        saveDraftsNow()
    }

    private fun clearDraftsForRegion(regionId: String) {
        drafts.getConfigurationSection("drafts")?.getKeys(false)
            ?.filter { drafts.getString("drafts.$it.region").equals(regionId, true) }
            ?.forEach { playerId ->
                draftWrites.remove(runCatching { UUID.fromString(playerId) }.getOrNull())?.cancel()
                drafts.set("drafts.$playerId", null)
            }
        saveDraftsNow()
    }

    private fun saveDraftsNow() {
        runCatching {
            draftFile.parentFile?.mkdirs()
            drafts.save(draftFile)
        }.onFailure { plugin.logger.warning("Could not save polygon drafts: ${it.message}") }
    }

    private fun restartPreview(player: Player) {
        previewTasks.remove(player.uniqueId)?.cancel()
        val duration = plugin.config.getLong("selection.polygon.preview.duration-ticks", 100L).coerceAtLeast(1L)
        val interval = plugin.config.getLong("selection.polygon.preview.interval-ticks", 10L).coerceAtLeast(1L)
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { drawPreview(player, notify = false) }, 0L, interval)
        previewTasks[player.uniqueId] = task
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (previewTasks[player.uniqueId] === task) {
                task.cancel()
                previewTasks.remove(player.uniqueId)
            }
        }, duration)
    }

    private fun sendShapeStatus(player: Player, session: Session) {
        when {
            session.points.size < 3 -> lang.send(player, "polygon-status-insufficient", "count" to session.points.size)
            RegionGeometry.isSelfIntersecting(session.points) -> lang.send(player, "polygon-status-invalid")
            RegionGeometry.isValidPolygon(session.points) -> lang.send(player, "polygon-status-valid")
        }
    }

    private fun showInsufficientPoints(player: Player) {
        if (!plugin.config.getBoolean("selection.polygon.insufficient-points-feedback.enabled", true)) return
        val name = plugin.config.getString("selection.polygon.insufficient-points-feedback.particle", "REDSTONE") ?: return
        val particle = BukkitCompatibility.resolveParticle(name) ?: return
        val count = plugin.config.getInt("selection.polygon.insufficient-points-feedback.count", 6).coerceAtLeast(0)
        val offset = plugin.config.getDouble("selection.polygon.insufficient-points-feedback.offset", 0.25).coerceAtLeast(0.0)
        val height = plugin.config.getDouble("selection.polygon.insufficient-points-feedback.height", 0.7)
        val speed = plugin.config.getDouble("selection.polygon.insufficient-points-feedback.speed", 0.0).coerceAtLeast(0.0)
        runCatching { player.spawnParticle(particle, player.location.clone().add(0.0, height, 0.0), count, offset, offset, offset, speed) }
            .onFailure { plugin.logger.warning("Could not show polygon validation particle: ${it.message}") }
    }

    private fun tool(regionId: String, count: Int, marker: String): ItemStack = ItemStack(editorTools.polygonTool()).apply {
        val meta = itemMeta ?: return@apply
        meta.setLocalizedName(marker)
        meta.setDisplayName(color(lang.text("polygon-tool-name", "polygon-tool-name", "region" to regionId)))
        meta.lore = listOf(
            lang.text("polygon-tool-lore-region", "polygon-tool-lore-region", "region" to regionId),
            lang.text("polygon-tool-lore-count", "polygon-tool-lore-count", "count" to count),
            lang.text("polygon-tool-lore-add"),
            lang.text("polygon-tool-lore-undo"),
            lang.text("polygon-tool-lore-redo"),
            lang.text("polygon-tool-lore-save"),
            lang.text("polygon-tool-lore-cancel"),
        ).map(::color)
        itemMeta = meta
    }

    private fun refreshHeldTool(player: Player, session: Session) {
        if (isEditingTool(player, player.inventory.itemInMainHand)) {
            player.inventory.setItemInMainHand(tool(session.regionId, session.points.size, session.toolMarker))
        }
        if (isEditingTool(player, player.inventory.itemInOffHand)) {
            player.inventory.setItemInOffHand(tool(session.regionId, session.points.size, session.toolMarker))
        }
    }

    private fun cancelMissingRegion(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        removeTools(player, session.toolMarker)
        lang.send(player, "region-not-found", "region" to session.regionId)
    }

    private fun removeTools(player: Player, marker: String) {
        player.inventory.contents.forEachIndexed { slot, item ->
            if (item?.itemMeta?.localizedName == marker) player.inventory.setItem(slot, null)
        }
        if (player.inventory.itemInOffHand.itemMeta?.localizedName == marker) {
            player.inventory.setItemInOffHand(null)
        }
    }

    private fun missingSession(player: Player): Boolean {
        lang.send(player, "polygon-not-editing")
        return false
    }

    private fun invalidIndex(player: Player, session: Session): Boolean {
        lang.send(player, "polygon-point-index-invalid", "count" to session.points.size)
        return false
    }

    private fun color(value: String): String = TextFormatter.color(value)

    private fun remember(session: Session) {
        val maximum = plugin.config.getInt("selection.polygon.history-limit", 32).coerceIn(1, 256)
        session.undoHistory += session.points.toList()
        while (session.undoHistory.size > maximum) session.undoHistory.removeAt(0)
        session.redoHistory.clear()
    }

    private companion object {
        const val DEFAULT_MAX_POINTS = 128
        const val MAX_POINTS_LIMIT = 512
        const val DEFAULT_PARTICLE = "END_ROD"
        const val DEFAULT_SPACING = 0.75
        const val DEFAULT_MAX_PARTICLES = 512
        const val DEFAULT_INVALID_PARTICLE = "REDSTONE"
        const val TOOL_MARKER_PREFIX = "worldscript:polygon-editor:"
    }
}
