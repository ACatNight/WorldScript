@file:Suppress("DEPRECATION")

package com.worldscript.foundation

import net.md_5.bungee.api.ChatColor

/**
 * Shared legacy text formatting for chat, GUI, editor and temporary UI output.
 *
 * The plugin still supports Paper 1.12.2, so legacy color translation remains
 * the compatibility boundary. New display systems should call this class
 * instead of translating colors independently.
 */
object TextFormatter {
    fun color(value: String): String =
        ChatColor.translateAlternateColorCodes('&', value)

    fun template(value: String, vararg replacements: Pair<String, Any?>): String =
        replacements.fold(value) { text, (name, replacement) ->
            text.replace("%$name%", replacement?.toString() ?: "")
        }

    fun region(value: String, regionName: String): String =
        template(value, "region" to regionName)
}
