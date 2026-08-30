package com.worldscript.modules.l3.spawn

import java.util.UUID

data class SpawnRule(
    val id: String,
    val enabled: Boolean,
    val regionId: String,
    val provider: SpawnProvider,
    val mobId: String,
    val amount: IntRangeValue,
    val intervalSeconds: IntRangeValue,
    val players: SpawnPlayerSettings,
    val spawn: SpawnLocationSettings,
    val safety: SpawnSafetySettings,
    val cleanup: SpawnCleanupSettings,
    val limits: SpawnLimitSettings,
)

enum class SpawnProvider {
    AUTO,
    VANILLA,
    MYTHICMOBS,
}

data class IntRangeValue(
    val min: Int,
    val max: Int,
) {
    fun normalized(floor: Int, ceiling: Int): IntRangeValue {
        val left = min.coerceIn(floor, ceiling)
        val right = max.coerceIn(floor, ceiling)
        return if (left <= right) IntRangeValue(left, right) else IntRangeValue(right, left)
    }

    fun display(unit: String = ""): String = if (min == max) "$min$unit" else "$min~$max$unit"
}

data class SpawnPlayerSettings(
    val requireInsideRegion: Boolean,
    val requireNearby: Boolean,
    val nearbyRadius: Double,
    val minDistance: Double,
)

data class SpawnLocationSettings(
    val attempts: Int,
)

data class SpawnSafetySettings(
    val enabled: Boolean,
    val groundRequired: Boolean,
    val avoidLiquid: Boolean,
    val avoidSolidBody: Boolean,
)

data class SpawnCleanupSettings(
    val despawnWhenEmpty: Boolean,
    val delaySeconds: Long,
)

data class SpawnLimitSettings(
    val maxAlive: Int,
)

data class SpawnRuntime(
    val alive: MutableSet<UUID> = linkedSetOf(),
    var nextSpawnAtMillis: Long = 0L,
    var emptySinceMillis: Long = 0L,
    var lastFailureAtMillis: Long = 0L,
    var lastFailureReason: String = "",
)

data class SpawnDefaults(
    val amount: IntRangeValue,
    val intervalSeconds: IntRangeValue,
    val hardAmountMax: Int,
    val players: SpawnPlayerSettings,
    val spawn: SpawnLocationSettings,
    val safety: SpawnSafetySettings,
    val cleanup: SpawnCleanupSettings,
    val limits: SpawnLimitSettings,
    val mythicCommand: String,
)

enum class SpawnResult {
    SUCCESS,
    RULE_NOT_FOUND,
    RULE_DISABLED,
    REGION_NOT_FOUND,
    WORLD_NOT_FOUND,
    LIMIT_REACHED,
    NO_PLAYER,
    NO_SAFE_LOCATION,
    PROVIDER_UNAVAILABLE,
    INVALID_MOB,
}
