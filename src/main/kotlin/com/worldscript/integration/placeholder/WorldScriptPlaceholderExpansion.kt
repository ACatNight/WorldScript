@file:Suppress("DEPRECATION") // PlaceholderAPI exposes this metadata through the Bukkit plugin descriptor.

package com.worldscript.integration.placeholder

import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.rpg.PlayerVariableService
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class WorldScriptPlaceholderExpansion(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "worldscript"

    override fun getAuthor(): String = "WorldScript"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String {
        if (player == null) return ""
        val current = regions.regionsAt(player.location) { id ->
            regions.isAccessible(id, state.isRegionUnlocked(player, id))
        }.lastOrNull()
        val effective = current?.let { regions.effective(it.id) }
        val parent = current?.parentId?.let { regions.effective(it) }
        val currentId = current?.id ?: ""

        return when (val request = PlaceholderRequest.parse(params)) {
            is PlaceholderRequest.RegionVariable -> variableValue(effective?.variables, request.key)
            is PlaceholderRequest.ParentVariable -> variableValue(parent?.variables, request.key)
            is PlaceholderRequest.ChildVariable -> variableValue(effective?.variables, request.key)
            is PlaceholderRequest.DynamicVariable -> variableValue(effective?.variables, request.key)
            is PlaceholderRequest.Fixed -> when (request.key) {
            "region_id" -> currentId
            "region_name" -> effective?.displayName ?: ""
            "region_role" -> effective?.role?.name?.lowercase() ?: ""
            "region_content_id" -> effective?.contentId ?: ""
            "parent_id" -> parent?.id ?: ""
            "parent_name" -> parent?.displayName ?: ""
            "child_id" -> current?.takeIf { it.parentId != null }?.id ?: ""
            "child_name" -> effective?.takeIf { it.parentId != null }?.displayName ?: ""
            "region_path" -> current?.let { regionPath(it.id, it.displayName, parent) } ?: ""
            "region_depth" -> current?.let { regions.depth(it.id).toString() } ?: "0"
            "region_unlocked" -> current?.let { state.isRegionUnlocked(player, it.id).toString() } ?: "false"
            "region_entered" -> current?.let { state.hasEnteredRegion(player, it.id).toString() } ?: "false"
            "region_completed" -> current?.let { state.isRegionCompleted(player, it.id).toString() } ?: "false"
            "region_world" -> player.world.name
            else -> ""
            }
            PlaceholderRequest.Unknown -> ""
        }
    }

    private fun variableValue(variables: Map<String, String>?, key: String): String =
        variables?.entries?.firstOrNull { it.key.equals(key, true) }?.value ?: ""

    private fun regionPath(id: String, currentName: String, parent: com.worldscript.foundation.model.RegionDefinition?): String {
        val path = regions.displayPath(id)
        val parentPath = parent?.let { regions.displayPath(it.id) }.orEmpty()
        return RegionNameFormatter.format(
            plugin.config.getString("placeholders.region-name-format", "{parent} / {current}") ?: "{parent} / {current}",
            parentPath,
            currentName,
            id,
            path,
        )
    }
}

/** Applies the administrator-facing region name template without recursive placeholder parsing. */
internal object RegionNameFormatter {
    fun format(template: String, parent: String, current: String, id: String, path: String): String {
        var result = template.trim()
        if (parent.isBlank()) {
            result = result.replace(Regex("\\{parent\\}\\s*(/|›|>|»|-)\\s*"), "")
        }
        return result
            .replace("{parent}", parent)
            .replace("{current}", current)
            .replace("{child}", current)
            .replace("{id}", id)
            .replace("{path}", path)
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}

/** Normalizes PlaceholderAPI parameters without touching Bukkit state. */
internal sealed class PlaceholderRequest {
    data class Fixed(val key: String) : PlaceholderRequest()
    data class RegionVariable(val key: String) : PlaceholderRequest()
    data class ParentVariable(val key: String) : PlaceholderRequest()
    data class ChildVariable(val key: String) : PlaceholderRequest()
    data class DynamicVariable(val key: String) : PlaceholderRequest()
    data object Unknown : PlaceholderRequest()

    companion object {
        fun parse(params: String): PlaceholderRequest {
            val trimmed = params.trim()
            val normalized = trimmed.lowercase()
            return when {
                normalized.isBlank() -> Unknown
                normalized in fixedKeys -> Fixed(normalized)
                normalized.startsWith("parent_var_") -> ParentVariable(trimmed.substring("parent_var_".length))
                normalized.startsWith("parent_") -> ParentVariable(trimmed.substring("parent_".length))
                normalized.startsWith("child_var_") -> ChildVariable(trimmed.substring("child_var_".length))
                normalized.startsWith("child_") -> ChildVariable(trimmed.substring("child_".length))
                normalized.startsWith("region_var_") -> RegionVariable(trimmed.substring("region_var_".length))
                normalized.startsWith("var_") -> RegionVariable(trimmed.substring("var_".length))
                else -> DynamicVariable(trimmed)
            }
        }

        private val fixedKeys = setOf(
            "region_id", "region_name", "region_role", "region_content_id", "parent_id", "parent_name",
            "child_id", "child_name", "region_path", "region_depth", "region_unlocked", "region_entered",
            "region_completed", "region_world",
        )
    }
}
