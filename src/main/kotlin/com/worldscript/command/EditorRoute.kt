package com.worldscript.command

internal object EditorRoute {
    fun fromCommand(eventKey: String?, actionPage: String?): String = when {
        eventKey == null -> "main"
        eventKey.equals("name", true) -> "name:"
        actionPage == null -> eventKey
        actionPage == "toggle" -> "toggle:$eventKey"
        actionPage.startsWith("cooldown:") -> "cooldown:$eventKey:${actionPage.removePrefix("cooldown:")}"
        actionPage.startsWith("mode:") -> "mode:$eventKey:${actionPage.removePrefix("mode:")}"
        actionPage.startsWith("action:") -> "action:$eventKey:${actionPage.removePrefix("action:")}"
        actionPage.startsWith("set:") -> "set:$eventKey:${actionPage.removePrefix("set:")}"
        actionPage.startsWith("remove:") -> "remove:$eventKey:${actionPage.removePrefix("remove:")}"
        actionPage.startsWith("sound:") -> "sound:$eventKey:${actionPage.removePrefix("sound:")}"
        actionPage.startsWith("select:") -> "select:$eventKey:${actionPage.removePrefix("select:")}"
        else -> eventKey
    }

    fun mutation(section: String): EditorMutation? =
        EditorOperation.entries.firstOrNull { section.startsWith(it.prefix) }
            ?.let { EditorMutation(it, section.removePrefix(it.prefix)) }
}

internal data class EditorMutation(
    val operation: EditorOperation,
    val payload: String,
)

internal enum class EditorOperation(val prefix: String) {
    STATUS("status:"),
    NAME("name:"),
    TOGGLE("toggle:"),
    COOLDOWN("cooldown:"),
    MODE("mode:"),
    SOUND("sound:"),
    SELECT("select:"),
    PARTICLE("particle:"),
    SET("set:"),
    REMOVE("remove:"),
}
