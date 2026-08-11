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

    init {
        if (!playerDirectory.exists()) playerDirectory.mkdirs()
    }

    fun variable(player: Player, key: String): String? = variablesFor(player.uniqueId)[key]

    fun setVariable(player: Player, key: String, value: String) {
        if (key.isBlank()) return
        variablesFor(player.uniqueId)[key.trim()] = value
        save(player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        save(event.player.uniqueId)
        variables.remove(event.player.uniqueId)
    }

    fun saveAll() {
        variables.keys.toList().forEach(::save)
    }

    private fun variablesFor(uuid: UUID): MutableMap<String, String> = variables.getOrPut(uuid) {
        val data = load(uuid)
        data.getConfigurationSection("variables")?.getKeys(false)?.associateWith { key -> data.getString("variables.$key", "") ?: "" }?.toMutableMap()
            ?: mutableMapOf()
    }

    private fun load(uuid: UUID): YamlConfiguration = YamlConfiguration.loadConfiguration(File(playerDirectory, "$uuid.yml"))

    private fun save(uuid: UUID) {
        val data = YamlConfiguration()
        variables[uuid]?.let { data.set("variables", it) }
        runCatching { data.save(File(playerDirectory, "$uuid.yml")) }
            .onFailure { plugin.logger.warning("Could not save RPG state for $uuid: ${it.message}") }
    }
}
