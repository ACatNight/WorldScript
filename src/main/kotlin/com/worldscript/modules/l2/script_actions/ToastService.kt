package com.worldscript.modules.l2.script_actions

import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.TextFormatter
import com.worldscript.foundation.model.RegionRole
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.chat.ComponentSerializer
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
    private val lang = com.worldscript.foundation.Lang(plugin)
    private val loadedKeys = linkedSetOf<NamespacedKey>()
    private val queues = mutableMapOf<UUID, ArrayDeque<ToastRequest>>()
    private val showing = mutableSetOf<UUID>()
    private val lastQueued = mutableMapOf<UUID, QueuedToast>()
    private val loadMethod: Method? = runCatching {
        Bukkit.getUnsafe()::class.java.methods.firstOrNull { method ->
            method.name == "loadAdvancement" &&
                (method.parameterTypes.size == 2 ||
                    (method.parameterTypes.size == 3 && method.parameterTypes[2] == Boolean::class.javaPrimitiveType)) &&
                method.parameterTypes[0] == NamespacedKey::class.java &&
                method.parameterTypes[1] == String::class.java
        }
    }.getOrNull()
    private val removeMethod: Method? = runCatching {
        Bukkit.getUnsafe()::class.java.getMethod("removeAdvancement", NamespacedKey::class.java)
    }.getOrNull()
    private val fromLegacyMaterialMethod: Method? = runCatching {
        Bukkit.getUnsafe()::class.java.getMethod("fromLegacy", Material::class.java)
    }.getOrNull()
    private val materialKeyMethod: Method? = runCatching {
        Material::class.java.getMethod("getKey")
    }.getOrNull()
    private val updateResourcesMethod: Method? = runCatching {
        plugin.server.javaClass.methods.firstOrNull { it.name == "updateResources" && it.parameterCount == 0 }
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
        enqueue(player,
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                regionRole = regionRole,
                title = TextFormatter.region(titleOverride.ifBlank { plugin.config.getString("discovery.display.toast.title").orEmpty() }, regionName),
                description = TextFormatter.region(descriptionOverride.ifBlank { plugin.config.getString("discovery.display.toast.description").orEmpty() }, regionName),
                frame = plugin.config.getString("discovery.display.toast.frame")?.lowercase(Locale.ROOT).orEmpty(),
                icon = iconOverride.ifBlank { plugin.config.getString("discovery.display.toast.icon").orEmpty() },
            ),
        )
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
        // An explicitly configured event action is independent from the
        // first-discovery switch. The global toggle controls discovery
        // feedback only; otherwise action Toasts silently do nothing.
        enqueue(player,
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                regionRole = regionRole,
                title = TextFormatter.region(title.ifBlank { regionName }, regionName),
                description = TextFormatter.region(description, regionName),
                frame = frame.ifBlank { "task" }.lowercase(Locale.ROOT),
                icon = icon.ifBlank { "region" },
            ),
        )
    }

    /** Manual diagnostic preview. It is intentionally independent of discovery switches. */
    fun showPreview(player: Player, regionId: String, regionName: String, role: RegionRole) {
        showDiscoveryPreview(player, regionId, regionName, role, "", "", "")
    }

    /**
     * Shows the exact Toast currently configured for a region's discovery page.
     * This deliberately bypasses all discovery/global switches: it is an editor
     * preview, not a discovery event.
     */
    fun showDiscoveryPreview(
        player: Player,
        regionId: String,
        regionName: String,
        role: RegionRole,
        titleOverride: String,
        descriptionOverride: String,
        iconOverride: String,
    ) {
        enqueue(player,
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                regionRole = role,
                title = TextFormatter.region(titleOverride.ifBlank { plugin.config.getString("discovery.display.toast.title").orEmpty() }, regionName),
                description = TextFormatter.region(descriptionOverride.ifBlank { plugin.config.getString("discovery.display.toast.description").orEmpty() }, regionName),
                frame = plugin.config.getString("discovery.display.toast.frame").orEmpty().lowercase(Locale.ROOT),
                icon = iconOverride.ifBlank { plugin.config.getString("discovery.display.toast.icon").orEmpty() },
            ),
        )
    }

    /** Editor preview for an event Toast action, independent of global switches. */
    fun showActionPreview(
        player: Player,
        regionId: String,
        regionName: String,
        regionRole: RegionRole,
        title: String,
        description: String,
        icon: String,
        frame: String,
    ) {
        enqueue(player,
            ToastRequest(
                regionId = regionId,
                regionName = regionName,
                regionRole = regionRole,
                title = TextFormatter.region(title.ifBlank { regionName }, regionName),
                description = TextFormatter.region(description, regionName),
                frame = frame.ifBlank { "task" }.lowercase(Locale.ROOT),
                icon = icon.ifBlank { "region" },
            ),
        )
    }

    fun close() {
        loadedKeys.forEach { key -> runCatching { removeMethod?.invoke(Bukkit.getUnsafe(), key) } }
        loadedKeys.clear()
        queues.clear()
        showing.clear()
        lastQueued.clear()
    }

    /** Read-only effective configuration used by editor and command diagnostics. */
    fun diagnose(regionName: String, role: RegionRole, regionEnabled: Boolean, titleOverride: String, descriptionOverride: String, iconOverride: String): ToastDiagnostics {
        val title = TextFormatter.region(titleOverride.ifBlank { plugin.config.getString("discovery.display.toast.title").orEmpty() }, regionName)
        val description = TextFormatter.region(descriptionOverride.ifBlank { plugin.config.getString("discovery.display.toast.description").orEmpty() }, regionName)
        val frame = plugin.config.getString("discovery.display.toast.frame", "task").orEmpty().lowercase(Locale.ROOT)
        val requestedIcon = iconOverride.ifBlank { plugin.config.getString("discovery.display.toast.icon").orEmpty() }
        val icon = resolveIcon(requestedIcon, role)
        return ToastDiagnostics(
            globalEnabled = plugin.config.getBoolean("discovery.display.toast.enabled", false),
            regionEnabled = regionEnabled,
            loadApiAvailable = loadMethod != null,
            frame = frame,
            frameValid = frame in VALID_FRAMES,
            title = title,
            description = description,
            descriptionDisplay = descriptionDisplayMode(),
            requestedIcon = requestedIcon.ifBlank { "region" },
            resolvedIcon = icon?.name.orEmpty(),
            iconValid = icon != null,
        )
    }

    private fun showNext(player: Player) {
        if (!showing.add(player.uniqueId)) return
        val request = queues[player.uniqueId]?.pollFirst()
        if (request == null) {
            showing.remove(player.uniqueId)
            queues.remove(player.uniqueId)
            return
        }
        val frame = request.frame.takeIf { it in VALID_FRAMES }
        val icon = resolveIcon(request.icon, request.regionRole)
        if (frame == null || icon == null || !sendNative(player, request.regionId, request.regionName, request.title, request.description, frame, icon)) {
            lang.send(player, "discovery-toast-fallback", true, "region" to request.regionName)
        } else {
            showDescription(player, request.description)
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            showing.remove(player.uniqueId)
            if (player.isOnline) showNext(player) else queues.remove(player.uniqueId)
        }, plugin.config.getLong("discovery.display.toast.queue-interval-ticks").coerceAtLeast(1L))
    }

    /**
     * Discovery and an ENTER action can happen in the same tick. Do not make
     * the client show the exact same Toast twice merely because both routes
     * requested it. Different event Toasts remain queued normally.
     */
    private fun enqueue(player: Player, request: ToastRequest) {
        val now = System.currentTimeMillis()
        val fingerprint = listOf(request.title, request.description, request.frame, request.icon).joinToString("\u0000")
        val previous = lastQueued[player.uniqueId]
        val window = plugin.config.getLong("discovery.display.toast.duplicate-window-millis", 750L).coerceAtLeast(0L)
        if (previous != null && previous.fingerprint == fingerprint && now - previous.queuedAt <= window) return
        lastQueued[player.uniqueId] = QueuedToast(fingerprint, now)
        queues.getOrPut(player.uniqueId) { ArrayDeque() }.addLast(request)
        showNext(player)
    }

    private fun sendNative(player: Player, regionId: String, regionName: String, title: String, description: String, frame: String, icon: Material): Boolean {
        val method = loadMethod ?: run {
            plugin.logger.warning("Toast unavailable: this server does not expose a compatible loadAdvancement API.")
            return false
        }
        val regionKey = regionId.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._/-]"), "_")
            .take(32)
            .ifBlank { "region" }
        val key = NamespacedKey(plugin, "toast_${regionKey}_${player.uniqueId.toString().replace("-", "")}_${requestSequence.incrementAndGet()}")
        return runCatching {
            val json = AdvancementToastPayload.createComponents(
                minecraftVersion = Bukkit.getBukkitVersion(),
                itemId = toastItemId(icon),
                titleComponent = legacyComponent(nativeToastText(title, description)),
                descriptionComponent = legacyComponent(description),
                frame = frame,
            )
            val advancement = when (method.parameterTypes.size) {
                2 -> method.invoke(Bukkit.getUnsafe(), key, json)
                3 -> method.invoke(Bukkit.getUnsafe(), key, json, false)
                else -> null
            } ?: run {
                plugin.logger.warning("Toast advancement '$key' was not accepted by the server.")
                return false
            }
            loadedKeys += key
            val cleanupDelay = plugin.config.getLong("discovery.display.toast.revoke-delay-ticks", 5L).coerceAtLeast(1L)
            runCatching { updateResourcesMethod?.invoke(plugin.server) }
                .onFailure { error -> plugin.logger.warning("Could not sync temporary Toast advancement '$key': ${rootMessage(error)}") }
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                runCatching {
                    if (player.isOnline) {
                        val progress = player.getAdvancementProgress(advancement as org.bukkit.advancement.Advancement)
                        progress.remainingCriteria.toList().forEach { progress.awardCriteria(it) }
                        plugin.server.scheduler.runTaskLater(plugin, Runnable {
                            runCatching {
                                if (player.isOnline) {
                                    val currentProgress = player.getAdvancementProgress(advancement)
                                    currentProgress.awardedCriteria.toList().forEach { currentProgress.revokeCriteria(it) }
                                }
                            }.onFailure { error ->
                                plugin.logger.warning("Could not revoke temporary Toast advancement '$key': ${rootMessage(error)}")
                            }
                        }, cleanupDelay)
                    }
                }.onFailure { error ->
                    plugin.logger.warning("Could not award temporary Toast advancement '$key': ${rootMessage(error)}")
                    // loadAdvancement can succeed while the actual client
                    // update fails (for example on an unsupported server
                    // implementation). Do not leave the user with only a log
                    // entry; provide the configured fallback notification.
                    if (player.isOnline) {
                        lang.send(player, "discovery-toast-fallback", true, "region" to regionName)
                    }
                }
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    runCatching { removeMethod?.invoke(Bukkit.getUnsafe(), key) }
                        .onFailure { error -> plugin.logger.warning("Could not remove temporary Toast advancement '$key': ${rootMessage(error)}") }
                    loadedKeys.remove(key)
                }, cleanupDelay + 1L)
            }, 1L)
            true
        }.getOrElse {
            runCatching { removeMethod?.invoke(Bukkit.getUnsafe(), key) }
            loadedKeys.remove(key)
            plugin.logger.warning("Could not show Toast for '$regionId': ${rootMessage(it)}")
            false
        }
    }

    private fun toastItemId(material: Material): String {
        // Use the server's canonical registry key first. Material enum names
        // are not resource locations (for example LEGACY_LOG would otherwise
        // become the invalid minecraft:log key on modern clients).
        modernMaterialId(material)?.let { return it }
        val fallbackName = plugin.config.getString("discovery.display.toast.fallback-icon", "PAPER") ?: "PAPER"
        val fallback = MaterialResolver.find(fallbackName)
        if (fallback != null) modernMaterialId(fallback)?.let { return it }
        plugin.logger.warning("Invalid discovery.display.toast.fallback-icon '$fallbackName'; using minecraft:paper.")
        return "minecraft:paper"
    }

    private fun modernMaterialId(material: Material): String? {
        val legacyName = "LEGACY_${material.name.removePrefix("LEGACY_")}"
        val legacyCandidate = runCatching { java.lang.Enum.valueOf(Material::class.java, legacyName) }.getOrNull()
            ?: material
        val converted = runCatching {
            fromLegacyMaterialMethod?.invoke(Bukkit.getUnsafe(), legacyCandidate) as? Material
        }.getOrNull() ?: material
        val key = runCatching { materialKeyMethod?.invoke(converted)?.toString() }.getOrNull()
            ?: return null
        val normalized = key.substringAfter(':').lowercase(Locale.ROOT)
        val legacyAliases = mapOf(
            "log" to "oak_log",
            "log_2" to "acacia_log",
            "leaves" to "oak_leaves",
            "leaves_2" to "acacia_leaves",
            "wood" to "oak_planks",
            "stone" to "stone",
        )
        val safeKey = legacyAliases[normalized]?.let { "${key.substringBefore(':')}:$it" } ?: key
        return safeKey.takeIf {
            ':' in it &&
                !converted.name.startsWith("LEGACY_", true) &&
                !it.substringAfter(':').startsWith("legacy_", true)
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

    /**
     * Vanilla advancement Toasts only draw their title. In `toast` mode, put
     * the description on a second title line so it stays inside the native
     * Toast instead of being rendered elsewhere on the player's screen.
     */
    private fun nativeToastText(title: String, description: String): String =
        if (descriptionDisplayMode() in TOAST_DESCRIPTION_MODES && description.isNotBlank()) "$title\n$description" else title

    private fun showDescription(player: Player, description: String) {
        if (description.isBlank()) return
        val formatted = legacyText(description)
        when (descriptionDisplayMode()) {
            "action-bar", "actionbar" -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(formatted))
            "toast-and-action-bar", "toast-and-actionbar" -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(formatted))
            "chat" -> player.spigot().sendMessage(*TextComponent.fromLegacyText(formatted))
            "toast-and-chat" -> player.spigot().sendMessage(*TextComponent.fromLegacyText(formatted))
        }
    }

    private fun descriptionDisplayMode(): String =
        plugin.config.getString("discovery.display.toast.description-display", "toast")?.lowercase(Locale.ROOT) ?: "toast"

    /** Converts legacy & colour codes into a JSON chat component for advancement data. */
    private fun legacyComponent(value: String): String =
        ComponentSerializer.toString(TextComponent.fromLegacyText(legacyText(value)))

    private fun legacyText(value: String): String =
        TextFormatter.color(value)

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

    private data class QueuedToast(val fingerprint: String, val queuedAt: Long)

    data class ToastDiagnostics(
        val globalEnabled: Boolean,
        val regionEnabled: Boolean,
        val loadApiAvailable: Boolean,
        val frame: String,
        val frameValid: Boolean,
        val title: String,
        val description: String,
        val descriptionDisplay: String,
        val requestedIcon: String,
        val resolvedIcon: String,
        val iconValid: Boolean,
    )

    private companion object {
        val VALID_FRAMES = setOf("task", "goal", "challenge")
        val TOAST_DESCRIPTION_MODES = setOf("toast", "toast-and-action-bar", "toast-and-actionbar", "toast-and-chat")
    }
}

