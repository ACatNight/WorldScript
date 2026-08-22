package com.worldscript.peripheral.test

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionBounds
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.command.EditorOperation
import com.worldscript.command.EditorActionRef
import com.worldscript.command.EditorRoute
import com.worldscript.modules.l1.region_core.RegionGeometry
import com.worldscript.modules.l1.region_core.RegionConfigurationValidator
import com.worldscript.modules.l1.region_events.RegionInteractionPolicy
import com.worldscript.modules.l2.rpg.PlayerRegionProgress
import com.worldscript.integration.placeholder.PlaceholderRequest

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

        check(EditorRoute.fromCommand("enter", "sound:1:play") == "sound:enter:1:play") { "command route should preserve the event key" }
        check(EditorRoute.mutation("particle:count:1")?.operation == EditorOperation.PARTICLE) { "particle mutation should be recognized" }
        check(EditorRoute.fromCommand("name", null) == "name:") { "display name editing route should be recognized" }
        check(EditorRoute.mutation("name:")?.operation == EditorOperation.NAME) { "display name mutation should be recognized" }
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
        println("[TEST] PASS region-validation: parent bounds are validated independently from storage")

        check(PlaceholderRequest.parse(" REGION_ID ") == PlaceholderRequest.Fixed("region_id")) { "fixed placeholders should be trimmed and normalized" }
        check(PlaceholderRequest.parse("var_short_name") == PlaceholderRequest.RegionVariable("short_name")) { "var_ prefix should resolve region variables" }
        check(PlaceholderRequest.parse("region_var_short_name") == PlaceholderRequest.RegionVariable("short_name")) { "region_var_ prefix should resolve region variables" }
        check(PlaceholderRequest.parse("parent_var_biome") == PlaceholderRequest.ParentVariable("biome")) { "parent_var_ prefix should resolve parent variables" }
        check(PlaceholderRequest.parse("short_name") == PlaceholderRequest.DynamicVariable("short_name")) { "unprefixed parameters should resolve dynamic region variables" }
        check(PlaceholderRequest.parse("REGION_NAME") == PlaceholderRequest.Fixed("region_name")) { "fixed placeholders must take precedence over dynamic variables" }
        check(PlaceholderRequest.parse("   ") == PlaceholderRequest.Unknown) { "blank parameters should be rejected" }
        println("[TEST] PASS placeholder.request: fixed and variable parameters are parsed consistently")
        println("[TEST] SUMMARY region-core: passed=7 failed=0 total=7")
    }
}
