package com.worldscript.peripheral.test

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.command.EditorOperation
import com.worldscript.command.EditorRoute
import com.worldscript.modules.l1.region_core.RegionGeometry
import com.worldscript.modules.l1.region_events.RegionInteractionPolicy
import com.worldscript.modules.l2.rpg.PlayerRegionProgress

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
        check(EditorRoute.mutation("events") == null) { "page names must not be treated as mutations" }
        println("[TEST] PASS editor.route: command and mutation routes are parsed consistently")
        println("[TEST] SUMMARY region-core: passed=4 failed=0 total=4")
    }
}
