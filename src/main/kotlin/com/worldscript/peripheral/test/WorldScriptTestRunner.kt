package com.worldscript.peripheral.test

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionBounds
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.DiscoveryDefinition
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.command.EditorOperation
import com.worldscript.command.EditorActionRef
import com.worldscript.command.EditorRoute
import com.worldscript.modules.l1.region_core.RegionGeometry
import com.worldscript.modules.l1.region_core.RegionConfigurationValidator
import com.worldscript.modules.l1.region_events.RegionInteractionPolicy
import com.worldscript.command.EditorInputParser
import com.worldscript.modules.l2.rpg.PlayerRegionProgress
import com.worldscript.integration.placeholder.PlaceholderRequest
import com.worldscript.integration.placeholder.RegionNameFormatter

object WorldScriptTestRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[TEST] START region-core")
        val bounds = RegionGeometry.from(BlockPosition(10, 70, -4), BlockPosition(2, 64, 8))
        check(bounds.min == BlockPosition(2, 64, -4)) { "min boundary normalization failed" }
        check(bounds.max == BlockPosition(10, 70, 8)) { "max boundary normalization failed" }
        check(RegionGeometry.contains(bounds, BlockPosition(2, 64, -4))) { "min boundary should be included" }
        check(RegionGeometry.contains(bounds, BlockPosition(10, 70, 8))) { "max boundary should be included" }
        check(!RegionGeometry.contains(bounds, BlockPosition(11, 70, 8))) { "outside position should be excluded" }
        println("[TEST] PASS region-core.geometry: bounds normalization and containment")

        val progress = PlayerRegionProgress()
        progress.unlock("sunken_ruins")
        progress.markEntered("sunken_ruins")
        progress.markCompleted("sunken_ruins")
        check(progress.isUnlocked("SUNKEN_RUINS")) { "unlock state should be player-local and case-insensitive" }
        check(progress.hasEntered("sunken_ruins")) { "entered state should be tracked separately" }
        check(progress.isCompleted("sunken_ruins")) { "completion state should be tracked separately" }
        check(GlobalRegionStatus.parse("unlocked") == GlobalRegionStatus.OPEN) { "legacy global status should migrate to open" }
        println("[TEST] PASS rpg.progress: player progress and global status are separate")

        check(RegionInteractionPolicy.shouldDispatch(true, true, false)) { "main-hand right click should dispatch" }
        check(!RegionInteractionPolicy.shouldDispatch(false, true, false)) { "off-hand click must not dispatch" }
        check(!RegionInteractionPolicy.shouldDispatch(true, false, false)) { "left click must not dispatch" }
        check(!RegionInteractionPolicy.shouldDispatch(true, true, true)) { "cancelled click must not dispatch" }
        println("[TEST] PASS region-events.interact: only active main-hand right clicks dispatch")
        check(RegionInteractionPolicy.eventType("LEFT_CLICK_BLOCK") == com.worldscript.foundation.model.RegionEventType.LEFT_CLICK)
        check(RegionInteractionPolicy.eventType("RIGHT_CLICK_BLOCK") == com.worldscript.foundation.model.RegionEventType.RIGHT_CLICK)
        check(RegionInteractionPolicy.eventType("LEFT_CLICK_AIR") == com.worldscript.foundation.model.RegionEventType.INTERACT)
        check(RegionInteractionPolicy.eventType("RIGHT_CLICK_AIR") == com.worldscript.foundation.model.RegionEventType.INTERACT)
        check(RegionInteractionPolicy.eventType("PHYSICAL") == com.worldscript.foundation.model.RegionEventType.INTERACT)
        println("[TEST] PASS region-events.mapping: block clicks stay independent from generic interaction")

        check(EditorRoute.fromCommand("enter", "sound:1:play") == "sound:enter:1:play") { "command route should preserve the event key" }
        check(EditorRoute.mutation("particle:count:1")?.operation == EditorOperation.PARTICLE) { "particle mutation should be recognized" }
        check(EditorRoute.fromCommand("name", null) == "name:") { "display name editing route should be recognized" }
        check(EditorRoute.mutation("name:")?.operation == EditorOperation.NAME) { "display name mutation should be recognized" }
        check(EditorRoute.fromCommand("variable", "add") == "variable:add") { "variable add route should be recognized" }
        check(EditorRoute.mutation("variable:edit:level")?.operation == EditorOperation.VARIABLE) { "variable edit mutation should be recognized" }
        check(EditorRoute.fromCommand("conditions", "edit:2") == "condition:edit:2") { "condition edit route should preserve its index" }
        check(EditorRoute.fromCommand("conditions", "remove:2") == "condition:remove:2") { "condition removal route should preserve its index" }
        check(EditorRoute.fromCommand("conditions", "failure-edit:2") == "condition:failure-edit:2") { "condition failure edit route should preserve its index" }
        check(EditorRoute.fromCommand("conditions", "failure-remove:2") == "condition:failure-remove:2") { "condition failure removal route should preserve its index" }
        check(EditorRoute.fromCommand("conditions", "failure-add") == "condition:failure-add") { "condition failure add route should be recognized" }
        check(EditorRoute.mutation("events") == null) { "page names must not be treated as mutations" }
        println("[TEST] PASS editor.route: command and mutation routes are parsed consistently")

        val actionRef = EditorActionRef.parse("enter:2:volume-up")
        check(actionRef?.eventKey == "enter" && actionRef.index == 2 && actionRef.arguments == listOf("volume-up")) { "action reference should preserve operation arguments" }
        check(EditorActionRef.parse("enter:-1:value") == null) { "negative action indexes must be rejected" }
        check(EditorActionRef.parse("enter:value") == null) { "missing numeric action indexes must be rejected" }
        println("[TEST] PASS editor.action-ref: action targets are parsed consistently")

        val parent = RegionDefinition("parent", "Parent", "world", "world", RegionBounds(BlockPosition(0, 64, 0), BlockPosition(10, 80, 10)))
        val child = RegionDefinition("child", "Child", "world", "world", RegionBounds(BlockPosition(9, 64, 9), BlockPosition(20, 80, 20)), parentId = "parent")
        val regions = mapOf(parent.id to parent, child.id to child)
        val validationIssues = RegionConfigurationValidator({ id -> regions[id.lowercase()] }, { false }).validate(regions.values, emptyList())
        check(validationIssues.any { it.contains("bounds are not fully inside parent") }) { "child bounds outside the parent must be rejected" }
        val invalidActionRegion = parent.copy(
            discovery = DiscoveryDefinition(actions = listOf(ActionDefinition(ActionType.MESSAGE))),
            events = mapOf(
                com.worldscript.foundation.model.RegionEventType.ENTER to com.worldscript.foundation.model.ScriptDefinition(
                    conditionFailureActions = listOf(ActionDefinition(ActionType.CONSOLE_COMMAND)),
                ),
            ),
        )
        val actionValidationIssues = RegionConfigurationValidator({ id -> if (id.equals(parent.id, true)) parent else null }, { false })
            .validate(listOf(invalidActionRegion), emptyList())
        check(actionValidationIssues.any { it.contains("discovery.actions[0]: message is empty") }) { "discovery actions must be validated" }
        check(actionValidationIssues.any { it.contains("condition-failure.actions[0]: command is empty") }) { "condition failure actions must be validated" }
        println("[TEST] PASS region-validation: parent bounds are validated independently from storage")

        check(PlaceholderRequest.parse(" REGION_ID ") == PlaceholderRequest.Fixed("region_id")) { "fixed placeholders should be trimmed and normalized" }
        check(PlaceholderRequest.parse("var_short_name") == PlaceholderRequest.RegionVariable("short_name")) { "var_ prefix should resolve region variables" }
        check(PlaceholderRequest.parse("region_var_short_name") == PlaceholderRequest.RegionVariable("short_name")) { "region_var_ prefix should resolve region variables" }
        check(PlaceholderRequest.parse("parent_var_biome") == PlaceholderRequest.ParentVariable("biome")) { "parent_var_ prefix should resolve parent variables" }
        check(PlaceholderRequest.parse("parent_biome") == PlaceholderRequest.ParentVariable("biome")) { "parent_ prefix should resolve parent variables" }
        check(PlaceholderRequest.parse("child_short_name") == PlaceholderRequest.ChildVariable("short_name")) { "child_ prefix should resolve current child variables" }
        check(PlaceholderRequest.parse("short_name") == PlaceholderRequest.DynamicVariable("short_name")) { "unprefixed parameters should resolve dynamic region variables" }
        check(PlaceholderRequest.parse("REGION_NAME") == PlaceholderRequest.Fixed("region_name")) { "fixed placeholders must take precedence over dynamic variables" }
        check(PlaceholderRequest.parse("   ") == PlaceholderRequest.Unknown) { "blank parameters should be rejected" }
        check(RegionNameFormatter.format("{parent} / {current}", "低语森林", "森林入口", "forest_entrance", "低语森林 / 森林入口") == "低语森林 / 森林入口") { "custom region path format failed" }
        check(RegionNameFormatter.format("当前位置：{parent} / {current}", "", "初始山谷", "starter_valley", "初始山谷") == "当前位置：初始山谷") { "root region path should not keep an empty parent separator" }
        check(RegionNameFormatter.format("{path} · {id}", "低语森林", "森林入口", "forest_entrance", "低语森林 / 森林入口") == "低语森林 / 森林入口 · forest_entrance") { "path and id tokens failed" }
        println("[TEST] PASS placeholder.request: fixed and variable parameters are parsed consistently")
        check(EditorInputParser.condition("permission: region.enter.mine")?.key == "region.enter.mine")
        check(EditorInputParser.condition("%player_level% >= 10")?.value == "10")
        check(EditorInputParser.discoveryAction("/say Welcome")?.type == com.worldscript.foundation.model.ActionType.CONSOLE_COMMAND)
        check(EditorInputParser.discoveryAction("player-command=spawn")?.value == "spawn")
        ActionType.entries.forEach { type ->
            val yamlType = ActionType.yamlName(type)
            check(ActionType.parseYaml(yamlType) == type) { "YAML action type '$yamlType' must parse as $type" }
            check(ActionType.parseYaml(type.name) == type) { "enum action type '${type.name}' must parse as $type" }
        }
        check(ActionType.parseYaml("not-an-action") == null) { "unknown YAML action types must be rejected" }
        val legacyDiscovery = DiscoveryDefinition(rewardActions = listOf(ActionDefinition(ActionType.CONSOLE_COMMAND, "say legacy")))
        val migratedDiscovery = DiscoveryDefinition(actions = listOf(ActionDefinition(ActionType.MESSAGE, "Modern action")), rewardActions = legacyDiscovery.rewardActions)
        check(legacyDiscovery.configuredActions().single().value == "say legacy") { "legacy discovery actions must remain executable" }
        check(migratedDiscovery.configuredActions().single().type == ActionType.MESSAGE) { "canonical discovery actions must take precedence" }
        val migratedLegacyDiscovery = legacyDiscovery.canonicalized()
        check(migratedLegacyDiscovery.actions == legacyDiscovery.rewardActions && migratedLegacyDiscovery.rewardActions.isEmpty()) {
            "legacy discovery rewards must migrate to the canonical list on write"
        }
        val clearedLegacyDiscovery = migratedDiscovery.canonicalized().copy(actions = emptyList())
        check(clearedLegacyDiscovery.configuredActions().isEmpty()) {
            "deleted canonical discovery actions must not resurrect legacy rewards"
        }
        val mixedDiscovery = DiscoveryDefinition(
            titleEnabled = true,
            soundEnabled = true,
            actions = listOf(ActionDefinition(ActionType.CONSOLE_COMMAND, "say discovered")),
        )
        check(mixedDiscovery.titleEnabled && mixedDiscovery.soundEnabled && mixedDiscovery.configuredActions().single().type == ActionType.CONSOLE_COMMAND) {
            "legacy title and sound settings must coexist with canonical discovery actions"
        }
        println("[TEST] PASS editor.input-parser: conditions and discovery actions are parsed consistently")
        println("[TEST] SUMMARY region-core: passed=7 failed=0 total=7")
    }
}
