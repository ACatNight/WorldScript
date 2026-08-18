package com.worldscript.modules.l2.atmosphere

import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
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
    private val task: BukkitTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
        tick++
        plugin.server.onlinePlayers.forEach { player -> emit(player) }
    }, 1L, 1L)

    fun close() = task.cancel()

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
        effectLocations(region.bounds, player.location, definition.preset).forEach { location ->
            if (location.distanceSquared(player.location) <= 32.0 * 32.0) {
                player.spawnParticle(particle, location, definition.count, definition.spreadX, definition.spreadY, definition.spreadZ, definition.speed)
            }
        }
    }

    private fun effectLocations(bounds: com.worldscript.foundation.model.RegionBounds, playerLocation: Location, preset: String): List<Location> {
        val world = playerLocation.world ?: return emptyList()
        val min = bounds.min
        val max = bounds.max
        val center = Location(world, (min.x + max.x) / 2.0 + 0.5, (min.y + max.y) / 2.0 + 0.5, (min.z + max.z) / 2.0 + 0.5)
        return when (preset.uppercase()) {
            "BORDER" -> listOf(
                Location(world, min.x + 0.5, center.y, min.z + 0.5),
                Location(world, max.x + 0.5, center.y, min.z + 0.5),
                Location(world, min.x + 0.5, center.y, max.z + 0.5),
                Location(world, max.x + 0.5, center.y, max.z + 0.5),
            )
            "PORTAL", "ENTRANCE", "WARNING" -> listOf(center)
            else -> listOf(playerLocation.clone().add(0.0, 0.8, 0.0))
        }
    }
}
