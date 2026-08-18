package com.worldscript.foundation

import org.bukkit.Particle
import org.bukkit.Sound
import java.util.Locale

object BukkitCompatibility {
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

    private fun soundValue(name: String): Sound? = runCatching { Sound.valueOf(name) }.getOrNull()
}
