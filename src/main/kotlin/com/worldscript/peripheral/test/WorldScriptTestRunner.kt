package com.worldscript.peripheral.test

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.modules.l1.region_core.RegionGeometry

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
        println("[TEST] SUMMARY region-core: passed=1 failed=0 total=1")
    }
}
