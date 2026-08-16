package com.worldscript.foundation

import org.bukkit.Material

/** Resolves flattened material names while keeping 1.12 servers usable. */
object MaterialResolver {
    fun find(primary: String, vararg legacy: String): Material? =
        (listOf(primary) + legacy.toList())
            .asSequence()
            .mapNotNull { name -> Material.matchMaterial(name) }
            .firstOrNull()
}
