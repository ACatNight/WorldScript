package com.worldscript.command

import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l3.spawn.IntRangeValue
import com.worldscript.modules.l3.spawn.SpawnRule
import com.worldscript.modules.l3.spawn.SpawnService
import org.bukkit.entity.Player
import java.util.Locale

internal class SpawnEditorController(
    private val spawn: SpawnService,
    private val renderer: EditorRenderer,
    private val sessions: EditorSessionStore,
    private val open: (Player, String, String) -> Unit,
    private val openMobSelector: (Player, String, String?) -> Unit,
) {
    fun render(player: Player, region: RegionDefinition) {
        val rules = spawn.rules(region.id)
        renderer.group(player, renderer.text("group-spawn", "&cRegion spawns"))
        renderer.property(player, renderer.text("label-spawn-state", "&e[Spawn state]"), renderer.text("value-enabled", "Enabled"), "&8—")
        renderer.property(player, renderer.text("label-spawn-rule-count", "&7Rule count"), "${rules.size}", renderer.text("refresh", "&7[Refresh]"), "/ws edit ${region.id} spawn")
        renderer.property(player, renderer.text("label-spawn-mode", "&7Spawn mode"), renderer.text("spawn-mode-random", "Random in region"), "&8—")

        renderer.group(player, renderer.text("group-spawn-rules", "&6Spawn rules"))
        if (rules.isEmpty()) {
            renderer.property(player, renderer.text("label-actions", "&8Rules"), renderer.text("spawn-empty-rules", "Not configured"), "&8—")
        } else {
            rules.forEach { rule ->
                renderer.property(
                    player,
                    "&f[${rule.id}]",
                    ruleSummary(rule),
                    renderer.text("button-edit", "&e[Edit]"),
                    "/ws edit ${region.id} spawn-rule:${rule.id}",
                    listOf(
                        ChatEditorButton(renderer.text("button-preview", "&d[Test]"), renderer.text("spawn-hint-test", "&7Spawn once for testing"), "/ws edit ${region.id} spawn test:${rule.id}"),
                        ChatEditorButton(if (rule.enabled) renderer.text("button-close", "&c[Close]") else renderer.text("button-open", "&a[Open]"), renderer.text("spawn-hint-toggle", "&7Toggle this spawn rule"), "/ws edit ${region.id} spawn toggle:${rule.id}"),
                    ),
                )
            }
        }

        renderer.group(player, renderer.text("group-operations", "&7Operations"))
        renderer.operationRow(
            player,
            ChatEditorButton(renderer.text("spawn-button-create-select", "&a[+ Select mob]"), renderer.text("spawn-hint-create-select", "&7Open mob selector and create a rule"), "/ws edit ${region.id} spawn select"),
            ChatEditorButton(renderer.text("spawn-button-manual-create", "&e[Manual create]"), renderer.text("spawn-hint-manual-create", "&7Enter: rule_id mob_id 1-10"), "/ws edit ${region.id} spawn manual"),
            ChatEditorButton(renderer.text("spawn-button-reload", "&b[Reload spawns]"), renderer.text("spawn-hint-reload", "&7Reload spawn module config"), "/ws edit ${region.id} spawn reload"),
        )
    }

    fun renderRule(player: Player, region: RegionDefinition, ruleId: String) {
        val rule = spawn.rules().firstOrNull { it.id.equals(ruleId, true) && it.regionId.equals(region.id, true) }
            ?: return open(player, region.id, "spawn")
        renderer.group(player, renderer.text("group-spawn-profile", "&6Spawn profile"))
        renderer.property(player, renderer.text("label-spawn-rule-id", "&7Rule ID"), rule.id, "&8—")
        renderer.property(player, renderer.text("label-spawn-region", "&7Bound region"), rule.regionId, "&8—")
        renderer.property(player, renderer.text("label-spawn-mob", "&bMob"), rule.mobId, renderer.text("spawn-button-reselect-mob", "&e[Reselect]"), "/ws edit ${region.id} spawn select:${rule.id}")
        renderer.property(player, renderer.text("label-spawn-provider", "&7Provider"), rule.provider.name.lowercase(Locale.ROOT), "&8—")
        renderer.property(player, renderer.text("label-enabled", "&e[Enabled]"), if (rule.enabled) renderer.text("value-enabled", "Enabled") else renderer.text("value-disabled", "Disabled"), if (rule.enabled) renderer.text("button-close", "&c[Close]") else renderer.text("button-open", "&a[Open]"), "/ws edit ${region.id} spawn toggle:${rule.id}")

        renderer.group(player, renderer.text("group-spawn-trigger", "&cTrigger conditions"))
        renderer.property(player, renderer.text("label-spawn-inside", "&7Player inside region"), yesNo(rule.players.requireInsideRegion), renderer.text("button-cycle", "&e[Toggle]"), "/ws edit ${region.id} spawn inside:${rule.id}")
        renderer.property(player, renderer.text("label-spawn-nearby", "&7Nearby player"), "${yesNo(rule.players.requireNearby)} &8· &f${rule.players.nearbyRadius.toInt()}m", renderer.text("button-input", "&e[Input]"), "/ws edit ${region.id} spawn input:${rule.id}:nearby")
        renderer.property(player, renderer.text("label-spawn-min-distance", "&7Too-close protection"), "${rule.players.minDistance.toInt()}m", renderer.text("button-input", "&e[Input]"), "/ws edit ${region.id} spawn input:${rule.id}:min-distance")

        renderer.group(player, renderer.text("group-spawn-random", "&dRandom spawn"))
        renderer.property(player, renderer.text("label-spawn-amount", "&7Amount"), "${rule.amount.display()}${renderer.text("spawn-unit-mobs", " mobs")}", renderer.text("button-input", "&e[Input]"), "/ws edit ${region.id} spawn input:${rule.id}:amount")
        renderer.property(player, renderer.text("label-spawn-interval", "&7Interval"), "${rule.intervalSeconds.display("s")}", renderer.text("button-input", "&e[Input]"), "/ws edit ${region.id} spawn input:${rule.id}:interval")
        renderer.property(player, renderer.text("label-spawn-location", "&7Location"), renderer.text("spawn-mode-random", "Random in region"), "&8—")

        renderer.group(player, renderer.text("group-spawn-safety", "&aSafety"))
        renderer.property(player, renderer.text("label-spawn-max-alive", "&7Max alive"), "${rule.limits.maxAlive}", renderer.text("button-input", "&e[Input]"), "/ws edit ${region.id} spawn input:${rule.id}:max-alive")
        renderer.property(player, renderer.text("label-spawn-safe-location", "&7Safe location"), yesNo(rule.safety.enabled), renderer.text("button-cycle", "&e[Toggle]"), "/ws edit ${region.id} spawn safety:${rule.id}")
        renderer.property(player, renderer.text("label-spawn-cleanup", "&7Empty cleanup"), "${yesNo(rule.cleanup.despawnWhenEmpty)} &8· &f${rule.cleanup.delaySeconds}s", renderer.text("button-input", "&e[Input]"), "/ws edit ${region.id} spawn input:${rule.id}:cleanup")

        renderer.group(player, renderer.text("group-operations", "&7Operations"))
        renderer.operationRow(
            player,
            ChatEditorButton(renderer.text("button-preview", "&d[Test]"), renderer.text("spawn-hint-test", "&7Spawn once for testing"), "/ws edit ${region.id} spawn test:${rule.id}"),
            ChatEditorButton(renderer.text("spawn-button-copy", "&b[Copy]"), renderer.text("spawn-hint-copy", "&7Reserved for later"), "/ws edit ${region.id} spawn"),
            ChatEditorButton(renderer.text("spawn-button-delete", "&c[Delete rule]"), renderer.text("spawn-hint-delete", "&cDelete this spawn rule"), "/ws edit ${region.id} spawn delete:${rule.id}"),
        )
    }

    fun control(player: Player, region: RegionDefinition, payload: String) {
        val parts = payload.split(':')
        when (parts.firstOrNull()?.lowercase(Locale.ROOT)) {
            "rule" -> parts.getOrNull(1)?.let { open(player, region.id, "spawn-rule:$it") }
            "select" -> openMobSelector(player, region.id, parts.getOrNull(1))
            "manual" -> {
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "spawn", RegionEventType.ENTER, -1, "__spawn_manual__", System.currentTimeMillis()))
                renderer.send(player, "spawn-manual-prompt", "&6Create spawn &8| &7Enter &frule_id mob_id 1-10&7, or type &ccancel&7.")
            }
            "input" -> {
                val ruleId = parts.getOrNull(1) ?: return
                val field = parts.getOrNull(2) ?: return
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "spawn:$ruleId", RegionEventType.ENTER, -1, "__spawn_$field", System.currentTimeMillis()))
                renderer.send(player, "spawn-input-prompt", "&6Editing &f%parameter% &8| &7Enter a value, or type &ccancel&7.", "parameter" to fieldLabel(field))
            }
            "toggle" -> parts.getOrNull(1)?.let { ruleId ->
                spawn.toggle(ruleId)
                renderer.send(player, "spawn-rule-toggled", "&aSpawn rule updated: &f%rule%", "rule" to ruleId)
                open(player, region.id, "spawn")
            }
            "inside" -> parts.getOrNull(1)?.let { ruleId ->
                spawn.toggleInside(ruleId)
                open(player, region.id, "spawn-rule:$ruleId")
            }
            "nearby-toggle" -> parts.getOrNull(1)?.let { ruleId ->
                spawn.toggleNearby(ruleId)
                open(player, region.id, "spawn-rule:$ruleId")
            }
            "safety" -> parts.getOrNull(1)?.let { ruleId ->
                spawn.toggleSafety(ruleId)
                open(player, region.id, "spawn-rule:$ruleId")
            }
            "cleanup-toggle" -> parts.getOrNull(1)?.let { ruleId ->
                spawn.toggleCleanup(ruleId)
                open(player, region.id, "spawn-rule:$ruleId")
            }
            "delete" -> parts.getOrNull(1)?.let { ruleId ->
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "spawn:$ruleId", RegionEventType.ENTER, -1, "__spawn_delete__", System.currentTimeMillis()))
                renderer.send(player, "spawn-delete-confirm", "&cDelete spawn rule %rule%? &7Type &fconfirm &7in chat. Anything else cancels.", "rule" to ruleId)
            }
            "test" -> parts.getOrNull(1)?.let { ruleId ->
                spawn.test(player, ruleId)
                open(player, region.id, "spawn-rule:$ruleId")
            }
            "reload" -> {
                spawn.reload()
                renderer.send(player, "spawn-reloaded", "&aSpawn config reloaded.")
                open(player, region.id, "spawn")
            }
        }
    }

    fun handleInput(player: Player, pending: EditorPendingInput, message: String): Boolean {
        if (pending.parameter == "__spawn_manual__") {
            val parts = message.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < 2) {
                renderer.send(player, "spawn-manual-invalid", "&cUse: rule_id mob_id 1-10")
                open(player, pending.regionId, "spawn")
                return true
            }
            val requestedId = parts[0]
            val mobId = parts[1]
            val created = spawn.createRule(pending.regionId, requestedId, mobId, com.worldscript.modules.l3.spawn.SpawnProvider.AUTO)
            parseRange(parts.getOrNull(2) ?: "")?.let { spawn.updateAmount(created.id, it) }
            renderer.send(player, "spawn-rule-created", "&aCreated spawn rule &f%rule% &7for &f%mob%", "rule" to created.id, "mob" to mobId)
            open(player, pending.regionId, "spawn-rule:${created.id}")
            return true
        }
        val ruleId = pending.eventKey.removePrefix("spawn:").takeIf { it.isNotBlank() } ?: return false
        when (pending.parameter) {
            "__spawn_amount" -> parseRange(message)?.let { spawn.updateAmount(ruleId, it) } ?: renderer.send(player, "spawn-range-invalid", "&cInvalid range. Example: 1-10")
            "__spawn_interval" -> parseRange(message)?.let { spawn.updateInterval(ruleId, it) } ?: renderer.send(player, "spawn-range-invalid", "&cInvalid range. Example: 30-90")
            "__spawn_nearby" -> message.toDoubleOrNull()?.let { spawn.updateNearbyRadius(ruleId, it) } ?: renderer.send(player, "spawn-number-invalid", "&cPlease enter a number.")
            "__spawn_min-distance" -> message.toDoubleOrNull()?.let { spawn.updateMinDistance(ruleId, it) } ?: renderer.send(player, "spawn-number-invalid", "&cPlease enter a number.")
            "__spawn_max-alive" -> message.toIntOrNull()?.let { spawn.updateMaxAlive(ruleId, it) } ?: renderer.send(player, "spawn-number-invalid", "&cPlease enter a number.")
            "__spawn_cleanup" -> message.toLongOrNull()?.let { spawn.updateCleanupDelay(ruleId, it) } ?: renderer.send(player, "spawn-number-invalid", "&cPlease enter a number.")
            "__spawn_delete__" -> {
                if (EditorInputParser.isConfirmation(message)) {
                    spawn.delete(ruleId)
                    renderer.send(player, "spawn-rule-deleted", "&aSpawn rule deleted: &f%rule%", "rule" to ruleId)
                    open(player, pending.regionId, "spawn")
                    return true
                }
                renderer.send(player, "delete-cancelled", "&7Deletion cancelled.")
                open(player, pending.regionId, "spawn-rule:$ruleId")
                return true
            }
            else -> return false
        }
        renderer.send(player, "spawn-rule-saved", "&aSpawn rule saved: &f%rule%", "rule" to ruleId)
        open(player, pending.regionId, "spawn-rule:$ruleId")
        return true
    }

    private fun ruleSummary(rule: SpawnRule): String =
        "${rule.mobId} &8· &f${rule.amount.display()} &8· &f${rule.intervalSeconds.display("s")} &8· &fmax ${rule.limits.maxAlive}"

    private fun yesNo(value: Boolean): String =
        if (value) renderer.text("value-enabled", "Enabled") else renderer.text("value-disabled", "Disabled")

    private fun fieldLabel(field: String): String = renderer.text("spawn-parameter-$field", field)

    private fun parseRange(raw: String): IntRangeValue? {
        val text = raw.trim().replace('~', '-')
        val parts = text.split('-', limit = 2).map { it.trim() }
        val first = parts.firstOrNull()?.toIntOrNull() ?: return null
        val second = parts.getOrNull(1)?.toIntOrNull() ?: first
        return IntRangeValue(first, second)
    }
}
