package com.worldscript

import com.worldscript.command.EditorInputParser
import com.worldscript.foundation.model.ConditionMode
import com.worldscript.foundation.model.ScriptDefinition
import com.worldscript.modules.l1.region_core.RegionGeometry
import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.PolygonPoint
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionShape
import com.worldscript.foundation.model.DiscoveryDefinition
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
    fun polygonGeometryIncludesBorderAndRejectsOutsideOrWrongHeight() {
        val points = listOf(PolygonPoint(0, 0), PolygonPoint(6, 0), PolygonPoint(0, 6))
        val region = RegionDefinition(
            id = "triangle",
            displayName = "Triangle",
            worldId = "world",
            worldName = "world",
            bounds = RegionGeometry.polygonBounds(points, 64, 70)!!,
            shape = RegionShape.Polygon(points),
        )

        assertTrue(RegionGeometry.contains(region, BlockPosition(1, 65, 1)))
        assertTrue(RegionGeometry.contains(region, BlockPosition(3, 65, 0)))
        assertTrue(!RegionGeometry.contains(region, BlockPosition(5, 65, 5)))
        assertTrue(!RegionGeometry.contains(region, BlockPosition(1, 71, 1)))
    }

    @Test
    fun polygonValidationRejectsDegenerateOutlines() {
        assertTrue(!RegionGeometry.isValidPolygon(listOf(PolygonPoint(0, 0), PolygonPoint(1, 1))))
        assertTrue(!RegionGeometry.isValidPolygon(listOf(PolygonPoint(0, 0), PolygonPoint(1, 1), PolygonPoint(2, 2))))
        assertTrue(RegionGeometry.isValidPolygon(listOf(PolygonPoint(0, 0), PolygonPoint(4, 0), PolygonPoint(0, 4))))
    }

    @Test
    fun conditionSwitchDefaultsOnForLegacyModel() {
        assertTrue(ScriptDefinition().conditionsEnabled)
        assertEquals(ConditionMode.AND, ScriptDefinition().conditionMode)
        assertTrue(DiscoveryDefinition().toastEnabled)
    }

    @Test
    fun inputParserSupportsPermissionAndComparison() {
        assertEquals("region.enter.mine", EditorInputParser.condition("permission: region.enter.mine")?.key)
        assertEquals("10", EditorInputParser.condition("%level% >= 10")?.value)
    }
}
