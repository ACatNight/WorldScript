package com.worldscript.modules.l2.script_actions

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.model.RegionRole
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

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
    private val requestSequence = AtomicLong()

    fun showDiscovery(
        player: Player,
        regionId: String,
        regionName: String,
        regionRole: RegionRole,
        regionEnabled: Boolean,
        titleOverride: String,
        descriptionOverride: String,
        iconOverride: String,
    ) {
        if (!regionEnabled || !plugin.config.getBoolean("discovery.display.toast.enabled", false)) return
        queues.getOrPut(player.uniqueId) { ArrayDeque() }.addLast(
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                regionRole = regionRole,
                title = template(titleOverride.ifBlank { plugin.config.getString("discovery.display.toast.title").orEmpty() }, regionName),
                description = template(descriptionOverride.ifBlank { plugin.config.getString("discovery.display.toast.description").orEmpty() }, regionName),
                frame = plugin.config.getString("discovery.display.toast.frame")?.lowercase(Locale.ROOT).orEmpty(),
                icon = iconOverride.ifBlank { plugin.config.getString("discovery.display.toast.icon").orEmpty() },
            ),
        )
        showNext(player)
    }

    /** Queues a Toast from any region event action, independent of first discovery. */
    fun showAction(
        player: Player,
        regionId: String,
        regionName: String,
        regionRole: RegionRole,
        title: String,
        description: String,
        icon: String,
        frame: String,
    ) {
        if (!plugin.config.getBoolean("discovery.display.toast.enabled", false)) return
        queues.getOrPut(player.uniqueId) { ArrayDeque() }.addLast(
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                regionRole = regionRole,
                title = template(title.ifBlank { plugin.config.getString("discovery.display.toast.title").orEmpty() }, regionName),
                description = template(description.ifBlank { plugin.config.getString("discovery.display.toast.description").orEmpty() }, regionName),
                frame = frame.ifBlank { plugin.config.getString("discovery.display.toast.frame").orEmpty() }.lowercase(Locale.ROOT),
                icon = icon.ifBlank { plugin.config.getString("discovery.display.toast.icon").orEmpty() },
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
        val icon = resolveIcon(request.icon, request.regionRole)
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
        val regionKey = regionId.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._/-]"), "_")
            .take(32)
            .ifBlank { "region" }
        val key = NamespacedKey(plugin, "toast_${regionKey}_${player.uniqueId.toString().replace("-", "")}_${requestSequence.incrementAndGet()}")
        return runCatching {
            val json = AdvancementToastPayload.create(
                minecraftVersion = Bukkit.getBukkitVersion(),
                materialName = icon.name,
                title = color(title),
                description = color(description),
                frame = frame,
            )
            val advancement = method.invoke(Bukkit.getUnsafe(), key, json) ?: return false
            loadedKeys += key
            val progress = player.getAdvancementProgress(advancement as org.bukkit.advancement.Advancement)
            progress.remainingCriteria.forEach { progress.awardCriteria(it) }
            val cleanupDelay = plugin.config.getLong("discovery.display.toast.revoke-delay-ticks", 5L).coerceAtLeast(1L)
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                runCatching {
                    if (player.isOnline) {
                        val currentProgress = player.getAdvancementProgress(advancement)
                        currentProgress.awardedCriteria.toList().forEach { currentProgress.revokeCriteria(it) }
                    }
                }.onFailure { error ->
                    plugin.logger.warning("Could not revoke temporary Toast advancement '$key': ${rootMessage(error)}")
                }
                runCatching { removeMethod?.invoke(Bukkit.getUnsafe(), key) }
                    .onFailure { error -> plugin.logger.warning("Could not remove temporary Toast advancement '$key': ${rootMessage(error)}") }
                loadedKeys.remove(key)
            }, cleanupDelay)
            true
        }.getOrElse {
            runCatching { removeMethod?.invoke(Bukkit.getUnsafe(), key) }
            loadedKeys.remove(key)
            plugin.logger.warning("Could not show Toast for '$regionId': ${rootMessage(it)}")
            false
        }
    }

    private fun resolveIcon(raw: String, role: RegionRole): Material? {
        val configured = raw.trim()
        val materialName = if (configured.equals("region", true)) {
            val roleKey = role.name.lowercase(Locale.ROOT).replace('_', '-')
            plugin.config.getItemStack("discovery.display.toast.role-items.$roleKey")?.type?.name
                ?: plugin.config.getString("discovery.display.toast.role-icons.$roleKey")
                ?: plugin.config.getString("gui.materials.$roleKey")
                ?: plugin.config.getString("gui.materials.point-of-interest").orEmpty()
        } else configured
        return MaterialResolver.find(materialName)
    }

    private fun template(value: String, regionName: String): String = value.replace("%region%", regionName)

    /** Advancement JSON does not interpret legacy colour codes consistently across supported clients. */
    private fun color(value: String): String = net.md_5.bungee.api.ChatColor.stripColor(
        net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', value),
    )

    private fun rootMessage(error: Throwable): String {
        var current = if (error is InvocationTargetException) error.targetException ?: error else error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return current.message?.takeIf { it.isNotBlank() } ?: current.javaClass.simpleName
    }

    private data class ToastRequest(
        val regionId: String,
        val regionName: String,
        val regionRole: RegionRole,
        val title: String,
        val description: String,
        val frame: String,
        val icon: String,
    )
}

internal object AdvancementToastPayload {
    fun create(minecraftVersion: String, materialName: String, title: String, description: String, frame: String): String {
        val iconKey = if (usesDataComponentItemFormat(minecraftVersion)) "id" else "item"
        val material = materialName.lowercase(Locale.ROOT)
        return """{"criteria":{"worldscript":{"trigger":"minecraft:impossible"}},"display":{"icon":{"$iconKey":"minecraft:$material"},"title":{"text":"${escape(title)}"},"description":{"text":"${escape(description)}"},"frame":"$frame","show_toast":true,"announce_to_chat":false,"hidden":true}}"""
    }

    fun usesDataComponentItemFormat(version: String): Boolean {
        val match = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""").find(version) ?: return false
        val major = match.groupValues[1].toIntOrNull() ?: return false
        val minor = match.groupValues[2].toIntOrNull() ?: return false
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        return major > 1 || minor > 20 || (minor == 20 && patch >= 5)
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
