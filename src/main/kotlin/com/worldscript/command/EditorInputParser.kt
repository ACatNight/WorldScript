package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.ComparisonOperator
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionType
import java.util.Locale

/** Parses player-entered values before they reach the region services. */
internal object EditorInputParser {
    fun condition(raw: String): ConditionDefinition? {
        val input = raw.trim()
        val permission = Regex("^permission\\s*:\\s*(\\S+)$", RegexOption.IGNORE_CASE).matchEntire(input)
        if (permission != null) {
            return ConditionDefinition(ConditionType.PERMISSION, key = permission.groupValues[1])
        }
        val match = Regex("^(.+?)\\s*(>=|<=|!=|==|>|<)\\s*(.+)$").matchEntire(input) ?: return null
        val operator = when (match.groupValues[2]) {
            ">" -> ComparisonOperator.GREATER
            ">=" -> ComparisonOperator.GREATER_OR_EQUAL
            "<" -> ComparisonOperator.LESS
            "<=" -> ComparisonOperator.LESS_OR_EQUAL
            "!=" -> ComparisonOperator.NOT_EQUALS
            else -> ComparisonOperator.EQUALS
        }
        return ConditionDefinition(
            type = ConditionType.VARIABLE,
            key = match.groupValues[1].trim(),
            value = match.groupValues[3].trim(),
            operator = operator,
        )
    }

    fun discoveryAction(raw: String): ActionDefinition? {
        val input = raw.trim()
        if (input.isBlank()) return null
        val parts = when {
            '=' in input -> input.split('=', limit = 2)
            ':' in input -> input.split(':', limit = 2)
            else -> listOf("console-command", input)
        }
        if (parts.size != 2 || parts[1].isBlank()) return null
        val type = actionType(parts[0]) ?: return null
        return ActionDefinition(type = type, value = parts[1].trim())
    }

    fun isCancellation(value: String): Boolean =
        value.equals("cancel", true) || value.equals("取消", true)

    fun isConfirmation(value: String): Boolean =
        value.equals("confirm", true) || value.equals("确认", true)

    private fun actionType(raw: String): ActionType? = when (raw.trim().lowercase(Locale.ROOT)) {
        "console", "console-command", "server-command", "command", "cmd" -> ActionType.CONSOLE_COMMAND
        "player", "player-command" -> ActionType.PLAYER_COMMAND
        "message", "msg", "text" -> ActionType.MESSAGE
        "title", "text-display" -> ActionType.TEXT_DISPLAY
        "sound" -> ActionType.SOUND
        "kether", "script" -> ActionType.KETHER
        else -> ActionType.parseYaml(raw)
    }
}
