package com.worldscript.command

internal data class EditorActionRef(
    val eventKey: String,
    val index: Int,
    val arguments: List<String>,
) {
    companion object {
        fun parse(value: String): EditorActionRef? {
            val parts = value.split(':')
            val eventKey = parts.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
            val index = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
            return EditorActionRef(eventKey, index, parts.drop(2))
        }
    }
}
