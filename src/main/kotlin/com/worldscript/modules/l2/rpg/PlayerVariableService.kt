package com.worldscript.modules.l2.rpg

import com.worldscript.foundation.api.PlayerRegionProgressService
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Player-only region progress. It deliberately cannot change the shared world state. */
data class PlayerRegionProgress(
    val unlockedRegions: MutableSet<String> = linkedSetOf(),
    val enteredRegions: MutableSet<String> = linkedSetOf(),
    val completedRegions: MutableSet<String> = linkedSetOf(),
    val claimedRewards: MutableSet<String> = linkedSetOf(),
) {
    fun isUnlocked(regionId: String): Boolean = regionId.key() in unlockedRegions
    fun unlock(regionId: String) = unlockedRegions.add(regionId.key())
    fun hasEntered(regionId: String): Boolean = regionId.key() in enteredRegions
    fun markEntered(regionId: String) = enteredRegions.add(regionId.key())
    fun isCompleted(regionId: String): Boolean = regionId.key() in completedRegions
    fun markCompleted(regionId: String) = completedRegions.add(regionId.key())

    private fun String.key(): String = trim().lowercase()
}

private data class PlayerState(
    val variables: MutableMap<String, String>,
    val progress: PlayerRegionProgress,
)

private data class PlayerStateSnapshot(
    val uuid: UUID,
    val variables: Map<String, String>,
    val unlockedRegions: Set<String>,
    val enteredRegions: Set<String>,
    val completedRegions: Set<String>,
    val claimedRewards: Set<String>,
)

