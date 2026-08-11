package com.worldscript.modules.l1.region_core

import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.RegionBounds

object RegionGeometry {
    fun from(first: BlockPosition, second: BlockPosition): RegionBounds = RegionBounds(
        BlockPosition(minOf(first.x, second.x), minOf(first.y, second.y), minOf(first.z, second.z)),
        BlockPosition(maxOf(first.x, second.x), maxOf(first.y, second.y), maxOf(first.z, second.z)),
    )

    fun contains(bounds: RegionBounds, position: BlockPosition): Boolean =
        position.x in bounds.min.x..bounds.max.x &&
            position.y in bounds.min.y..bounds.max.y &&
            position.z in bounds.min.z..bounds.max.z
}
