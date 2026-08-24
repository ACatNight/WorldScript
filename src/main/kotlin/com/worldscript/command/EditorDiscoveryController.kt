package com.worldscript.command

import com.worldscript.foundation.model.DiscoveryDefinition
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.entity.Player
import java.util.Locale

/** Owns discovery settings and first-discovery action editing routes. */
internal class EditorDiscoveryController(
    private val regions: RegionCoreServiceImpl,
    private val renderer: EditorRenderer,
    private val sessions: EditorSessionStore,
    private val open: (Player, String, String) -> Unit,
    private val enableGlobal: (String) -> Unit,
    private val actionLabel: (com.worldscript.foundation.model.ActionDefinition) -> String,
) {
    private fun text(key: String, fallback: String) = renderer.text(key, fallback)
    private fun send(player: Player, key: String, fallback: String, vararg replacements: Pair<String, Any?>) = renderer.send(player, key, fallback, *replacements)
    private fun enabled(value: Boolean) = if (value) text("value-enabled", "Enabled") else text("value-disabled", "Disabled")
    private fun toggle(value: Boolean) = if (value) text("button-close", "&c[Close]") else text("button-open", "&a[Open]")

    fun render(player: Player, region: RegionDefinition) {
        val discovery = region.discovery ?: regions.effective(region.id)?.discovery ?: DiscoveryDefinition()
        renderer.group(player, text("group-discovery", "&5Discovery"))
        renderer.property(player, text("label-discovery-enabled", "&eDiscovery"), enabled(discovery.enabled), toggle(discovery.enabled), "/ws edit ${region.id} discovery:toggle")
        renderer.property(player, text("label-discovery-toast", "&eToast"), enabled(discovery.toastEnabled), toggle(discovery.toastEnabled), "/ws edit ${region.id} discovery:toast")
        renderer.property(player, text("label-discovery-title", "&eTitle"), enabled(discovery.titleEnabled), toggle(discovery.titleEnabled), "/ws edit ${region.id} discovery:title")
        renderer.property(player, text("label-discovery-sound", "&eSound"), enabled(discovery.soundEnabled), toggle(discovery.soundEnabled), "/ws edit ${region.id} discovery:sound")
        renderer.property(player, text("label-discovery-reward", "&eReward"), enabled(discovery.rewardEnabled), toggle(discovery.rewardEnabled), "/ws edit ${region.id} discovery:reward")
        if (discovery.toastEnabled) {
            renderer.group(player, text("group-discovery-toast", "&6Toast feedback"))
            renderer.property(player, text("label-discovery-toast-title", "&7Toast title"), discovery.toastTitle.ifBlank { text("value-global-default", "Global default") }, text("button-input", "&e[Input]"), "/ws edit ${region.id} discovery:toast-title-input")
            renderer.property(player, text("label-discovery-toast-description", "&7Toast description"), discovery.toastDescription.ifBlank { text("value-global-default", "Global default") }, text("button-input", "&e[Input]"), "/ws edit ${region.id} discovery:toast-description-input")
            renderer.property(player, text("label-discovery-toast-icon", "&7Toast icon"), discovery.toastIcon.ifBlank { text("value-global-default", "Global default") }, text("button-use-held-item", "&b[Use held item]"), "/ws edit ${region.id} discovery:toast-held-item", listOf(
                ChatEditorButton(text("button-input", "&e[Input]"), text("hint-toast-icon-input", "&7Enter a Bukkit material name"), "/ws edit ${region.id} discovery:toast-icon-input"),
                ChatEditorButton(text("button-reset", "&c[Reset]"), text("hint-toast-reset", "&7Use the global default icon again"), "/ws edit ${region.id} discovery:toast-icon-reset"),
            ))
        }
        if (discovery.titleEnabled) {
            renderer.group(player, text("group-discovery-title", "&5Title feedback"))
            renderer.property(player, text("label-discovery-title-text", "&7Title text"), discovery.title.ifBlank { text("value-unset", "Not set") }, text("button-input", "&e[Input]"), "/ws edit ${region.id} discovery:title-input")
            renderer.property(player, text("label-discovery-subtitle", "&7Subtitle"), discovery.subtitle.ifBlank { text("value-unset", "Not set") }, text("button-input", "&e[Input]"), "/ws edit ${region.id} discovery:subtitle-input")
            renderer.stepper(player, text("label-discovery-fade-in", "&7Fade in"), "${discovery.fadeIn} tick", "&c[-5]", "/ws edit ${region.id} discovery:fade-in:-5", "&a[+5]", "/ws edit ${region.id} discovery:fade-in:5")
            renderer.stepper(player, text("label-discovery-stay", "&7Stay"), "${discovery.stay} tick", "&c[-10]", "/ws edit ${region.id} discovery:stay:-10", "&a[+10]", "/ws edit ${region.id} discovery:stay:10")
            renderer.stepper(player, text("label-discovery-fade-out", "&7Fade out"), "${discovery.fadeOut} tick", "&c[-5]", "/ws edit ${region.id} discovery:fade-out:-5", "&a[+5]", "/ws edit ${region.id} discovery:fade-out:5")
        }
        if (discovery.soundEnabled) {
            renderer.group(player, text("group-discovery-sound", "&3Sound feedback"))
            renderer.property(player, text("label-discovery-sound-type", "&7Sound type"), discovery.sound, text("button-input", "&e[Input]"), "/ws edit ${region.id} discovery:sound-input")
            renderer.stepper(player, text("label-discovery-volume", "&7Volume"), "%.1f".format(Locale.US, discovery.volume), "&c[-0.1]", "/ws edit ${region.id} discovery:volume:-0.1", "&a[+0.1]", "/ws edit ${region.id} discovery:volume:0.1")
            renderer.stepper(player, text("label-discovery-pitch", "&7Pitch"), "%.1f".format(Locale.US, discovery.pitch), "&c[-0.1]", "/ws edit ${region.id} discovery:pitch:-0.1", "&a[+0.1]", "/ws edit ${region.id} discovery:pitch:0.1")
        }
        if (discovery.rewardEnabled) {
            renderer.group(player, text("group-discovery-reward", "&6First discovery rewards"))
            if (discovery.configuredActions().isEmpty()) renderer.property(player, text("label-discovery-reward-actions", "&7Reward actions"), text("value-unset", "Not set"), "&8—")
            else discovery.configuredActions().forEachIndexed { index, action -> renderer.property(player, "&f${index + 1}.", actionLabel(action), text("button-edit", "&e[Edit]"), "/ws edit ${region.id} discovery action:$index") }
            renderer.operation(player, text("button-add-action", "&a[+ Add action]"), text("hint-add-action", "&7Add an action without replacing existing actions"), "/ws edit ${region.id} add:discovery")
        }
    }

    fun control(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':')
        when (parts.firstOrNull()) {
            "toggle", "toast", "title", "sound", "reward" -> {
                val current = regions.effective(region.id)?.discovery ?: DiscoveryDefinition()
                val enabled = when (parts[0]) {
                    "toggle" -> !current.enabled
                    "toast" -> !current.toastEnabled
                    "title" -> !current.titleEnabled
                    "sound" -> !current.soundEnabled
                    else -> !current.rewardEnabled
                }
                regions.updateDiscovery(region.id) { when (parts[0]) {
                    "toggle" -> it.copy(enabled = enabled)
                    "toast" -> it.copy(enabled = enabled || it.enabled, toastEnabled = enabled)
                    // A child feature is unusable while the region-level
                    // Discovery switch is off. Enable both in one saved
                    // update so the editor never shows a false-positive ON.
                    "title" -> it.copy(enabled = enabled || it.enabled, titleEnabled = enabled)
                    "sound" -> it.copy(enabled = enabled || it.enabled, soundEnabled = enabled)
                    else -> it.copy(enabled = enabled || it.enabled, rewardEnabled = enabled)
                } }
                if (enabled) {
                    // Child feedback cannot run until Discovery itself is globally
                    // enabled, so activating any child also activates the parent.
                    enableGlobal("discovery.enabled")
                    if (parts[0] == "toast") enableGlobal("discovery.display.toast.enabled")
                    else if (parts[0] != "toggle") enableGlobal("discovery.${parts[0]}.enabled")
                }
            }
            "title-input", "subtitle-input", "sound-input", "toast-title-input", "toast-description-input", "toast-icon-input" -> {
                val parameter = when (parts[0]) {
                    "title-input" -> "__discovery_title__"
                    "subtitle-input" -> "__discovery_subtitle__"
                    "sound-input" -> "__discovery_sound__"
                    "toast-title-input" -> "__discovery_toast_title__"
                    "toast-description-input" -> "__discovery_toast_description__"
                    else -> "__discovery_toast_icon__"
                }
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "discovery", RegionEventType.ENTER, -1, parameter, System.currentTimeMillis()))
                send(player, "discovery-input-prompt", "&6Enter the discovery value, or type &ccancel&7.")
                return
            }
            "toast-held-item" -> {
                val item = player.inventory.itemInMainHand
                if (item.type == org.bukkit.Material.AIR) send(player, "toast-icon-held-empty", "&cHold an item in your main hand first.")
                else {
                    activateToastDiscovery(region.id) { it.copy(toastIcon = item.type.name) }
                    send(player, "toast-icon-saved", "&aToast icon saved: &f%value%", "value" to item.type.name)
                }
            }
            "toast-icon-reset" -> {
                activateToastDiscovery(region.id) { it.copy(toastIcon = "") }
                send(player, "toast-icon-reset", "&eToast icon now uses the global default.")
            }
            "fade-in", "stay", "fade-out" -> {
                val delta = parts.getOrNull(1)?.toIntOrNull() ?: return
                regions.updateDiscovery(region.id) { when (parts[0]) { "fade-in" -> it.copy(fadeIn = (it.fadeIn + delta).coerceAtLeast(0)); "stay" -> it.copy(stay = (it.stay + delta).coerceAtLeast(0)); else -> it.copy(fadeOut = (it.fadeOut + delta).coerceAtLeast(0)) } }
            }
            "volume", "pitch" -> {
                val delta = parts.getOrNull(1)?.toDoubleOrNull() ?: return
                regions.updateDiscovery(region.id) { val next = ((if (parts[0] == "volume") it.volume else it.pitch) + delta).coerceAtLeast(0.0).toFloat(); if (parts[0] == "volume") it.copy(volume = next) else it.copy(pitch = next) }
            }
        }
        open(player, region.id, "discovery")
    }

    /** Saving a Toast value must also make its discovery pipeline executable. */
    private fun activateToastDiscovery(id: String, update: (DiscoveryDefinition) -> DiscoveryDefinition) {
        regions.updateDiscovery(id) { update(it.copy(enabled = true, toastEnabled = true)) }
        enableGlobal("discovery.enabled")
        enableGlobal("discovery.display.toast.enabled")
    }
}
