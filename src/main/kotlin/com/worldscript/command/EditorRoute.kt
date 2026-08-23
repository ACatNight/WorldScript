package com.worldscript.command

internal object EditorRoute {
    fun fromCommand(eventKey: String?, actionPage: String?): String = when {
        eventKey == null -> "main"
        eventKey.equals("name", true) -> "name:"
        eventKey.equals("variable", true) -> "variable:${actionPage ?: ""}"
        eventKey.equals("conditions", true) && actionPage.equals("add", true) -> "condition:add"
        eventKey.equals("conditions", true) && actionPage.equals("mode", true) -> "condition:mode"
        eventKey.equals("conditions", true) && actionPage.equals("failure-add", true) -> "condition:failure-add"
        eventKey.equals("conditions", true) && actionPage?.startsWith("failure-remove:") == true -> "condition:failure-remove:${actionPage.orEmpty().removePrefix("failure-remove:")}"
        eventKey.equals("conditions", true) && actionPage?.startsWith("failure-edit:") == true -> "condition:failure-edit:${actionPage.orEmpty().removePrefix("failure-edit:")}"
        eventKey.equals("conditions", true) && actionPage?.startsWith("edit:") == true -> "condition:edit:${actionPage.orEmpty().removePrefix("edit:")}"
        eventKey.equals("conditions", true) && actionPage?.startsWith("remove:") == true -> "condition:remove:${actionPage.orEmpty().removePrefix("remove:")}"
        actionPage == null -> eventKey
        actionPage == "toggle" -> "toggle:$eventKey"
        actionPage.startsWith("cooldown:") -> "cooldown:$eventKey:${actionPage.removePrefix("cooldown:")}"
        actionPage.startsWith("mode:") -> "mode:$eventKey:${actionPage.removePrefix("mode:")}"
        actionPage.startsWith("action:") -> "action:$eventKey:${actionPage.removePrefix("action:")}"
        actionPage.startsWith("set:") -> "set:$eventKey:${actionPage.removePrefix("set:")}"
        actionPage.startsWith("remove:") -> "remove:$eventKey:${actionPage.removePrefix("remove:")}"
        actionPage.startsWith("sound:") -> "sound:$eventKey:${actionPage.removePrefix("sound:")}"
        actionPage.startsWith("select:") -> "select:$eventKey:${actionPage.removePrefix("select:")}"
        actionPage.startsWith("discovery:") -> "discovery:${actionPage.removePrefix("discovery:")}"
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
    VARIABLE("variable:"),
    TOGGLE("toggle:"),
    COOLDOWN("cooldown:"),
    MODE("mode:"),
    SOUND("sound:"),
    SELECT("select:"),
    PARTICLE("particle:"),
    SET("set:"),
    REMOVE("remove:"),
    DISCOVERY("discovery:"),
    CONDITION("condition:"),
}
