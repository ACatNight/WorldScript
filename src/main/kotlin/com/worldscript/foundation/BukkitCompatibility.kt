@file:Suppress("DEPRECATION")

package com.worldscript.foundation

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.inventory.Inventory
import java.lang.reflect.Method
import java.util.Locale

object BukkitCompatibility {
    // InventoryView changed from a class to an interface in modern Bukkit.
    // Reflection avoids encoding either invokevirtual or invokeinterface.
    private val topInventoryMethod: Method = Class.forName("org.bukkit.inventory.InventoryView").getMethod("getTopInventory")
    private val passableMethod: Method? = runCatching { Block::class.java.getMethod("isPassable") }.getOrNull()
    private val soundValueMethod: Method = Sound::class.java.getMethod("valueOf", String::class.java)

    fun topInventory(event: InventoryEvent): Inventory = topInventoryMethod.invoke(event.view) as Inventory

    fun isPassable(block: Block): Boolean = passableMethod?.invoke(block) as? Boolean ?: block.isEmpty

    fun resolveSound(value: String): Sound? {
        val name = value.trim().uppercase(Locale.ROOT)
        return soundValue(name) ?: when (name) {
            "BLOCK_NOTE_BLOCK_PLING" -> soundValue("BLOCK_NOTE_PLING")
            "BLOCK_NOTE_PLING" -> soundValue("BLOCK_NOTE_BLOCK_PLING")
            else -> null
        }
    }

    fun resolveParticle(value: String): Particle? =
        runCatching { Particle.valueOf(value.trim().uppercase(Locale.ROOT)) }.getOrNull()

    private fun soundValue(name: String): Sound? = runCatching { soundValueMethod.invoke(null, name) as? Sound }.getOrNull()
}
