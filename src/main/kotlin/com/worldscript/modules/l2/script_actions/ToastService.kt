package com.worldscript.modules.l2.script_actions

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/** Best-effort native advancement toast with a language-message fallback. */
class ToastService(private val plugin: JavaPlugin) {
    private val lang = Lang(plugin)
    private val loadedKeys = linkedSetOf<NamespacedKey>()
    private val queues = mutableMapOf<UUID, ArrayDeque<ToastRequest>>()
    private val showing = mutableSetOf<UUID>()
    private val loadMethod: Method? = runCatching {
        Bukkit.getUnsafe()::class.java.getMethod("loadAdvancement", NamespacedKey::class.java, String::class.java)
    }.getOrNull()
    private val removeMethod: Method? = runCatching {
        Bukkit.getUnsafe()::class.java.getMethod("removeAdvancement", NamespacedKey::class.java)
    }.getOrNull()

    fun showDiscovery(player: Player, regionId: String, regionName: String, regionEnabled: Boolean) {
        if (!regionEnabled || !plugin.config.getBoolean("discovery.display.toast.enabled", false)) return
        queues.getOrPut(player.uniqueId) { ArrayDeque() }.addLast(
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                title = template("discovery.display.toast.title", regionName),
                description = template("discovery.display.toast.description", regionName),
                frame = plugin.config.getString("discovery.display.toast.frame")?.lowercase(Locale.ROOT).orEmpty(),
                icon = plugin.config.getString("discovery.display.toast.icon").orEmpty(),
            ),
        )
        showNext(player)
    }

    fun close() {
        loadedKeys.forEach { key -> runCatching { removeMethod?.invoke(Bukkit.getUnsafe(), key) } }
        loadedKeys.clear()
        queues.clear()
        showing.clear()
    }

    private fun showNext(player: Player) {
        if (!showing.add(player.uniqueId)) return
        val request = queues[player.uniqueId]?.pollFirst()
        if (request == null) {
            showing.remove(player.uniqueId)
            queues.remove(player.uniqueId)
            return
        }
        val frame = when (request.frame) {
            "task", "goal", "challenge" -> request.frame
            else -> null
        }
        val icon = resolveIcon(request.icon)
        if (frame == null || icon == null || !sendNative(player, request.regionId, request.title, request.description, frame, icon)) {
            lang.send(player, "discovery-toast-fallback", true, "region" to request.regionName)
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            showing.remove(player.uniqueId)
            if (player.isOnline) showNext(player) else queues.remove(player.uniqueId)
        }, plugin.config.getLong("discovery.display.toast.queue-interval-ticks").coerceAtLeast(1L))
    }

    private fun sendNative(player: Player, regionId: String, title: String, description: String, frame: String, icon: Material): Boolean {
        val method = loadMethod ?: return false
        val key = NamespacedKey(plugin, "toast_${regionId.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._/-]"), "_")}")
        return runCatching {
            val json = """{"criteria":{"worldscript":{"trigger":"minecraft:impossible"}},"display":{"icon":{"item":"minecraft:${icon.name.lowercase(Locale.ROOT)}"},"title":{"text":"${jsonEscape(color(title))}"},"description":{"text":"${jsonEscape(color(description))}"},"frame":"$frame","announce_to_chat":false,"hidden":true}}"""
            val advancement = method.invoke(Bukkit.getUnsafe(), key, json) ?: return false
            loadedKeys += key
            val progress = player.getAdvancementProgress(advancement as org.bukkit.advancement.Advancement)
            progress.remainingCriteria.forEach { progress.awardCriteria(it) }
            progress.awardedCriteria.forEach { progress.revokeCriteria(it) }
            true
        }.getOrElse {
            plugin.logger.warning("Could not show discovery toast for '$regionId': ${it.message}")
            false
        }
    }

    private fun resolveIcon(raw: String): Material? {
        val configured = raw.trim()
        val materialName = if (configured.equals("region", true)) {
            plugin.config.getString("gui.materials.point-of-interest").orEmpty()
        } else configured
        return MaterialResolver.find(materialName)
    }

    private fun template(path: String, regionName: String): String =
        plugin.config.getString(path).orEmpty().replace("%region%", regionName)

    /** Advancement JSON does not interpret legacy colour codes consistently across supported clients. */
    private fun color(value: String): String = net.md_5.bungee.api.ChatColor.stripColor(
        net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', value),
    )

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private data class ToastRequest(
        val regionId: String,
        val regionName: String,
        val title: String,
        val description: String,
        val frame: String,
        val icon: String,
    )
}
