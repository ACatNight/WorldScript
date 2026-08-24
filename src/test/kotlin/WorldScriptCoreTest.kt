package com.worldscript

import com.worldscript.command.EditorInputParser
import com.worldscript.foundation.model.ConditionMode
import com.worldscript.foundation.model.ScriptDefinition
import com.worldscript.modules.l1.region_core.RegionGeometry
import com.worldscript.foundation.model.BlockPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldScriptCoreTest {
    @Test
    fun geometryIncludesNormalizedBounds() {
        val bounds = RegionGeometry.from(BlockPosition(10, 70, -4), BlockPosition(2, 64, 8))
        assertTrue(RegionGeometry.contains(bounds, BlockPosition(2, 64, -4)))
        assertEquals(BlockPosition(10, 70, 8), bounds.max)
    }

    @Test
    fun conditionSwitchDefaultsOnForLegacyModel() {
        assertTrue(ScriptDefinition().conditionsEnabled)
        assertEquals(ConditionMode.AND, ScriptDefinition().conditionMode)
    }

    @Test
    fun inputParserSupportsPermissionAndComparison() {
        assertEquals("region.enter.mine", EditorInputParser.condition("permission: region.enter.mine")?.key)
        assertEquals("10", EditorInputParser.condition("%level% >= 10")?.value)
    }
}
