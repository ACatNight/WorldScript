package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.PolygonPoint
import com.worldscript.foundation.model.RegionBounds
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionShape

object RegionGeometry {
    fun from(first: BlockPosition, second: BlockPosition): RegionBounds = RegionBounds(
        BlockPosition(minOf(first.x, second.x), minOf(first.y, second.y), minOf(first.z, second.z)),
        BlockPosition(maxOf(first.x, second.x), maxOf(first.y, second.y), maxOf(first.z, second.z)),
    )

    fun contains(bounds: RegionBounds, position: BlockPosition): Boolean =
        position.x in bounds.min.x..bounds.max.x &&
            position.y in bounds.min.y..bounds.max.y &&
            position.z in bounds.min.z..bounds.max.z

    fun contains(region: RegionDefinition, position: BlockPosition): Boolean {
        if (!contains(region.bounds, position)) return false
        return when (val shape = region.shape) {
            RegionShape.Cuboid -> true
            is RegionShape.Polygon -> containsPolygon(shape.points, position.x.toDouble(), position.z.toDouble())
        }
    }

    /** Ray casting with an explicit edge check so blocks on the border remain inside. */
    fun containsPolygon(points: List<PolygonPoint>, x: Double, z: Double): Boolean {
        if (points.size < 3) return false
        var inside = false
        var previous = points.last()
        points.forEach { current ->
            if (isOnSegment(previous, current, x, z)) return true
            val crosses = (current.z > z) != (previous.z > z) &&
                x < (previous.x - current.x).toDouble() * (z - current.z) /
                (previous.z - current.z).toDouble() + current.x
            if (crosses) inside = !inside
            previous = current
        }
        return inside
    }

    fun isValidPolygon(points: List<PolygonPoint>): Boolean =
        points.distinct().size >= 3 && signedAreaTwice(points) != 0L

    fun polygonBounds(points: List<PolygonPoint>, minY: Int, maxY: Int): RegionBounds? {
        if (!isValidPolygon(points)) return null
        return RegionBounds(
            BlockPosition(points.minOf { it.x }, minOf(minY, maxY), points.minOf { it.z }),
            BlockPosition(points.maxOf { it.x }, maxOf(minY, maxY), points.maxOf { it.z }),
        )
    }

    fun encloses(outer: RegionBounds, inner: RegionBounds): Boolean =
        contains(outer, inner.min) && contains(outer, inner.max)

    fun encloses(outer: RegionDefinition, inner: RegionDefinition): Boolean {
        if (inner.bounds.min.y < outer.bounds.min.y || inner.bounds.max.y > outer.bounds.max.y) return false
        val footprint = when (val shape = inner.shape) {
            RegionShape.Cuboid -> listOf(
                PolygonPoint(inner.bounds.min.x, inner.bounds.min.z),
                PolygonPoint(inner.bounds.min.x, inner.bounds.max.z),
                PolygonPoint(inner.bounds.max.x, inner.bounds.min.z),
                PolygonPoint(inner.bounds.max.x, inner.bounds.max.z),
            )
            is RegionShape.Polygon -> shape.points
        }
        return footprint.all { point ->
            contains(outer, BlockPosition(point.x, inner.bounds.min.y, point.z)) &&
                contains(outer, BlockPosition(point.x, inner.bounds.max.y, point.z))
        }
    }

    private fun signedAreaTwice(points: List<PolygonPoint>): Long = points.indices.sumOf { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        current.x.toLong() * next.z - next.x.toLong() * current.z
    }

    private fun isOnSegment(first: PolygonPoint, second: PolygonPoint, x: Double, z: Double): Boolean {
        val cross = (x - first.x) * (second.z - first.z) - (z - first.z) * (second.x - first.x)
        if (kotlin.math.abs(cross) > 1.0E-9) return false
        return x >= minOf(first.x, second.x) && x <= maxOf(first.x, second.x) &&
            z >= minOf(first.z, second.z) && z <= maxOf(first.z, second.z)
    }
}
