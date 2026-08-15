package com.worldscript.modules.l1.region_events

/** Prevents accidental and off-hand interaction script execution. */
object RegionInteractionPolicy {
    fun shouldDispatch(isMainHand: Boolean, isRightClickBlock: Boolean, isCancelled: Boolean): Boolean =
        isMainHand && isRightClickBlock && !isCancelled
}
