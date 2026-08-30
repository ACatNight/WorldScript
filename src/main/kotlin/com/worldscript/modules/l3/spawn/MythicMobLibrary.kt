package com.worldscript.modules.l3.spawn

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class MythicMobLibrary(private val plugin: JavaPlugin) {
    fun available(): Boolean = Bukkit.getPluginManager().isPluginEnabled("MythicMobs")

    fun mobIds(): List<String> {
        if (!available()) return emptyList()
        return readByModernApi().ifEmpty { readByLegacyApi() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun contains(id: String): Boolean {
        val requested = id.trim()
        if (requested.isBlank() || !available()) return false
        return mobIds().any { it.equals(requested, true) }
    }

    private fun readByModernApi(): List<String> = runCatching {
        val mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
        val instance = mythicBukkit.getMethod("inst").invoke(null)
        val mobManager = instance.javaClass.methods.firstOrNull { it.name == "getMobManager" && it.parameterTypes.isEmpty() }
            ?.invoke(instance) ?: return emptyList()
        val namesMethod = mobManager.javaClass.methods.firstOrNull { it.name == "getMobNames" && it.parameterTypes.isEmpty() }
            ?: return emptyList()
        toStringList(namesMethod.invoke(mobManager))
    }.onFailure {
        plugin.logger.fine("Could not read MythicMobs modern mob list: ${it.message}")
    }.getOrDefault(emptyList())

    private fun readByLegacyApi(): List<String> = runCatching {
        val pluginInstance = Bukkit.getPluginManager().getPlugin("MythicMobs") ?: return emptyList()
        val mobManager = pluginInstance.javaClass.methods.firstOrNull { it.name.lowercase(Locale.ROOT).contains("mobmanager") && it.parameterTypes.isEmpty() }
            ?.invoke(pluginInstance) ?: return emptyList()
        val method = mobManager.javaClass.methods.firstOrNull {
            it.parameterTypes.isEmpty() && it.name.lowercase(Locale.ROOT) in setOf("getmobnames", "getmobtypes")
        } ?: return emptyList()
        toStringList(method.invoke(mobManager))
    }.onFailure {
        plugin.logger.fine("Could not read MythicMobs legacy mob list: ${it.message}")
    }.getOrDefault(emptyList())

    private fun toStringList(value: Any?): List<String> {
        if (value == null) return emptyList()
        return when (value) {
            is Iterable<*> -> value.mapNotNull { item -> item?.toString()?.substringAfterLast(':')?.takeIf { it.isNotBlank() } }
            is Array<*> -> value.mapNotNull { item -> item?.toString()?.substringAfterLast(':')?.takeIf { it.isNotBlank() } }
            else -> emptyList()
        }
    }
}