class PlayerVariableService(private val plugin: JavaPlugin) : Listener, PlayerRegionProgressService {
    private val playerDirectory = File(plugin.dataFolder, "players")
    private val states = mutableMapOf<UUID, PlayerState>()
    private val dirty = linkedSetOf<UUID>()
    private val offlineSince = mutableMapOf<UUID, Long>()
    private val saveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "WorldScript-player-state").apply { isDaemon = true }
    }
    private val flushTask: BukkitTask
    private var closed = false

    init {
        if (!playerDirectory.exists()) playerDirectory.mkdirs()
        // Snapshot on the server thread, then serialize those snapshots away from gameplay.
        flushTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            flushDirty()
            releaseOfflineStates()
        }, 100L, 100L)
    }

    fun variable(player: Player, key: String): String? =
        stateFor(player.uniqueId).variables.entries.firstOrNull { it.key.equals(key.trim(), true) }?.value

    fun variables(player: Player): Map<String, String> = stateFor(player.uniqueId).variables.toMap()

    fun setVariable(player: Player, key: String, value: String) {
        if (key.isBlank()) return
        stateFor(player.uniqueId).variables[key.trim()] = value
        markDirty(player.uniqueId)
    }

    fun isRegionUnlocked(player: Player, regionId: String): Boolean = isRegionUnlocked(player.uniqueId, regionId)

    override fun isRegionUnlocked(playerId: UUID, regionId: String): Boolean = stateFor(playerId).progress.isUnlocked(regionId)

    fun unlockRegion(player: Player, regionId: String) = unlockRegion(player.uniqueId, regionId)

    override fun unlockRegion(playerId: UUID, regionId: String) {
        stateFor(playerId).progress.unlock(regionId)
        markDirty(playerId)
    }

    fun hasEnteredRegion(player: Player, regionId: String): Boolean = hasEnteredRegion(player.uniqueId, regionId)

    override fun hasEnteredRegion(playerId: UUID, regionId: String): Boolean = stateFor(playerId).progress.hasEntered(regionId)

    fun markRegionEntered(player: Player, regionId: String) = markRegionEntered(player.uniqueId, regionId)

    override fun markRegionEntered(playerId: UUID, regionId: String) {
        stateFor(playerId).progress.markEntered(regionId)
        markDirty(playerId)
    }

    fun isRegionCompleted(player: Player, regionId: String): Boolean = isRegionCompleted(player.uniqueId, regionId)

    override fun isRegionCompleted(playerId: UUID, regionId: String): Boolean = stateFor(playerId).progress.isCompleted(regionId)

    fun markRegionCompleted(player: Player, regionId: String) = markRegionCompleted(player.uniqueId, regionId)

    override fun markRegionCompleted(playerId: UUID, regionId: String) {
        stateFor(playerId).progress.markCompleted(regionId)
        markDirty(playerId)
    }

    /** Clears all per-player discovery state for one region so it can be tested again. */
    fun resetRegionProgress(playerId: UUID, regionId: String) {
        val key = regionId.trim().lowercase()
        val progress = stateFor(playerId).progress
        progress.unlockedRegions.remove(key)
        progress.enteredRegions.remove(key)
        progress.completedRegions.remove(key)
        markDirty(playerId)
    }

    fun claimReward(player: Player, rewardKey: String): Boolean {
        val claimed = stateFor(player.uniqueId).progress.claimedRewards
        if (!claimed.add(rewardKey)) return false
        markDirty(player.uniqueId)
        return true
    }

    fun isRewardClaimed(player: Player, rewardKey: String): Boolean = rewardKey in stateFor(player.uniqueId).progress.claimedRewards

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        offlineSince.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        markDirty(uuid)
        offlineSince[uuid] = System.currentTimeMillis()
        flushDirty(uuid)
    }

    fun saveAll() {
        if (closed) return
        states.keys.forEach(::markDirty)
        flushDirty()
        flushTask.cancel()
        closed = true
        saveExecutor.shutdown()
        if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            plugin.logger.warning("Player state writes did not finish before WorldScript disabled.")
        }
    }

    private fun stateFor(uuid: UUID): PlayerState = states.getOrPut(uuid) { loadState(uuid) }

    private fun markDirty(uuid: UUID) {
        if (!closed) dirty += uuid
    }

    private fun flushDirty(only: UUID? = null) {
        if (closed) return
        val pending = if (only == null) {
            dirty.toList().also { dirty.clear() }
        } else if (dirty.remove(only)) {
            listOf(only)
        } else {
            emptyList()
        }
        pending.mapNotNull { uuid -> states[uuid]?.let { snapshot(uuid, it) } }.forEach { snapshot ->
            runCatching { saveExecutor.execute { write(snapshot) } }
                .onFailure { plugin.logger.warning("Could not queue RPG state save for ${snapshot.uuid}: ${it.message}") }
        }
    }

    private fun releaseOfflineStates() {
        val cutoff = System.currentTimeMillis() - OFFLINE_CACHE_MILLIS
        offlineSince.entries.removeIf { (uuid, since) ->
            if (since > cutoff || uuid in dirty) return@removeIf false
            states.remove(uuid)
            true
        }
    }

    private fun loadState(uuid: UUID): PlayerState {
        val data = YamlConfiguration.loadConfiguration(File(playerDirectory, "$uuid.yml"))
        val variables = data.getConfigurationSection("variables")?.getKeys(false)
            ?.associateWith { key -> data.getString("variables.$key", "") ?: "" }
            ?.toMutableMap() ?: linkedMapOf()
        return PlayerState(
            variables,
            PlayerRegionProgress(
                data.getStringList("region-state.unlocked").map(::regionKey).toMutableSet(),
                data.getStringList("region-state.entered").map(::regionKey).toMutableSet(),
                data.getStringList("region-state.completed").map(::regionKey).toMutableSet(),
                data.getStringList("region-state.claimed-rewards").toMutableSet(),
            ),
        )
    }

    private fun snapshot(uuid: UUID, state: PlayerState): PlayerStateSnapshot = PlayerStateSnapshot(
        uuid,
        state.variables.toMap(),
        state.progress.unlockedRegions.toSet(),
        state.progress.enteredRegions.toSet(),
        state.progress.completedRegions.toSet(),
        state.progress.claimedRewards.toSet(),
    )

    private fun write(snapshot: PlayerStateSnapshot) {
        val file = File(playerDirectory, "${snapshot.uuid}.yml")
        runCatching {
            val data = YamlConfiguration()
            data.set("variables", snapshot.variables)
            data.set("region-state.unlocked", snapshot.unlockedRegions.toList())
            data.set("region-state.entered", snapshot.enteredRegions.toList())
            data.set("region-state.completed", snapshot.completedRegions.toList())
            data.set("region-state.claimed-rewards", snapshot.claimedRewards.toList())
            data.save(file)
        }.onFailure { plugin.logger.warning("Could not save RPG state for ${snapshot.uuid}: ${it.message}") }
    }

    private fun regionKey(value: String): String = value.trim().lowercase()

    private companion object {
        const val OFFLINE_CACHE_MILLIS = 10 * 60 * 1000L
    }
}
