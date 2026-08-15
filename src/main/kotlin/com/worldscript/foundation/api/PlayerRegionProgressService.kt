package com.worldscript.foundation.api

import java.util.UUID

/**
 * Player-specific region progress for integrations such as quest plugins.
 * Implementations must be called from the server thread.
 */
interface PlayerRegionProgressService {
    fun isRegionUnlocked(playerId: UUID, regionId: String): Boolean
    fun unlockRegion(playerId: UUID, regionId: String)
    fun hasEnteredRegion(playerId: UUID, regionId: String): Boolean
    fun markRegionEntered(playerId: UUID, regionId: String)
    fun isRegionCompleted(playerId: UUID, regionId: String): Boolean
    fun markRegionCompleted(playerId: UUID, regionId: String)
}
