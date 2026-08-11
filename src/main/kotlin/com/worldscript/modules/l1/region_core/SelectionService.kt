package com.worldscript.modules.l1.region_core

import org.bukkit.Location
import org.bukkit.entity.Player

class SelectionService(private val plugin: org.bukkit.plugin.java.JavaPlugin) {
    private val positions = mutableMapOf<java.util.UUID, Array<Location?>>()
    fun set(player: Player, index: Int, location: Location) { positions.computeIfAbsent(player.uniqueId) { arrayOfNulls(2) }[index - 1] = location.clone() }
    fun get(player: Player): Array<Location?>? = positions[player.uniqueId]
    fun clear(player: Player) { positions.remove(player.uniqueId) }
    fun clear(playerId: java.util.UUID) { positions.remove(playerId) }
}
