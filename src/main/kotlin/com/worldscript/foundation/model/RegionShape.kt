package com.worldscript.foundation.model

data class PolygonPoint(val x: Int, val z: Int)

sealed class RegionShape {
    object Cuboid : RegionShape()

    data class Polygon(val points: List<PolygonPoint>) : RegionShape()
}
