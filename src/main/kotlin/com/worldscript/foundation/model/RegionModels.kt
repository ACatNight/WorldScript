package com.worldscript.foundation.model

import java.util.Locale

data class BlockPosition(val x: Int, val y: Int, val z: Int)

data class RegionBounds(val min: BlockPosition, val max: BlockPosition)

enum class RegionEventType {
    ENTER,
    LEAVE,
    INTERACT,
    LEFT_CLICK,
    RIGHT_CLICK,
}

enum class ActionType {
    KETHER,
    TEXT_DISPLAY,
    SOUND,
    PLAYER_COMMAND,
    CONSOLE_COMMAND,
    MESSAGE,
    TELEPORT,
    SET_VARIABLE,
    SET_REGION_STATUS,
    GIVE_ITEM,
    GIVE_EXPERIENCE,
    GIVE_MONEY,
    UNLOCK_REGION,
    COMPLETE_REGION,

    ;

    companion object {
        /**
         * Parses the stable YAML representation of an action type.
         *
         * Region YAML uses kebab-case while Kotlin enum identifiers use
         * underscores. Keep that normalization here so every configuration
         * load path has identical compatibility behaviour.
         */
        fun parseYaml(value: String?): ActionType? {
            val normalized = value
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.replace('-', '_')
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return entries.firstOrNull { it.name == normalized }
        }

        fun yamlName(type: ActionType): String = type.name.lowercase(Locale.ROOT).replace('_', '-')
    }
}

/** World-level state shared by every player. */
enum class GlobalRegionStatus {
    LOCKED,
    OPEN,
    DANGEROUS,
    PEACEFUL;

    companion object {
        fun parse(value: String?): GlobalRegionStatus? = when (value?.trim()?.uppercase()) {
            "UNLOCKED" -> OPEN // Compatibility with pre-0.1.8 region files.
            else -> entries.firstOrNull { it.name == value?.trim()?.uppercase() }
        }
    }
}

enum class RegionRole {
    HUB,
    OPEN_ZONE,
    POINT_OF_INTEREST,
    DANGER_ZONE,
    GATE,
}

enum class ConditionType {
    PLAYER_LEVEL, // Retained only to reject legacy configuration safely.
    PERMISSION,
    ITEM,
    VARIABLE,
    REGION_STATUS,
    PLAYER_REGION_STATUS,
}

enum class ComparisonOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER,
    GREATER_OR_EQUAL,
    LESS,
    LESS_OR_EQUAL,
    EXISTS,
}

enum class ConditionMode { AND, OR }

data class ConditionDefinition(
    val type: ConditionType,
    val key: String = "",
    val value: String = "",
    val operator: ComparisonOperator = ComparisonOperator.EQUALS,
    val amount: Int = 1,
)

enum class RewardType {
    ITEM,
    EXPERIENCE,
    MONEY,
    COMMAND,
    UNLOCK_REGION,
    COMPLETE_REGION,
    SET_VARIABLE,
    SET_REGION_STATUS,
    MESSAGE,
}

data class RewardDefinition(
    val type: RewardType,
    val value: String,
    val amount: Double = 1.0,
    val once: Boolean = false,
)

data class ActionDefinition(
    val type: ActionType,
    val value: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val preset: String? = null,
)

data class RegionParticleDefinition(
    val enabled: Boolean = true,
    val preset: String = "AMBIENT",
    val type: String = "END_ROD",
    val count: Int = 2,
    val intervalTicks: Long = 20,
    val spreadX: Double = 1.5,
    val spreadY: Double = 0.8,
    val spreadZ: Double = 1.5,
    val speed: Double = 0.0,
)

data class DiscoveryDefinition(
    val enabled: Boolean = false,
    val titleEnabled: Boolean = false,
    /** Per-region switch for the global native discovery Toast. */
    val toastEnabled: Boolean = true,
    val title: String = "",
    val subtitle: String = "",
    val fadeIn: Int = 10,
    val stay: Int = 50,
    val fadeOut: Int = 10,
    val soundEnabled: Boolean = false,
    val sound: String = "ENTITY_PLAYER_LEVELUP",
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f,
    val rewardEnabled: Boolean = false,
    /** Canonical discovery action list for newly edited configurations. */
    val actions: List<ActionDefinition> = emptyList(),
    /** Legacy YAML compatibility; migrate-on-write copies this into [actions]. */
    val rewardActions: List<ActionDefinition> = emptyList(),
) {
    fun configuredActions(): List<ActionDefinition> = actions.ifEmpty { rewardActions }

    /**
     * Converts legacy reward actions to the canonical discovery action list.
     * Once an edited definition is written, the legacy list must be empty:
     * otherwise deleting the final canonical action would make old rewards
     * unexpectedly reappear through [configuredActions].
     */
    fun canonicalized(): DiscoveryDefinition = when {
        rewardActions.isEmpty() -> this
        actions.isEmpty() -> copy(actions = rewardActions, rewardActions = emptyList())
        else -> copy(rewardActions = emptyList())
    }
}

data class ScriptDefinition(
    val enabled: Boolean = true,
    val cooldownSeconds: Long = 0,
    val actions: List<ActionDefinition> = emptyList(),
    val conditions: List<ConditionDefinition> = emptyList(),
    /** Actions executed when this event's entry conditions deny access. */
    val conditionFailureActions: List<ActionDefinition> = emptyList(),
    val conditionMode: ConditionMode = ConditionMode.AND,
    /** Per-region opt-in switch for evaluating entry conditions. */
    val conditionsEnabled: Boolean = true,
    val rewards: List<RewardDefinition> = emptyList(),
    val overrideParent: Boolean = false,
    val firstEntryOnly: Boolean = false,
    val repeatEntryOnly: Boolean = false,
)

data class RegionDefinition(
    val id: String,
    val displayName: String,
    val worldId: String,
    val worldName: String,
    val bounds: RegionBounds,
    val shape: RegionShape = RegionShape.Cuboid,
    val role: RegionRole = RegionRole.OPEN_ZONE,
    val contentId: String = "",
    val priority: Int = 0,
    val events: Map<RegionEventType, ScriptDefinition> = emptyMap(),
    val parentId: String? = null,
    val inheritParent: Boolean = true,
    val variables: Map<String, String> = emptyMap(),
    val statuses: Set<GlobalRegionStatus> = emptySet(),
    val particle: RegionParticleDefinition? = null,
    val discovery: DiscoveryDefinition? = null,
)
