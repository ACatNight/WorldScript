package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionMode
import com.worldscript.foundation.model.ConditionType
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.ScriptDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.entity.Player
import java.util.Locale

/** Owns the entry-condition page and its chat-input routes. */
internal class EditorConditionController(
    private val regions: RegionCoreServiceImpl,
    private val renderer: EditorRenderer,
    private val sessions: EditorSessionStore,
    private val open: (Player, String, String) -> Unit,
    private val enableGlobal: (String) -> Unit,
    private val actionLabel: (ActionDefinition) -> String,
) {
    private fun text(key: String, fallback: String) = renderer.text(key, fallback)
    private fun send(player: Player, key: String, fallback: String, vararg replacements: Pair<String, Any?>) = renderer.send(player, key, fallback, *replacements)

    fun render(player: Player, region: RegionDefinition) {
        val script = regions.effective(region.id)?.events?.get(RegionEventType.ENTER) ?: ScriptDefinition()
        renderer.group(player, text("group-conditions", "&cConditions"))
        renderer.property(
            player,
            text("label-conditions-enabled", "&eEntry conditions"),
            if (script.conditionsEnabled) text("value-enabled", "Enabled") else text("value-disabled", "Disabled"),
            if (script.conditionsEnabled) text("button-close", "&c[Close]") else text("button-open", "&a[Open]"),
            "/ws edit ${region.id} conditions toggle",
        )
        renderer.property(
            player,
            text("label-condition-mode", "&eCombination"),
            if (script.conditionMode == ConditionMode.AND) text("value-condition-all", "&aALL") else text("value-condition-any", "&aANY"),
            text("button-cycle", "&e[Switch]"),
            "/ws edit ${region.id} conditions mode",
        )
        if (script.conditions.isEmpty()) {
            renderer.property(player, text("label-condition-list", "&7Conditions"), text("value-unset", "Not configured"), "&8—")
        } else {
            script.conditions.forEachIndexed { index, condition ->
                renderer.property(
                    player, "&f${index + 1}. ${typeLabel(condition)}", expression(condition), text("button-edit", "&e[Edit]"),
                    "/ws edit ${region.id} conditions edit:$index",
                    listOf(ChatEditorButton(text("button-delete", "&c[Delete]"), text("hint-delete-condition", "&cRemove this entry"), "/ws edit ${region.id} conditions remove:$index")),
                )
            }
        }
        renderer.operation(player, text("button-add", "&a[Add condition]"), text("hint-add-condition", "&7Enter an expression in chat"), "/ws edit ${region.id} conditions add")
        renderer.group(player, text("group-condition-failure", "&cFailure feedback"))
        renderer.property(player, text("label-condition-failure-actions", "&7Failure actions"), "${script.conditionFailureActions.size}", text("button-add", "&a[Add]"), "/ws edit ${region.id} conditions failure-add")
        script.conditionFailureActions.forEachIndexed { index, action ->
            renderer.property(
                player,
                "&f[${index + 1}]",
                actionLabel(action),
                text("button-edit", "&e[Edit]"),
                "/ws edit ${region.id} conditions failure-edit:$index",
                listOf(ChatEditorButton(text("button-delete", "&c[Delete]"), text("hint-delete-action", "&cDelete this action"), "/ws edit ${region.id} conditions failure-remove:$index")),
            )
        }
    }

    fun control(player: Player, region: RegionDefinition, value: String) {
        when {
            value == "toggle" -> {
                val enabled = !(regions.effective(region.id)?.events?.get(RegionEventType.ENTER)?.conditionsEnabled ?: false)
                regions.updateEvent(region.id, RegionEventType.ENTER) { it.copy(conditionsEnabled = enabled) }
                if (enabled) enableGlobal("conditions.enabled")
                open(player, region.id, "conditions")
            }
            value == "add" -> {
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "conditions", RegionEventType.ENTER, -1, "__condition_add__", System.currentTimeMillis()))
                send(player, "condition-prompt", "&6Enter a condition expression, or permission: node. Type &ccancel&7 to stop.")
            }
            value == "mode" -> {
                regions.updateEvent(region.id, RegionEventType.ENTER) { it.copy(conditionMode = if (it.conditionMode == ConditionMode.AND) ConditionMode.OR else ConditionMode.AND) }
                open(player, region.id, "conditions")
            }
            value == "failure-add" -> {
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "conditions", RegionEventType.ENTER, -1, "__condition_failure_action_add__", System.currentTimeMillis()))
                send(player, "condition-failure-action-prompt", "&6Enter type=value, for example message=Access denied. Type &ccancel&7 to stop.")
            }
            value.startsWith("failure-remove:") -> {
                val index = value.removePrefix("failure-remove:").toIntOrNull() ?: return
                val action = regions.effective(region.id)?.events?.get(RegionEventType.ENTER)?.conditionFailureActions?.getOrNull(index) ?: return
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "conditions", RegionEventType.ENTER, index, "__condition_failure_action_delete__", System.currentTimeMillis()))
                send(player, "condition-failure-action-delete-confirm", "&cRemove failure action &f%value%&c? Type &fconfirm&c to continue.", "value" to actionLabel(action))
            }
            value.startsWith("failure-edit:") -> {
                val index = value.removePrefix("failure-edit:").toIntOrNull() ?: return
                val action = regions.effective(region.id)?.events?.get(RegionEventType.ENTER)?.conditionFailureActions?.getOrNull(index) ?: return
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "conditions", RegionEventType.ENTER, index, "__condition_failure_action_edit__", System.currentTimeMillis()))
                send(player, "condition-failure-action-edit-prompt", "&6Current: &f%value%&7. Enter type=value, or type &ccancel&7.", "value" to action.value)
            }
            else -> {
                val parts = value.split(':', limit = 2)
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                val condition = regions.effective(region.id)?.events?.get(RegionEventType.ENTER)?.conditions?.getOrNull(index) ?: return
                when (parts.firstOrNull()) {
                    "remove" -> {
                        sessions.begin(player.uniqueId, EditorPendingInput(region.id, "conditions", RegionEventType.ENTER, index, "__condition_delete__", System.currentTimeMillis()))
                        send(player, "condition-delete-confirm", "&cRemove condition &f%value%&c? Type &fconfirm&c to continue.", "value" to label(condition))
                    }
                    "edit" -> {
                        sessions.begin(player.uniqueId, EditorPendingInput(region.id, "conditions", RegionEventType.ENTER, index, "__condition_edit__", System.currentTimeMillis()))
                        send(player, "condition-edit-prompt", "&6Current: &f%value%&7. Enter a replacement expression, or type &ccancel&7.", "value" to label(condition))
                    }
                }
            }
        }
    }

    fun label(condition: ConditionDefinition): String = if (condition.type == ConditionType.PERMISSION) {
        "permission: ${condition.key}"
    } else {
        expression(condition)
    }

    private fun typeLabel(condition: ConditionDefinition): String = when (condition.type) {
        ConditionType.PERMISSION -> text("condition-type-permission", "&bPermission")
        ConditionType.VARIABLE -> if (condition.key.contains('%')) {
            text("condition-type-placeholder", "&bPlaceholder")
        } else {
            text("condition-type-variable", "&bVariable")
        }
        ConditionType.ITEM -> text("condition-type-item", "&bItem")
        ConditionType.REGION_STATUS -> text("condition-type-region-status", "&bRegion status")
        ConditionType.PLAYER_REGION_STATUS -> text("condition-type-player-region-status", "&bPlayer region status")
        ConditionType.PLAYER_LEVEL -> text("condition-type-player-level", "&eLegacy level")
    }

    private fun expression(condition: ConditionDefinition): String = if (condition.type == ConditionType.PERMISSION) {
        "&f${condition.key}"
    } else {
        "&f${condition.key} &7${operatorSymbol(condition.operator.name)} &f${condition.value}"
    }

    private fun operatorSymbol(operator: String): String = when (operator.uppercase(Locale.ROOT)) {
        "GREATER", "GREATER_THAN" -> ">"
        "GREATER_OR_EQUAL" -> ">="
        "LESS", "LESS_THAN" -> "<"
        "LESS_OR_EQUAL" -> "<="
        "EQUALS" -> "=="
        "NOT_EQUALS" -> "!="
        else -> operator.lowercase(Locale.ROOT)
    }
}
