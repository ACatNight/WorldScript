package com.worldscript.command

import java.util.UUID

/** Owns one pending chat edit per player. */
internal class EditorSessionStore {
    private val sessions = mutableMapOf<UUID, EditorPendingInput>()

    fun begin(playerId: UUID, pending: EditorPendingInput) {
        sessions[playerId] = pending
    }

    fun take(playerId: UUID): EditorPendingInput? = sessions.remove(playerId)

    fun clear(playerId: UUID) {
        sessions.remove(playerId)
    }

    fun clearAll() {
        sessions.clear()
    }
}
