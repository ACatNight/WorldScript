package com.worldscript.peripheral.test

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.modules.l1.region_core.RegionGeometry
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
        println("[TEST] SUMMARY region-core: passed=2 failed=0 total=2")
    }
}
