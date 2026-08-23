package com.worldscript.modules.l1.region_events

import com.worldscript.foundation.model.RegionEventType
import org.bukkit.event.block.Action

/** Prevents accidental and off-hand interaction script execution. */
object RegionInteractionPolicy {
    fun shouldDispatch(isMainHand: Boolean, isCancelled: Boolean): Boolean = isMainHand && !isCancelled

    /** Compatibility overload for the built-in protocol runner. */
    fun shouldDispatch(isMainHand: Boolean, isRightClickBlock: Boolean, isCancelled: Boolean): Boolean =
        isMainHand && isRightClickBlock && !isCancelled

    /**
     * Keeps block-click events separate from the generic interaction event.
     * A block click must never silently fall back to INTERACT when its own
     * event is disabled.
     */
    fun eventType(action: Action): RegionEventType? = eventType(action.name)

    /** String form keeps pure protocol tests independent from the Paper runtime. */
    fun eventType(action: String): RegionEventType? = when (action.uppercase()) {
        "LEFT_CLICK_BLOCK" -> RegionEventType.LEFT_CLICK
        "RIGHT_CLICK_BLOCK" -> RegionEventType.RIGHT_CLICK
        "LEFT_CLICK_AIR",
        "RIGHT_CLICK_AIR",
        "PHYSICAL" -> RegionEventType.INTERACT
        else -> null
    }
}
