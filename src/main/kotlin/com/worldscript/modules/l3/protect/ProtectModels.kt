package com.worldscript.modules.l3.protect

import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import java.util.Locale

data class ProtectPvpSettings(
    val enabled: Boolean = true,
    val defaultAllow: Boolean = true,
    val blockedStatuses: Set<String> = setOf("peaceful"),
    val allowedStatuses: Set<String> = setOf("dangerous"),
)

data class ProtectDecision(
    val allowed: Boolean,
    val matchedStatus: String = "",
)

object ProtectPolicy {
    fun decidePvp(region: RegionDefinition?, settings: ProtectPvpSettings): ProtectDecision {
        if (!settings.enabled || region == null) return ProtectDecision(true)
        val statuses = region.statuses.map { statusKey(it) }.toSet()
        val blocked = statuses.firstOrNull { it in settings.blockedStatuses }
        if (blocked != null) return ProtectDecision(false, blocked)
        val allowed = statuses.firstOrNull { it in settings.allowedStatuses }
        if (allowed != null) return ProtectDecision(true, allowed)
        return ProtectDecision(settings.defaultAllow)
    }

    fun normalizeStatus(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace('-', '_')

    private fun statusKey(status: GlobalRegionStatus): String =
        status.name.lowercase(Locale.ROOT)
}
