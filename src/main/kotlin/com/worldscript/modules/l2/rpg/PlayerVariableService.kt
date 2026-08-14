package com.worldscript.modules.l2.rpg

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class PlayerVariableService(private val plugin: JavaPlugin) : Listener {
    private val playerDirectory = File(plugin.dataFolder, "players")
    private val variables = mutableMapOf<UUID, MutableMap<String, String>>()
    private val unlockedRegions = mutableMapOf<UUID, MutableSet<String>>()
    private val enteredRegions = mutableMapOf<UUID, MutableSet<String>>()
    private val completedRegions = mutableMapOf<UUID, MutableSet<String>>()
    private val claimedRewards = mutableMapOf<UUID, MutableSet<String>>()

    init {
        if (!playerDirectory.exists()) playerDirectory.mkdirs()
    }

    fun variable(player: Player, key: String): String? = variablesFor(player.uniqueId)[key]

    fun setVariable(player: Player, key: String, value: String) {
        if (key.isBlank()) return
        variablesFor(player.uniqueId)[key.trim()] = value
        save(player.uniqueId)
    }

    fun isRegionUnlocked(player: Player, regionId: String): Boolean = regionId.key() in unlockedFor(player.uniqueId)

    fun unlockRegion(player: Player, regionId: String) {
        unlockedFor(player.uniqueId).add(regionId.key())
        save(player.uniqueId)
    }

    fun hasEnteredRegion(player: Player, regionId: String): Boolean = regionId.key() in enteredFor(player.uniqueId)

    fun markRegionEntered(player: Player, regionId: String) {
        enteredFor(player.uniqueId).add(regionId.key())
        save(player.uniqueId)
    }

    fun isRegionCompleted(player: Player, regionId: String): Boolean = regionId.key() in completedFor(player.uniqueId)

    fun markRegionCompleted(player: Player, regionId: String) {
        completedFor(player.uniqueId).add(regionId.key())
        save(player.uniqueId)
    }

    fun claimReward(player: Player, rewardKey: String): Boolean {
        val claimed = claimedFor(player.uniqueId)
        if (!claimed.add(rewardKey)) return false
        save(player.uniqueId)
        return true
    }

    fun isRewardClaimed(player: Player, rewardKey: String): Boolean = rewardKey in claimedFor(player.uniqueId)

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        save(event.player.uniqueId)
        variables.remove(event.player.uniqueId)
        unlockedRegions.remove(event.player.uniqueId)
        enteredRegions.remove(event.player.uniqueId)
        completedRegions.remove(event.player.uniqueId)
        claimedRewards.remove(event.player.uniqueId)
    }

    fun saveAll() {
        variables.keys.toList().forEach(::save)
    }

    private fun variablesFor(uuid: UUID): MutableMap<String, String> = variables.getOrPut(uuid) {
        val data = load(uuid)
        data.getConfigurationSection("variables")?.getKeys(false)?.associateWith { key -> data.getString("variables.$key", "") ?: "" }?.toMutableMap()
            ?: mutableMapOf()
    }

    private fun unlockedFor(uuid: UUID): MutableSet<String> = unlockedRegions.getOrPut(uuid) { loadSet(uuid, "region-state.unlocked") }

    private fun enteredFor(uuid: UUID): MutableSet<String> = enteredRegions.getOrPut(uuid) { loadSet(uuid, "region-state.entered") }

    private fun completedFor(uuid: UUID): MutableSet<String> = completedRegions.getOrPut(uuid) { loadSet(uuid, "region-state.completed") }

    private fun claimedFor(uuid: UUID): MutableSet<String> = claimedRewards.getOrPut(uuid) { loadSet(uuid, "region-state.claimed-rewards") }

    private fun loadSet(uuid: UUID, path: String): MutableSet<String> = load(uuid).getStringList(path).map { it.key() }.toMutableSet()

    private fun load(uuid: UUID): YamlConfiguration = YamlConfiguration.loadConfiguration(File(playerDirectory, "$uuid.yml"))

    private fun save(uuid: UUID) {
        val data = load(uuid)
        variables[uuid]?.let { data.set("variables", it) }
        unlockedRegions[uuid]?.let { data.set("region-state.unlocked", it.toList()) }
        enteredRegions[uuid]?.let { data.set("region-state.entered", it.toList()) }
        completedRegions[uuid]?.let { data.set("region-state.completed", it.toList()) }
        claimedRewards[uuid]?.let { data.set("region-state.claimed-rewards", it.toList()) }
        runCatching { data.save(File(playerDirectory, "$uuid.yml")) }
            .onFailure { plugin.logger.warning("Could not save RPG state for $uuid: ${it.message}") }
    }

    private fun String.key(): String = trim().lowercase()
}
