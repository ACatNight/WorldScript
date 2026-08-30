package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.BukkitCompatibility
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

class SelectionService(private val plugin: org.bukkit.plugin.java.JavaPlugin) {
    private val positions = mutableMapOf<UUID, Array<Location?>>()
    private val previewTasks = mutableMapOf<UUID, BukkitTask>()

    fun set(player: Player, index: Int, location: Location) {
        positions.computeIfAbsent(player.uniqueId) { arrayOfNulls(2) }[index - 1] = location.clone()
        restartPreview(player)
    }

    fun get(player: Player): Array<Location?>? = positions[player.uniqueId]

    fun preview(player: Player): Boolean {
        if (positions[player.uniqueId]?.any { it != null } != true) return false
        restartPreview(player)
        return true
    }

    fun clear(player: Player) = clear(player.uniqueId)

    fun clear(playerId: UUID) {
        positions.remove(playerId)
        previewTasks.remove(playerId)?.cancel()
    }

    private fun restartPreview(player: Player) {
        previewTasks.remove(player.uniqueId)?.cancel()
        if (!plugin.config.getBoolean("selection.preview.enabled", true)) return
        val duration = plugin.config.getLong("selection.preview.duration-ticks", 100L).coerceAtLeast(1L)
        val interval = plugin.config.getLong("selection.preview.interval-ticks", 10L).coerceAtLeast(1L)
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (player.isOnline && positions[player.uniqueId] != null) render(player)
        }, 0L, interval)
        previewTasks[player.uniqueId] = task
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (previewTasks[player.uniqueId] === task) {
                task.cancel()
                previewTasks.remove(player.uniqueId)
            }
        }, duration)
    }

    private fun render(player: Player) {
        val selected = positions[player.uniqueId] ?: return
        val first = selected[0]
        val second = selected[1]
        val particle = BukkitCompatibility.resolveParticle(plugin.config.getString("selection.preview.particle", "END_ROD") ?: return) ?: return
        val count = plugin.config.getInt("selection.preview.count", 1).coerceIn(1, 16)
        val offset = plugin.config.getDouble("selection.preview.y-offset", 0.5)
        val speed = plugin.config.getDouble("selection.preview.speed", 0.0).coerceAtLeast(0.0)
        if (first == null || second == null || first.world?.uid != second.world?.uid) {
            val point = first ?: second ?: return
            player.spawnParticle(particle, point.x + 0.5, point.y + offset, point.z + 0.5, count, 0.0, 0.0, 0.0, speed)
            return
        }
        val spacing = plugin.config.getDouble("selection.preview.spacing", 1.0).coerceIn(0.25, 8.0)
        val limit = plugin.config.getInt("selection.preview.max-particles", 512).coerceIn(12, 4096)
        val minX = minOf(first.blockX, second.blockX).toDouble()
        val maxX = maxOf(first.blockX, second.blockX).toDouble() + 1.0
        val minY = minOf(first.blockY, second.blockY).toDouble()
        val maxY = maxOf(first.blockY, second.blockY).toDouble() + 1.0
        val minZ = minOf(first.blockZ, second.blockZ).toDouble()
        val maxZ = maxOf(first.blockZ, second.blockZ).toDouble() + 1.0
        val corners = listOf(
            doubleArrayOf(minX, minY, minZ), doubleArrayOf(maxX, minY, minZ), doubleArrayOf(minX, maxY, minZ), doubleArrayOf(maxX, maxY, minZ),
            doubleArrayOf(minX, minY, maxZ), doubleArrayOf(maxX, minY, maxZ), doubleArrayOf(minX, maxY, maxZ), doubleArrayOf(maxX, maxY, maxZ),
        )
        val edges = listOf(0 to 1, 0 to 2, 0 to 4, 1 to 3, 1 to 5, 2 to 3, 2 to 6, 3 to 7, 4 to 5, 4 to 6, 5 to 7, 6 to 7)
        var remaining = limit
        edges.forEach { (start, end) ->
            val from = corners[start]
            val to = corners[end]
            val distance = kotlin.math.sqrt((to[0] - from[0]) * (to[0] - from[0]) + (to[1] - from[1]) * (to[1] - from[1]) + (to[2] - from[2]) * (to[2] - from[2]))
            val samples = max(1, ceil(distance / spacing).toInt())
            for (index in 0..samples) {
                if (remaining-- <= 0) return
                val progress = index.toDouble() / samples
                player.spawnParticle(particle, from[0] + (to[0] - from[0]) * progress, from[1] + (to[1] - from[1]) * progress + offset, from[2] + (to[2] - from[2]) * progress, count, 0.0, 0.0, 0.0, speed)
            }
        }
    }
}
