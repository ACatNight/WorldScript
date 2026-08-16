package com.worldscript.modules.l2.atmosphere

import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

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
        val particle = runCatching { Particle.valueOf(definition.type) }.getOrNull() ?: return
        player.spawnParticle(
            particle,
            player.location.clone().add(0.0, 0.8, 0.0),
            definition.count,
            definition.spreadX,
            definition.spreadY,
            definition.spreadZ,
            definition.speed,
        )
    }
}
