package com.worldscript.command

import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.RegionEventType

internal object EditorCatalog {
    val soundChoices = listOf(
        "BLOCK_PORTAL_TRIGGER",
        "BLOCK_NOTE_BLOCK_PLING",
        "ENTITY_PLAYER_LEVELUP",
        "ENTITY_VILLAGER_TRADE",
        "ENTITY_GENERIC_EXPLODE",
        "ENTITY_PLAYER_ATTACK_STRONG",
    )

    val particleChoices = listOf(
        "END_ROD",
        "FLAME",
        "ENCHANT",
        "PORTAL",
        "CLOUD",
        "SOUL_FIRE_FLAME",
        "HEART",
        "VILLAGER_HAPPY",
    )

    val actionFallbacks = mapOf(
        ActionType.TEXT_DISPLAY to "Title display",
        ActionType.SOUND to "Sound",
        ActionType.MESSAGE to "Chat message",
        ActionType.PLAYER_COMMAND to "Player command",
        ActionType.CONSOLE_COMMAND to "Console command",
        ActionType.TELEPORT to "Teleport",
        ActionType.KETHER to "Kether script",
        ActionType.SET_VARIABLE to "Set variable",
        ActionType.SET_REGION_STATUS to "Set region status",
        ActionType.GIVE_ITEM to "Give item",
        ActionType.GIVE_EXPERIENCE to "Give experience",
        ActionType.GIVE_MONEY to "Give money",
        ActionType.UNLOCK_REGION to "Unlock region",
        ActionType.COMPLETE_REGION to "Complete region",
    )
}

internal enum class RegionEventMenu(val key: String, val type: RegionEventType) {
    ENTER("enter", RegionEventType.ENTER),
    LEAVE("leave", RegionEventType.LEAVE),
    LEFT("left-click", RegionEventType.LEFT_CLICK),
    RIGHT("right-click", RegionEventType.RIGHT_CLICK),
    INTERACT("interact", RegionEventType.INTERACT),
}

internal data class ChatEditorButton(val label: String, val hover: String, val command: String)

internal data class EditorPendingInput(
    val regionId: String,
    val eventKey: String,
    val type: RegionEventType,
    val index: Int,
    val parameter: String,
    val createdAt: Long,
)
