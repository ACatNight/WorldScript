package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Loads the same action definitions shown in chat from presets/actions.yml. */
class ActionPresetCatalog(private val plugin: JavaPlugin) {
    private var entries: List<ActionPreset> = emptyList()

    init {
        reload()
    }

    fun reload() {
        val file = File(plugin.dataFolder, "presets/actions.yml")
        val config = YamlConfiguration.loadConfiguration(file)
        entries = config.getConfigurationSection("actions")?.getKeys(false)?.mapNotNull { id ->
            val path = "actions.$id"
            val rawType = config.getString("$path.type") ?: return@mapNotNull null
            val type = ActionType.entries.firstOrNull { it.name.equals(rawType.replace('-', '_'), true) }
                ?: return@mapNotNull null
            val defaults = config.getConfigurationSection("$path.defaults")
                ?.getKeys(false)
                ?.associateWith { key -> config.getString("$path.defaults.$key", "") ?: "" }
                ?: emptyMap()
            ActionPreset(id, config.getString("$path.name", id) ?: id, type, defaults)
        } ?: emptyList()
    }

    fun all(): List<ActionPreset> = entries

    fun create(id: String): ActionDefinition? = entries.firstOrNull { it.id.equals(id, true) }
        ?.let { preset -> ActionDefinition(preset.type, parameters = preset.defaults, preset = preset.id) }
}

data class ActionPreset(
    val id: String,
    val name: String,
    val type: ActionType,
    val defaults: Map<String, String>,
)
