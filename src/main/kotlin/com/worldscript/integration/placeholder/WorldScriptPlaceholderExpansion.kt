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

        return when (params.lowercase()) {
            "region_id" -> currentId
            "region_name" -> effective?.displayName ?: ""
            "region_role" -> effective?.role?.name?.lowercase() ?: ""
            "region_content_id" -> effective?.contentId ?: ""
            "parent_id" -> parent?.id ?: ""
            "parent_name" -> parent?.displayName ?: ""
            "child_id" -> current?.takeIf { it.parentId != null }?.id ?: ""
            "child_name" -> effective?.takeIf { it.parentId != null }?.displayName ?: ""
            "region_depth" -> current?.let { regions.depth(it.id).toString() } ?: "0"
            "region_unlocked" -> current?.let { regions.isAccessible(it.id, state.isRegionUnlocked(player, it.id)).toString() } ?: "false"
            "region_entered" -> current?.let { state.hasEnteredRegion(player, it.id).toString() } ?: "false"
            "region_completed" -> current?.let { state.isRegionCompleted(player, it.id).toString() } ?: "false"
            "region_world" -> player.world.name
            else -> ""
        }
    }
}
