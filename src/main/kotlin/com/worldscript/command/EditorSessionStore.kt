package com.worldscript.command

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owns one pending chat edit per player. */
internal class EditorSessionStore {
    // Chat input is received asynchronously while editor navigation and
    // lifecycle callbacks run on the server thread.
    private val sessions = ConcurrentHashMap<UUID, EditorPendingInput>()

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
