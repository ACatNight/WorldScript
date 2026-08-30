package com.worldscript.modules.l2.atmosphere

import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.foundation.model.PolygonPoint
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionShape
import com.worldscript.modules.l2.rpg.PlayerVariableService
import com.worldscript.foundation.BukkitCompatibility
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.Location

/** Small, local atmosphere layer. The deepest active region owns the effect. */
class RegionParticleService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) {
    private var tick: Long = 0
    private val locationCache = LinkedHashMap<String, List<Location>>(128, 0.75f, true)
    private val task: BukkitTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
        tick++
        plugin.server.onlinePlayers.forEach { player -> emit(player) }
    }, 1L, 1L)

    fun invalidate() = locationCache.clear()

    fun close() {
        task.cancel()
        invalidate()
    }

    private fun emit(player: Player) {
        val region = regions.regionsAt(player.location) { id ->
            regions.isAccessible(id, state.isRegionUnlocked(player, id))
        }.lastOrNull() ?: return
        val definition = regions.effective(region.id)?.particle ?: return
        if (!definition.enabled) return
        if (tick % definition.intervalTicks != 0L) return
        val particle = BukkitCompatibility.resolveParticle(definition.type) ?: run {
            plugin.logger.warning("Unsupported particle '${definition.type}' in region '${region.id}'.")
            return
        }
        val bounds = region.bounds
        val dx = player.location.x - (bounds.min.x + bounds.max.x) / 2.0
        val dz = player.location.z - (bounds.min.z + bounds.max.z) / 2.0
        if (dx * dx + dz * dz > 48.0 * 48.0) return
        effectLocations(region, player.location, definition.preset).forEach { location ->
            if (location.distanceSquared(player.location) <= 32.0 * 32.0) {
                player.spawnParticle(particle, location, definition.count, definition.spreadX, definition.spreadY, definition.spreadZ, definition.speed)
            }
        }
    }

    private fun effectLocations(region: RegionDefinition, playerLocation: Location, preset: String): List<Location> {
        val world = playerLocation.world ?: return emptyList()
        val bounds = region.bounds
        val min = bounds.min
        val max = bounds.max
        val center = Location(world, (min.x + max.x) / 2.0 + 0.5, (min.y + max.y) / 2.0 + 0.5, (min.z + max.z) / 2.0 + 0.5)
        val cacheKey = "${region.id}|${preset.uppercase()}|${center.y.toInt()}|${world.uid}"
        if (preset.uppercase() == "BORDER") locationCache[cacheKey]?.let { return it }
        val result = when (preset.uppercase()) {
            "BORDER" -> when (val shape = region.shape) {
                is RegionShape.Polygon -> polygonBorder(world, shape.points, center.y)
                RegionShape.Cuboid -> listOf(
                    Location(world, min.x + 0.5, center.y, min.z + 0.5),
                    Location(world, max.x + 0.5, center.y, min.z + 0.5),
                    Location(world, min.x + 0.5, center.y, max.z + 0.5),
                    Location(world, max.x + 0.5, center.y, max.z + 0.5),
                )
            }
            "PORTAL", "ENTRANCE", "WARNING" -> listOf(center)
            else -> listOf(playerLocation.clone().add(0.0, 0.8, 0.0))
        }
        if (preset.uppercase() == "BORDER") {
            locationCache[cacheKey] = result
            while (locationCache.size > 128) locationCache.remove(locationCache.entries.first().key)
        }
        return result
    }

    /** Samples the actual polygon outline instead of its enclosing cuboid. */
    private fun polygonBorder(world: org.bukkit.World, points: List<PolygonPoint>, y: Double): List<Location> {
        if (points.size < 3) return emptyList()
        val result = ArrayList<Location>()
        points.forEachIndexed { index, start ->
            val end = points[(index + 1) % points.size]
            val distance = kotlin.math.hypot((end.x - start.x).toDouble(), (end.z - start.z).toDouble())
            val steps = distance.toInt().coerceAtLeast(1).coerceAtMost(64)
            for (step in 0 until steps) {
                val t = step.toDouble() / steps
                result += Location(world, start.x + (end.x - start.x) * t + 0.5, y, start.z + (end.z - start.z) * t + 0.5)
            }
        }
        return result
    }
}