internal object AdvancementToastPayload {
    fun create(minecraftVersion: String, itemId: String, title: String, description: String, frame: String): String {
        return createComponents(
            minecraftVersion,
            itemId,
            plainComponent(title),
            plainComponent(description),
            frame,
        )
    }

    fun createComponents(
        minecraftVersion: String,
        itemId: String,
        titleComponent: String,
        descriptionComponent: String,
        frame: String,
    ): String {
        val iconKey = if (usesDataComponentItemFormat(minecraftVersion)) "id" else "item"
        val normalizedItemId = itemId.lowercase(Locale.ROOT).let { if (':' in it) it else "minecraft:$it" }
        return """{"criteria":{"worldscript":{"trigger":"minecraft:impossible"}},"display":{"icon":{"$iconKey":"$normalizedItemId"},"title":$titleComponent,"description":$descriptionComponent,"frame":"$frame","show_toast":true,"announce_to_chat":false,"hidden":true}}"""
    }

    fun usesDataComponentItemFormat(version: String): Boolean {
        val match = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""").find(version) ?: return false
        val major = match.groupValues[1].toIntOrNull() ?: return false
        val minor = match.groupValues[2].toIntOrNull() ?: return false
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        return major > 1 || minor > 20 || (minor == 20 && patch >= 5)
    }

    private fun plainComponent(value: String): String = """{"text":"${escape(value)}"}"""

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
