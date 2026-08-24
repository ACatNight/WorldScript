@file:Suppress("DEPRECATION")

package com.worldscript.command

import com.worldscript.foundation.Lang
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player

/**
 * Chat UI primitives shared by the region editor pages.
 *
 * This class deliberately has no region or persistence knowledge. Controllers
 * decide what a row means; the renderer only turns rows into readable,
 * clickable chat components. Keeping this boundary small makes new pages and
 * action editors use the same interaction language.
 */
internal class EditorRenderer(private val lang: Lang) {
    fun text(key: String, fallback: String): String =
        lang.textWithLocalFallback("editor-$key", fallback)

    fun message(key: String, fallback: String, vararg replacements: Pair<String, Any?>): String {
        var value = text(key, fallback)
        replacements.forEach { (name, replacement) ->
            value = value.replace("%$name%", replacement?.toString() ?: "")
        }
        return value
    }

    fun send(player: Player, key: String, fallback: String, vararg replacements: Pair<String, Any?>) {
        player.sendMessage(color(message(key, fallback, *replacements)))
    }

    fun group(player: Player, title: String) {
        spacer(player)
        // Keep groups unmistakable in a fast-moving chat log without adding a
        // full-width separator before every field. The title itself keeps its
        // semantic colour (gold, purple, red, etc.) from the caller.
        player.sendMessage(color(text("ui-group-prefix", "&8┌─ ") + title))
    }

    fun spacer(player: Player) {
        player.sendMessage("")
    }

    fun divider(player: Player) {
        player.sendMessage(color(text("ui-divider", "&8────────────────────────────────────────")))
    }

    fun property(
        player: Player,
        label: String,
        value: String,
        actionLabel: String,
        action: String? = null,
        extra: List<ChatEditorButton> = emptyList(),
    ) {
        val components = mutableListOf<BaseComponent>()
        // A stable label → value rhythm makes long editor pages skimmable.
        // Do not force a colour onto the value: callers may deliberately use
        // a semantic state colour such as enabled/disabled.
        components += TextComponent(color(
            text("ui-property-prefix", "&8│ ") +
                text("ui-property-label", "&7") + label +
                text("ui-property-separator", " &8› ") + value,
        ))
        if (action == null) {
            components += TextComponent(color(" &8$actionLabel"))
        } else {
            components += TextComponent(" ")
            components += button(actionLabel, message("hint-run-action", "&7Run: %action%", "action" to plain(actionLabel)), action).toList()
        }
        extra.forEach {
            components += TextComponent(color(text("ui-extra-separator", " &8| ")))
            components += button(it.label, it.hover, it.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    fun stepper(
        player: Player,
        label: String,
        value: String,
        decreaseLabel: String,
        decrease: String,
        increaseLabel: String,
        increase: String,
    ) {
        property(player, label, value, decreaseLabel, decrease, listOf(
            ChatEditorButton(increaseLabel, text("hint-increase-value", "&7Increase value"), increase),
        ))
    }

    fun operation(player: Player, label: String, hover: String, command: String) {
        player.spigot().sendMessage(*button(label, hover, command))
    }

    fun operationRow(player: Player, vararg buttons: ChatEditorButton) {
        val components = mutableListOf<BaseComponent>()
        buttons.forEachIndexed { index, button ->
            if (index > 0) components += TextComponent("  ")
            components += button(button.label, button.hover, button.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    fun button(label: String, hover: String, command: String): Array<BaseComponent> = ComponentBuilder(color(label))
        .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(color(hover))))
        .create()

    fun color(value: String): String = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', value)

    fun plain(value: String): String = net.md_5.bungee.api.ChatColor.stripColor(color(value)) ?: value
}
