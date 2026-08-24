package com.worldscript.command

import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionParticleDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.entity.Player

/** Owns the particle/atmosphere page so the main chat editor stays focused on routing. */
internal class EditorParticleController(
    private val regions: RegionCoreServiceImpl,
    private val renderer: EditorRenderer,
    private val send: (Player, String, String, Array<out Pair<String, Any?>>) -> Unit,
    private val message: (String, String, Array<out Pair<String, Any?>>) -> String,
) {
    private fun text(key: String, fallback: String) = renderer.text(key, fallback)

    fun render(player: Player, region: RegionDefinition) {
        val local = region.particle
        val particle = local ?: regions.effective(region.id)?.particle ?: RegionParticleDefinition(enabled = false)
        renderer.group(player, text("group-atmosphere", "&dRegion atmosphere"))
        renderer.property(player, text("label-particle-state", "&d[Display state]"), if (particle.enabled) text("value-enabled", "Enabled") else text("value-disabled", "Disabled"), if (particle.enabled) text("button-close", "&c[Close]") else text("button-open", "&a[Open]"), "/ws edit ${region.id} particle:toggle")
        renderer.property(player, text("label-preset", "&d[Visual style]"), particle.preset, text("button-readonly", "&8[Read-only]"))
        renderer.property(player, text("label-particle-type", "&d[Particle type]"), particle.type, text("button-preview", "&d[Preview]"), "/ws edit ${region.id} particle:preview", listOf(
            ChatEditorButton(text("button-previous", "&e[Previous]"), text("hint-previous-particle", "&7Select the previous particle"), "/ws edit ${region.id} particle:prev"),
            ChatEditorButton(text("button-next", "&e[Next]"), text("hint-next-particle", "&7Select the next particle"), "/ws edit ${region.id} particle:next"),
        ))
        renderer.stepper(player, text("label-particle-count", "&e[Particle count]"), particle.count.toString(), "&c[-1]", "/ws edit ${region.id} particle:count:-1", "&a[+1]", "/ws edit ${region.id} particle:count:1")
        renderer.stepper(player, text("label-particle-interval", "&e[Spawn interval]"), "${particle.intervalTicks} tick", "&c[-5]", "/ws edit ${region.id} particle:interval:-5", "&a[+5]", "/ws edit ${region.id} particle:interval:5")
        renderer.property(player, text("label-particle-spread", "&b[Spread]"), "${particle.spreadX}, ${particle.spreadY}, ${particle.spreadZ}", text("button-config", "&8[Config]"))
        if (local == null && region.parentId != null) sendMessage(player, "particle-inherited", "&8Particles are inherited from the parent; the first edit creates a local override.")
    }

    fun control(player: Player, region: RegionDefinition, value: String) {
        val current = region.particle ?: regions.effective(region.id)?.particle ?: RegionParticleDefinition(enabled = false)
        val parts = value.split(':', limit = 2)
        if (parts[0] == "preview") { preview(player, current); return }
        val updated = when (parts[0]) {
            "toggle" -> current.copy(enabled = !current.enabled)
            "prev", "next" -> {
                val choices = EditorCatalog.particleChoices.filter { BukkitCompatibility.resolveParticle(it) != null }.ifEmpty { listOf(current.type) }
                val index = choices.indexOf(current.type).coerceAtLeast(0)
                val delta = if (parts[0] == "next") 1 else -1
                current.copy(type = choices[(index + delta + choices.size) % choices.size])
            }
            "count" -> current.copy(count = (current.count + (parts.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(1, 64))
            "interval" -> current.copy(intervalTicks = (current.intervalTicks + (parts.getOrNull(1)?.toLongOrNull() ?: 0)).coerceAtLeast(1))
            else -> current
        }
        regions.updateParticle(region.id, updated)
        val status = if (updated.enabled) text("value-enabled", "Enabled") else text("value-disabled", "Disabled")
        val feedback = when (parts[0]) {
            "toggle" -> message("particle-toggle", "Particles are now %state%.", arrayOf("state" to status))
            "prev", "next" -> message("particle-type-saved", "Particle type changed to %value%.", arrayOf("value" to updated.type))
            "count" -> message("particle-count-saved", "Particle count changed to %value%.", arrayOf("value" to updated.count))
            "interval" -> message("particle-interval-saved", "Spawn interval changed to %value% tick.", arrayOf("value" to updated.intervalTicks))
            else -> text("particle-saved", "Particle settings saved.")
        }
        sendMessage(player, "particle-updated", "&a%value% &8| &7Refresh to view the full page.", "value" to feedback)
    }

    private fun preview(player: Player, definition: RegionParticleDefinition) {
        val particle = BukkitCompatibility.resolveParticle(definition.type)
        if (particle == null) { sendMessage(player, "particle-unsupported", "&cThis server does not support particle: &f%value%", "value" to definition.type); return }
        player.spawnParticle(particle, player.location.clone().add(0.0, 1.0, 0.0), definition.count, definition.spreadX, definition.spreadY, definition.spreadZ, definition.speed)
        sendMessage(player, "particle-preview", "&aPreviewed particle: &f%value%", "value" to definition.type)
    }

    private fun sendMessage(player: Player, key: String, fallback: String, vararg replacements: Pair<String, Any?>) = send(player, key, fallback, replacements)
}
