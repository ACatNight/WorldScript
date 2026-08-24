package com.worldscript.foundation.model

data class PolygonPoint(val x: Int, val z: Int)

sealed class RegionShape {
    object Cuboid : RegionShape()

    /**
     * [cuboidBounds] is retained so converting a region back to a cuboid
     * restores the exact range it had before polygon editing began.
     */
    data class Polygon(
        val points: List<PolygonPoint>,
        val cuboidBounds: RegionBounds? = null,
    ) : RegionShape()
}
