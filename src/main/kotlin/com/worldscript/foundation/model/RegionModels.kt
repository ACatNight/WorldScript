package com.worldscript.foundation.model

data class BlockPosition(val x: Int, val y: Int, val z: Int)

data class RegionBounds(val min: BlockPosition, val max: BlockPosition)

enum class RegionEventType {
    ENTER,
    LEAVE,
    INTERACT,
}

enum class ActionType {
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
}

enum class RegionStatus {
    LOCKED,
    UNLOCKED,
    COMPLETED,
    DANGEROUS,
    PEACEFUL,
    QUEST_ACTIVE,
}

enum class ConditionType {
    PLAYER_LEVEL,
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

data class ActionDefinition(val type: ActionType, val value: String)

data class ScriptDefinition(
    val enabled: Boolean = true,
    val cooldownSeconds: Long = 0,
    val actions: List<ActionDefinition> = emptyList(),
    val conditions: List<ConditionDefinition> = emptyList(),
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
    val priority: Int = 0,
    val events: Map<RegionEventType, ScriptDefinition> = emptyMap(),
    val parentId: String? = null,
    val inheritParent: Boolean = true,
    val variables: Map<String, String> = emptyMap(),
    val statuses: Set<RegionStatus> = emptySet(),
)
