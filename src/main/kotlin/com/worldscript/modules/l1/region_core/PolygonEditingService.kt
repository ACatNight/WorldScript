@file:Suppress("DEPRECATION")

package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.model.PolygonPoint
import com.worldscript.foundation.model.RegionShape
import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot

class PolygonEditingService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
) {
    private data class Session(
        val regionId: String,
        val points: MutableList<PolygonPoint>,
        val toolMarker: String,
    )

    private val sessions = mutableMapOf<UUID, Session>()
    private val lang = Lang(plugin)

    fun start(player: Player, regionId: String): Boolean {
        sessions[player.uniqueId]?.let { active ->
            lang.send(player, "polygon-session-active", "region" to active.regionId)
            return false
        }
        val region = regions.find(regionId) ?: return false
        val points = (region.shape as? RegionShape.Polygon)?.points?.toMutableList() ?: mutableListOf()
        val session = Session(region.id, points, TOOL_MARKER_PREFIX + UUID.randomUUID())
        if (player.inventory.addItem(tool(region.id, points.size, session.toolMarker)).isNotEmpty()) {
            lang.send(player, "polygon-tool-inventory-full")
            return false
        }
        sessions[player.uniqueId] = session
        lang.send(player, "polygon-started", "region" to region.id, "count" to points.size)
        return true
    }

    fun isEditing(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    fun activeRegion(player: Player): String? = sessions[player.uniqueId]?.regionId

    fun isEditingTool(player: Player, item: ItemStack?): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        return item?.type == configuredToolMaterial() && item.itemMeta?.localizedName == session.toolMarker
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
        session.points += point
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-added", "count" to session.points.size, "x" to x, "z" to z)
        preview(player, notify = false)
    }

    fun undo(player: Player) {
        val session = sessions[player.uniqueId] ?: return
        val removed = session.points.removeLastOrNull()
        if (removed == null) {
            lang.send(player, "polygon-no-points")
            return
        }
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-undone", "count" to session.points.size, "x" to removed.x, "z" to removed.z)
        preview(player, notify = false)
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
            return false
        }
        if (!regions.updatePolygon(session.regionId, session.points)) {
            lang.send(player, "polygon-save-failed", "region" to session.regionId)
            return false
        }
        sessions.remove(player.uniqueId)
        removeTools(player, session.toolMarker)
        lang.send(player, "polygon-saved", "region" to session.regionId, "count" to session.points.size)
        return true
    }

    fun cancel(player: Player, notify: Boolean = true): Boolean {
        val removed = sessions.remove(player.uniqueId) ?: return false
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
        val removed = session.points.removeAt(index)
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-removed", "index" to number, "x" to removed.x, "z" to removed.z, "count" to session.points.size)
        preview(player, notify = false)
        return true
    }

    fun movePoint(player: Player, fromNumber: Int, toNumber: Int): Boolean {
        val session = sessions[player.uniqueId] ?: return missingSession(player)
        val from = fromNumber - 1
        val to = toNumber - 1
        if (from !in session.points.indices || to !in session.points.indices) return invalidIndex(player, session)
        val point = session.points.removeAt(from)
        session.points.add(to, point)
        refreshHeldTool(player, session)
        lang.send(player, "polygon-point-moved", "from" to fromNumber, "to" to toNumber)
        preview(player, notify = false)
        return true
    }

    fun preview(player: Player, notify: Boolean = true): Boolean {
        val session = sessions[player.uniqueId] ?: run {
            if (notify) lang.send(player, "polygon-not-editing")
            return false
        }
        if (session.points.isEmpty()) {
            if (notify) lang.send(player, "polygon-no-points")
            return false
        }
        val particleName = plugin.config.getString("selection.polygon.preview.particle", DEFAULT_PARTICLE) ?: DEFAULT_PARTICLE
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
        val y = player.location.y + offset
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
            removeTools(player, session.toolMarker)
        }
        lang.send(player, "polygon-reset", "region" to region.id)
        return true
    }

    fun clear() = sessions.clear()

    private fun tool(regionId: String, count: Int, marker: String): ItemStack = ItemStack(configuredToolMaterial()).apply {
        val meta = itemMeta ?: return@apply
        meta.setLocalizedName(marker)
        meta.setDisplayName(color(lang.text("polygon-tool-name", "polygon-tool-name", "region" to regionId)))
        meta.lore = listOf(
            lang.text("polygon-tool-lore-region", "polygon-tool-lore-region", "region" to regionId),
            lang.text("polygon-tool-lore-count", "polygon-tool-lore-count", "count" to count),
            lang.text("polygon-tool-lore-add"),
            lang.text("polygon-tool-lore-undo"),
            lang.text("polygon-tool-lore-save"),
            lang.text("polygon-tool-lore-cancel"),
        ).map(::color)
        itemMeta = meta
    }

    private fun refreshHeldTool(player: Player, session: Session) {
        if (isEditingTool(player, player.inventory.itemInMainHand)) {
            player.inventory.setItemInMainHand(tool(session.regionId, session.points.size, session.toolMarker))
        }
    }

    private fun configuredToolMaterial(): Material =
        MaterialResolver.find(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE", "GOLD_AXE") ?: Material.STICK

    private fun cancelMissingRegion(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        removeTools(player, session.toolMarker)
        lang.send(player, "region-not-found", "region" to session.regionId)
    }

    private fun removeTools(player: Player, marker: String) {
        player.inventory.contents.forEachIndexed { slot, item ->
            if (item?.itemMeta?.localizedName == marker) player.inventory.setItem(slot, null)
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

    private fun color(value: String): String = ChatColor.translateAlternateColorCodes('&', value)

    private companion object {
        const val DEFAULT_MAX_POINTS = 128
        const val MAX_POINTS_LIMIT = 512
        const val DEFAULT_PARTICLE = "END_ROD"
        const val DEFAULT_SPACING = 0.75
        const val DEFAULT_MAX_PARTICLES = 512
        const val TOOL_MARKER_PREFIX = "worldscript:polygon-editor:"
    }
}
