package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.RegionParticleDefinition
import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.UUID

/** Text-based editor for region properties and event actions. */
class RegionChatEditor(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val presets: ActionPresetCatalog,
): Listener {
    private val input = mutableMapOf<UUID, EditorPendingInput>()
    private val lang = com.worldscript.foundation.Lang(plugin)
    private val inputTimeoutMillis: Long
        get() = plugin.config.getLong("editor.input-timeout-seconds", 120).coerceIn(15, 600) * 1000

    private fun editorText(key: String, fallback: String): String = lang.textWithLocalFallback("editor-$key", fallback)

    private fun editorMessage(key: String, fallback: String, vararg replacements: Pair<String, Any?>): String {
        var message = editorText(key, fallback)
        replacements.forEach { (name, value) -> message = message.replace("%$name%", value?.toString() ?: "") }
        return message
    }

    private fun sendEditor(player: Player, key: String, fallback: String, vararg replacements: Pair<String, Any?>) {
        player.sendMessage(color(editorMessage(key, fallback, *replacements)))
    }

    fun open(player: Player, regionId: String, section: String = "main") {
        input.remove(player.uniqueId)
        val region = regions.find(regionId) ?: run {
            sendEditor(player, "region-not-found", "&cRegion not found: &f%region%", "region" to regionId)
            return
        }

        EditorRoute.mutation(section)?.let { mutation ->
            return when (mutation.operation) {
                EditorOperation.STATUS -> cycleStatus(player, region)
                EditorOperation.TOGGLE -> toggleEvent(player, region, mutation.payload)
                EditorOperation.COOLDOWN -> adjustCooldown(player, region, mutation.payload)
                EditorOperation.MODE -> toggleMode(player, region, mutation.payload)
                EditorOperation.SOUND -> soundControl(player, region, mutation.payload)
                EditorOperation.SELECT -> selectParameter(player, region, mutation.payload)
                EditorOperation.PARTICLE -> particleControl(player, region, mutation.payload)
                EditorOperation.SET -> setInput(player, region, mutation.payload)
                EditorOperation.REMOVE -> removeAction(player, region, mutation.payload)
            }
        }

        header(player, region, section)
        when {
            section == "main" -> main(player, region)
            section == "data" -> data(player, region)
            section == "variables" -> variables(player, region)
            section == "events" -> events(player, region)
            section == "particles" -> particles(player, region)
            section.startsWith("add:") -> addPreset(player, region, section.removePrefix("add:"))
            section.startsWith("action:") -> action(player, region, section.removePrefix("action:"))
            else -> event(player, region, section)
        }
        footer(player, region, section)
    }

    fun reset() = input.clear()

    fun close(player: Player) {
        input.remove(player.uniqueId)
    }

    private fun header(player: Player, region: RegionDefinition, section: String) {
        player.sendMessage(color(editorText("header", "&6Unit &8> &e%id% &8> &f%name%").replace("%id%", region.id).replace("%name%", region.displayName)))
        player.sendMessage(color(editorText("meta", "&7ID &f%id% &8· &7World &f%world%").replace("%id%", region.id).replace("%world%", region.worldName)))
        spacer(player)
        player.sendMessage(color(editorText("context", "&8Observer &f(1) &8· &7Editor &fRegion content &8· &7Page &f%page%").replace("%page%", pageName(section))))
        spacer(player)
        operationRow(player,
            ChatEditorButton(editorText("tab-identity", "&e[Properties]"), editorText("hint-identity", "&7View region overview"), "/ws edit ${region.id} main"),
            ChatEditorButton(editorText("tab-data", "&e[Data]"), editorText("hint-data", "&7View region data"), "/ws edit ${region.id} data"),
            ChatEditorButton(editorText("tab-variables", "&b[Variables]"), editorText("hint-variables", "&7View region variables"), "/ws edit ${region.id} variables"),
            ChatEditorButton(editorText("tab-events", "&a[Events]"), editorText("hint-events", "&7Edit region events"), "/ws edit ${region.id} events"),
            ChatEditorButton(editorText("tab-particles", "&d[Particles]"), editorText("hint-particles", "&7Edit region atmosphere"), "/ws edit ${region.id} particles"),
        )
        operationRow(player,
            ChatEditorButton(editorText("refresh", "&7[Refresh]"), editorText("hint-refresh", "&7Reload this page"), "/ws edit ${region.id} $section"),
            ChatEditorButton(editorText("close", "&c[Close]"), editorText("hint-close", "&7Close the chat editor"), "/ws edit close"),
        )
        spacer(player)
        player.sendMessage(color("&8&m----------------------------------------"))
    }

    private fun main(player: Player, region: RegionDefinition) {
        group(player, editorText("group-identity", "&6Properties"))
        property(player, editorText("label-status", "&e[Region status]"), statusText(region), editorText("button-cycle", "&e[Cycle]"), "/ws edit ${region.id} status:next")
        property(player, editorText("label-parent", "&eParent region"), region.parentId?.let { regions.find(it)?.displayName ?: it } ?: editorText("value-none", "None"), "&8—")
        property(player, editorText("label-children", "&eChild regions"), "${childCount(region)}", "&8—")
        property(player, editorText("label-inheritance", "&eInheritance"), if (region.inheritParent) editorText("value-inherited", "Inherited from parent") else editorText("value-independent", "Local configuration"), "&8—")

    }

    private fun data(player: Player, region: RegionDefinition) {
        group(player, editorText("group-data", "&eData"))
        property(player, editorText("label-bounds", "&fBounds"), boundsText(region), "&8—")
        property(player, editorText("label-content-id", "&fContent ID"), region.contentId.ifBlank { editorText("value-unset", "Not set") }, "&8—")
        property(player, editorText("label-priority", "&fPriority"), region.priority.toString(), "&8—")
        property(player, editorText("label-world", "&fWorld"), region.worldName, "&8—")
        property(player, editorText("label-role", "&fRegion role"), region.role.name.lowercase(Locale.ROOT), "&8—")
    }

    private fun variables(player: Player, region: RegionDefinition) {
        group(player, editorText("group-variables", "&bVariables"))
        property(player, editorText("label-variable-count", "&bVariables"), region.variables.size.toString(), "&8—")
        property(player, editorText("label-parent-name", "&bParent name"), region.parentId?.let { regions.find(it)?.displayName } ?: editorText("value-none", "None"), editorText("value-hud", "&8HUD"))
        property(player, editorText("label-current-name", "&bCurrent name"), region.displayName, editorText("value-hud", "&8HUD"))
        region.variables.toSortedMap().forEach { (key, value) ->
            property(player, "&b$key", value.ifBlank { editorText("value-unset", "Not set") }, editorText("button-config", "&8[Config]"))
        }
    }

    private fun events(player: Player, region: RegionDefinition) {
        group(player, editorText("group-events", "&aEvents and feedback"))
        RegionEventMenu.entries.forEach { menu ->
            val script = regions.effective(region.id)?.events?.get(menu.type)
            val status = if (script?.enabled == false) editorText("value-disabled", "Disabled") else editorText("value-enabled", "Enabled")
            val actionCount = editorMessage("value-actions", "%count% action(s)", "count" to (script?.actions?.size ?: 0))
            property(player, eventLabel(menu), "$status &8| &f$actionCount", editorText("button-open", "&a[Open]"), "/ws edit ${region.id} ${menu.key}")
        }
    }

    private fun event(player: Player, region: RegionDefinition, key: String) {
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val script = regions.effective(region.id)?.events?.get(menu.type)
        group(player, "&e${eventLabel(menu)}")
        property(player, editorText("label-enabled", "&e[Enabled]"), if (script?.enabled == false) editorText("value-disabled", "Disabled") else editorText("value-enabled", "Enabled"), if (script?.enabled == false) editorText("button-open", "&a[Open]") else editorText("button-close", "&c[Close]"), "/ws edit ${region.id} ${menu.key} toggle")
        property(player, editorText("label-mode", "&b[Trigger mode]"), mode(script), editorText("button-cycle", "&e[Cycle]"), "/ws edit ${region.id} ${menu.key} mode:next")
        stepper(player, editorText("label-cooldown", "&e[Cooldown]"), "${script?.cooldownSeconds ?: 0}s", "&c[-5]", "/ws edit ${region.id} ${menu.key} cooldown:-5", "&a[+5]", "/ws edit ${region.id} ${menu.key} cooldown:5")

        group(player, editorText("action-list", "&6Actions"))
        operation(player, editorText("add-action", "&a[+ Add action]"), editorText("hint-add-action", "&7Add an action without replacing existing actions"), "/ws edit ${region.id} add:$key")
        if (script?.actions.isNullOrEmpty()) {
            property(player, editorText("label-actions", "&8Actions"), editorText("empty-actions", "Not configured"), "&8—")
        } else {
            script?.actions?.forEachIndexed { index, action ->
                property(player, "&f[${index + 1}]", actionLabel(action), editorText("button-edit", "&e[Edit]"), "/ws edit ${region.id} ${menu.key} action:$index")
            }
        }
    }

    private fun addPreset(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val key = parts.firstOrNull() ?: return open(player, region.id, "events")
        if (parts.size == 2) {
            val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
            val action = presets.create(parts[1]) ?: return open(player, region.id, "add:$key")
            regions.updateEvent(region.id, menu.type) { it.copy(actions = it.actions + action) }
            sendEditor(player, "action-added", "&aAction added: &f%value%", "value" to parts[1])
            return open(player, region.id, key)
        }
        group(player, editorText("group-add-action", "&6Add action"))
        if (presets.all().isEmpty()) {
            property(player, editorText("label-preset-library", "&8[Preset library]"), editorText("empty-actions", "Not configured"), editorText("button-back", "&7[Back]"), "/ws edit ${region.id} $key")
        } else {
            presets.all().forEach { preset ->
                property(player, "&b[${presetLabel(preset)}]", preset.type.name.lowercase(Locale.ROOT), editorText("button-add", "&a[Add]"), "/ws edit ${region.id} add:$key:${preset.id}")
            }
        }
        group(player, editorText("group-operations", "&7Operations"))
        operation(player, editorText("button-back", "&7[Back]"), editorText("hint-back-event", "&7Return to event settings"), "/ws edit ${region.id} $key")
    }

    private fun action(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val key = parts.firstOrNull() ?: return open(player, region.id, "events")
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return open(player, region.id, key)
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val action = regions.effective(region.id)?.events?.get(menu.type)?.actions?.getOrNull(index) ?: return open(player, region.id, key)

        group(player, editorText("group-profile", "&6Action profile"))
        property(player, editorText("label-owner", "&7Event"), eventLabel(menu), "&8—")
        property(player, editorText("label-action-type", "&7Action type"), actionLabel(action), "&8—")
        if (action.type == ActionType.SOUND) soundProperties(player, region, key, index, action)

        group(player, editorText("group-parameters", "&bAction parameters"))
        if (action.parameters.isEmpty()) {
            property(player, editorText("label-action-value", "&bAction value"), action.value.ifBlank { editorText("value-unset", "Not set") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:value")
        } else {
            action.parameters.toSortedMap().forEach { (name, current) ->
                val extra = if (name == "region") listOf(
                    ChatEditorButton(editorText("button-previous", "&e[Previous]"), editorText("hint-previous-region", "&7Select the previous region"), "/ws edit ${region.id} $key select:$index:region:prev"),
                    ChatEditorButton(editorText("button-next", "&e[Next]"), editorText("hint-next-region", "&7Select the next region"), "/ws edit ${region.id} $key select:$index:region:next"),
                ) else emptyList()
                property(player, "&b${parameterLabel(name)}", current.ifBlank { editorText("value-unset", "Not set") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:$name", extra)
            }
        }
        group(player, editorText("group-danger", "&cDanger zone"))
        operation(player, editorText("button-delete", "&c[Delete action]"), editorText("hint-delete-action", "&cDelete this action"), "/ws edit ${region.id} $key remove:$index")
    }

    private fun soundProperties(player: Player, region: RegionDefinition, key: String, index: Int, action: ActionDefinition) {
        val sound = action.parameters["sound"] ?: action.value
        group(player, editorText("group-sound", "&3Sound"))
        property(player, editorText("label-sound", "&3[Sound]"), sound.ifBlank { editorText("value-unset", "Not set") }, editorText("button-listen", "&3[Listen]"), "/ws edit ${region.id} $key sound:$index:play", listOf(
            ChatEditorButton(editorText("button-previous", "&e[Previous]"), editorText("hint-previous-sound", "&7Select the previous sound"), "/ws edit ${region.id} $key sound:$index:prev"),
            ChatEditorButton(editorText("button-next", "&e[Next]"), editorText("hint-next-sound", "&7Select the next sound"), "/ws edit ${region.id} $key sound:$index:next"),
        ))
        stepper(player, editorText("label-volume", "&e[Volume]"), action.parameters["volume"] ?: "1.0", "&c[-0.1]", "/ws edit ${region.id} $key sound:$index:volume-down", "&a[+0.1]", "/ws edit ${region.id} $key sound:$index:volume-up")
        stepper(player, editorText("label-pitch", "&e[Pitch]"), action.parameters["pitch"] ?: "1.0", "&c[-0.1]", "/ws edit ${region.id} $key sound:$index:pitch-down", "&a[+0.1]", "/ws edit ${region.id} $key sound:$index:pitch-up")
    }

    private fun setInput(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val eventKey = target.eventKey
        val index = target.index
        val parameter = target.arguments.firstOrNull() ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == eventKey }?.type ?: return
        if (!ensureLocalAction(region, type, index)) return
        input[player.uniqueId] = EditorPendingInput(region.id, eventKey, type, index, parameter, System.currentTimeMillis())
        sendEditor(player, "input-prompt", "&6Editing &f%parameter% &8| &7Enter a value or type &ccancel &7to stop.", "parameter" to parameterLabel(parameter))
    }

    private fun removeAction(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val key = target.eventKey
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val index = target.index
        if (region.events[type]?.actions?.getOrNull(index) == null && regions.effective(region.id)?.events?.get(type)?.actions?.getOrNull(index) == null) return
        input[player.uniqueId] = EditorPendingInput(region.id, key, type, index, "__delete__", System.currentTimeMillis())
        sendEditor(player, "delete-confirm", "&cDelete action %index%? &7Type &fconfirm &7in chat. Anything else cancels.", "index" to index)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val pending = input.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = event.message
        if (System.currentTimeMillis() - pending.createdAt > inputTimeoutMillis) {
            event.player.sendMessage(color(editorText("input-expired", "&eThe editor session expired. Open the action again.")))
            return
        }
        if (isCancellation(message)) {
            sendEditor(event.player, "edit-cancelled", "&7Edit cancelled.")
            return
        }
        val player = event.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val region = regions.find(pending.regionId) ?: return@Runnable
            if (pending.parameter == "__delete__") {
                if (!isConfirmation(message)) {
                    sendEditor(player, "delete-cancelled", "&7Deletion cancelled.")
                    return@Runnable
                }
                if (ensureLocalAction(region, pending.type, pending.index)) {
                    regions.removeAction(pending.regionId, pending.type, pending.index)
                    sendEditor(player, "action-deleted", "&aAction deleted.")
                    open(player, pending.regionId, pending.eventKey)
                }
                return@Runnable
            }
            if (!ensureLocalAction(region, pending.type, pending.index)) return@Runnable
            val action = regions.find(pending.regionId)?.events?.get(pending.type)?.actions?.getOrNull(pending.index) ?: return@Runnable
            val updated = if (pending.parameter == "value") action.copy(value = message) else action.copy(parameters = action.parameters + (pending.parameter to message))
            regions.updateAction(pending.regionId, pending.type, pending.index, updated)
            sendEditor(player, "parameter-saved", "&aParameter saved: &f%parameter% &7= &f%value%", "parameter" to parameterLabel(pending.parameter), "value" to message)
            open(player, pending.regionId, pending.eventKey)
        })
    }

    private fun toggleEvent(player: Player, region: RegionDefinition, key: String) {
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        regions.toggleEvent(region.id, type)
        val enabled = regions.effective(region.id)?.events?.get(type)?.enabled != false
        sendEditor(player, "event-toggled", "&a%event% &7is now %state%.&8 Refresh to view the full page.", "event" to eventLabel(RegionEventMenu.entries.first { it.key == key }), "state" to if (enabled) editorText("value-enabled", "Enabled") else editorText("value-disabled", "Disabled"))
    }

    private fun adjustCooldown(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val type = RegionEventMenu.entries.firstOrNull { it.key == parts[0] }?.type ?: return
        val delta = parts.getOrNull(1)?.toLongOrNull() ?: return
        regions.updateEvent(region.id, type) { it.copy(cooldownSeconds = (it.cooldownSeconds + delta).coerceAtLeast(0)) }
        val cooldown = regions.effective(region.id)?.events?.get(type)?.cooldownSeconds ?: 0
        sendEditor(player, "cooldown-saved", "&aCooldown &f%value%s &7saved.", "value" to cooldown)
    }

    private fun toggleMode(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val type = RegionEventMenu.entries.firstOrNull { it.key == parts[0] }?.type ?: return
        regions.updateEvent(region.id, type) {
            when {
                !it.firstEntryOnly && !it.repeatEntryOnly -> it.copy(firstEntryOnly = true)
                it.firstEntryOnly -> it.copy(firstEntryOnly = false, repeatEntryOnly = true)
                else -> it.copy(repeatEntryOnly = false)
            }
        }
        val script = regions.effective(region.id)?.events?.get(type)
        sendEditor(player, "mode-saved", "&aTrigger mode &f%value% &7saved.", "value" to mode(script))
    }

    private fun mode(script: com.worldscript.foundation.model.ScriptDefinition?): String = when {
        script?.firstEntryOnly == true -> editorText("value-mode-first", "First entry")
        script?.repeatEntryOnly == true -> editorText("value-mode-repeat", "Repeat entry")
        else -> editorText("value-mode-always", "Always")
    }

    private fun soundControl(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val key = target.eventKey
        val index = target.index
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val action = regions.effective(region.id)?.events?.get(type)?.actions?.getOrNull(index) ?: return
        val current = action.parameters["sound"] ?: action.value
        val sounds = EditorCatalog.soundChoices.filter { BukkitCompatibility.resolveSound(it) != null }.ifEmpty { listOf(current) }
        val currentIndex = sounds.indexOf(current).coerceAtLeast(0)
        when (target.arguments.firstOrNull()) {
            "prev", "next" -> {
                val delta = if (target.arguments.first() == "next") 1 else -1
                val selected = sounds[(currentIndex + delta + sounds.size) % sounds.size]
                updateActionParameter(region, key, index, action.copy(parameters = action.parameters + ("sound" to selected)))
                sendEditor(player, "sound-selected", "&aSound changed to &f%value%", "value" to selected)
            }
            "play" -> {
                val sound = BukkitCompatibility.resolveSound(current)
                if (sound == null) sendEditor(player, "sound-unsupported", "&cThis server does not support sound: &f%value%", "value" to current)
                else {
                    player.playSound(player.location, sound, action.parameters["volume"]?.toFloatOrNull() ?: 1f, action.parameters["pitch"]?.toFloatOrNull() ?: 1f)
                    sendEditor(player, "sound-preview", "&aPreviewed sound: &f%value%", "value" to current)
                }
            }
            "volume-down", "volume-up", "pitch-down", "pitch-up" -> {
                val operation = target.arguments.first()
                val name = if (operation.startsWith("volume")) "volume" else "pitch"
                val delta = if (operation.endsWith("up")) 0.1 else -0.1
                val next = ((action.parameters[name]?.toDoubleOrNull() ?: 1.0) + delta).coerceIn(0.0, 2.0)
                updateActionParameter(region, key, index, action.copy(parameters = action.parameters + (name to "%.1f".format(Locale.US, next))))
                sendEditor(player, "number-saved", "&a%name% &f%value% &7saved.", "name" to parameterLabel(name), "value" to "%.1f".format(Locale.US, next))
            }
        }
    }

    private fun selectParameter(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val key = target.eventKey
        val index = target.index
        val parameter = target.arguments.getOrNull(0) ?: return
        val direction = target.arguments.getOrNull(1) ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val action = regions.effective(region.id)?.events?.get(type)?.actions?.getOrNull(index) ?: return
        val options = regions.all().map { it.id }.sorted()
        if (options.isEmpty()) return
        val current = options.indexOf(action.parameters[parameter]).coerceAtLeast(0)
        val delta = if (direction == "next") 1 else -1
        val selected = options[(current + delta + options.size) % options.size]
        updateActionParameter(region, key, index, action.copy(parameters = action.parameters + (parameter to selected)))
        sendEditor(player, "selection-saved", "&a%name% &f%value% &7saved.", "name" to parameterLabel(parameter), "value" to selected)
    }

    private fun updateActionParameter(region: RegionDefinition, key: String, index: Int, action: ActionDefinition) {
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        if (!ensureLocalAction(region, type, index)) return
        regions.updateAction(region.id, type, index, action)
    }

    private fun ensureLocalAction(region: RegionDefinition, type: RegionEventType, index: Int): Boolean {
        if (region.events[type]?.actions?.getOrNull(index) != null) return true
        val inheritedActions = regions.effective(region.id)?.events?.get(type)?.actions ?: return false
        if (index !in inheritedActions.indices) return false
        regions.updateEvent(region.id, type) { local -> local.copy(actions = inheritedActions) }
        return true
    }

    private fun particles(player: Player, region: RegionDefinition) {
        val local = region.particle
        val particle = local ?: regions.effective(region.id)?.particle ?: RegionParticleDefinition(enabled = false)
        group(player, editorText("group-atmosphere", "&dRegion atmosphere"))
        property(player, editorText("label-particle-state", "&d[Display state]"), if (particle.enabled) editorText("value-enabled", "Enabled") else editorText("value-disabled", "Disabled"), if (particle.enabled) editorText("button-close", "&c[Close]") else editorText("button-open", "&a[Open]"), "/ws edit ${region.id} particle:toggle")
        property(player, editorText("label-preset", "&d[Visual style]"), particle.preset, editorText("button-readonly", "&8[Read-only]"))
        property(player, editorText("label-particle-type", "&d[Particle type]"), particle.type, editorText("button-preview", "&d[Preview]"), "/ws edit ${region.id} particle:preview", listOf(
            ChatEditorButton(editorText("button-previous", "&e[Previous]"), editorText("hint-previous-particle", "&7Select the previous particle"), "/ws edit ${region.id} particle:prev"),
            ChatEditorButton(editorText("button-next", "&e[Next]"), editorText("hint-next-particle", "&7Select the next particle"), "/ws edit ${region.id} particle:next"),
        ))
        stepper(player, editorText("label-particle-count", "&e[Particle count]"), particle.count.toString(), "&c[-1]", "/ws edit ${region.id} particle:count:-1", "&a[+1]", "/ws edit ${region.id} particle:count:1")
        stepper(player, editorText("label-particle-interval", "&e[Spawn interval]"), "${particle.intervalTicks} tick", "&c[-5]", "/ws edit ${region.id} particle:interval:-5", "&a[+5]", "/ws edit ${region.id} particle:interval:5")
        property(player, editorText("label-particle-spread", "&b[Spread]"), "${particle.spreadX}, ${particle.spreadY}, ${particle.spreadZ}", editorText("button-config", "&8[Config]"))
        if (local == null && region.parentId != null) sendEditor(player, "particle-inherited", "&8Particles are inherited from the parent; the first edit creates a local override.")
    }

    private fun particleControl(player: Player, region: RegionDefinition, value: String) {
        val current = region.particle ?: regions.effective(region.id)?.particle ?: RegionParticleDefinition(enabled = false)
        val parts = value.split(':', limit = 2)
        if (parts[0] == "preview") {
            previewParticle(player, current)
            return
        }
        val updated = when (parts[0]) {
            "toggle" -> current.copy(enabled = !current.enabled)
            "prev", "next" -> {
                val choices = EditorCatalog.particleChoices.filter { BukkitCompatibility.resolveParticle(it) != null }.ifEmpty { listOf(current.type) }
                val index = choices.indexOf(current.type).coerceAtLeast(0)
                val delta = if (parts[0] == "next") 1 else -1
                current.copy(type = choices[(index + delta + choices.size) % choices.size])
            }
            "count" -> current.copy(count = (current.count + (parts.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(1, 64))
            "interval" -> current.copy(intervalTicks = (current.intervalTicks + (parts.getOrNull(1)?.toLongOrNull() ?: 0)).coerceAtLeast(1))
            else -> current
        }
        regions.updateParticle(region.id, updated)
        val message = when (parts[0]) {
            "toggle" -> editorMessage("particle-toggle", "Particles are now %state%.", "state" to if (updated.enabled) editorText("value-enabled", "Enabled") else editorText("value-disabled", "Disabled"))
            "prev", "next" -> editorMessage("particle-type-saved", "Particle type changed to %value%.", "value" to updated.type)
            "count" -> editorMessage("particle-count-saved", "Particle count changed to %value%.", "value" to updated.count)
            "interval" -> editorMessage("particle-interval-saved", "Spawn interval changed to %value% tick.", "value" to updated.intervalTicks)
            else -> editorText("particle-saved", "Particle settings saved.")
        }
        sendEditor(player, "particle-updated", "&a%value% &8| &7Refresh to view the full page.", "value" to message)
    }

    private fun previewParticle(player: Player, definition: RegionParticleDefinition) {
        val particle = BukkitCompatibility.resolveParticle(definition.type)
        if (particle == null) {
            sendEditor(player, "particle-unsupported", "&cThis server does not support particle: &f%value%", "value" to definition.type)
            return
        }
        player.spawnParticle(particle, player.location.clone().add(0.0, 1.0, 0.0), definition.count, definition.spreadX, definition.spreadY, definition.spreadZ, definition.speed)
        sendEditor(player, "particle-preview", "&aPreviewed particle: &f%value%", "value" to definition.type)
    }

    private fun cycleStatus(player: Player, region: RegionDefinition) {
        val statuses = listOf(GlobalRegionStatus.OPEN, GlobalRegionStatus.DANGEROUS, GlobalRegionStatus.PEACEFUL, GlobalRegionStatus.LOCKED)
        val current = statuses.indexOf(region.statuses.firstOrNull()).coerceAtLeast(0)
        val next = statuses[(current + 1) % statuses.size]
        statuses.filter { it != next }.forEach { regions.setStatus(region.id, it, false) }
        regions.setStatus(region.id, next, true)
        sendEditor(player, "status-saved", "&aRegion status changed to &f%value% &7.", "value" to statusText(region.copy(statuses = setOf(next))))
    }

    private fun footer(player: Player, region: RegionDefinition, section: String) {
        val back = when {
            section == "main" -> "main"
            section == "data" || section == "variables" || section == "events" || section == "particles" -> "main"
            section.startsWith("action:") -> section.removePrefix("action:").substringBefore(':')
            section.startsWith("add:") -> section.removePrefix("add:").substringBefore(':')
            RegionEventMenu.entries.any { it.key == section } -> "events"
            else -> "main"
        }
        spacer(player)
        player.sendMessage(color("&8&m----------------------------------------"))
        operationRow(player,
            ChatEditorButton(editorText("button-back", "&7[Back]"), editorText("hint-back", "&7Return to the previous page"), "/ws edit ${region.id} $back"),
            ChatEditorButton(editorText("button-page", "&f[1 / 1]"), editorText("hint-current-page", "&7Current page"), "/ws edit ${region.id} $section"),
            ChatEditorButton(editorText("refresh", "&7[Refresh]"), editorText("hint-refresh", "&7Reload this page"), "/ws edit ${region.id} $section"),
        )
        sendEditor(player, "footer-hint", "&8Hint &7Click colored text to operate; text parameters open chat input.")
    }

    private fun group(player: Player, title: String) {
        spacer(player)
        player.sendMessage(color("$title"))
    }

    private fun spacer(player: Player) {
        player.sendMessage("")
    }

    private fun property(player: Player, label: String, value: String, actionLabel: String, action: String? = null, extra: List<ChatEditorButton> = emptyList()) {
        val components = mutableListOf<BaseComponent>()
        components += TextComponent(color("$label &f$value"))
        if (action == null) components += TextComponent(color(" &8$actionLabel"))
        else {
            components += TextComponent(" ")
            components += button(actionLabel, editorMessage("hint-run-action", "&7Run: %action%", "action" to plain(actionLabel)), action).toList()
        }
        extra.forEach {
            components += TextComponent(color(" &8| "))
            components += button(it.label, it.hover, it.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    private fun stepper(player: Player, label: String, value: String, decreaseLabel: String, decrease: String, increaseLabel: String, increase: String) {
        property(player, label, value, decreaseLabel, decrease, listOf(ChatEditorButton(increaseLabel, editorText("hint-increase-value", "&7Increase value"), increase)))
    }

    private fun operation(player: Player, label: String, hover: String, command: String) {
        player.spigot().sendMessage(*button(label, hover, command))
    }

    private fun operationRow(player: Player, vararg buttons: ChatEditorButton) {
        val components = mutableListOf<BaseComponent>()
        buttons.forEachIndexed { index, button ->
            if (index > 0) components += TextComponent("  ")
            components += button(button.label, button.hover, button.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    private fun actionLabel(action: ActionDefinition): String {
        action.preset?.let { presetId ->
            presets.all().firstOrNull { it.id.equals(presetId, true) }?.let { return presetLabel(it) }
        }
        return EditorCatalog.actionFallbacks[action.type]?.let { editorText("action-${action.type.name.lowercase(Locale.ROOT)}", it) }
            ?: action.type.name.lowercase(Locale.ROOT).replace('_', ' ')
    }

    private fun parameterLabel(name: String): String = editorText("parameter-${name.lowercase(Locale.ROOT)}", name)

    private fun presetLabel(preset: ActionPreset): String = editorText("preset-${preset.id}", preset.name)

    private fun eventLabel(menu: RegionEventMenu): String = editorText("event-${menu.key}", menu.key)

    private fun boundsText(region: RegionDefinition): String {
        val min = region.bounds.min
        val max = region.bounds.max
        return "(${min.x}, ${min.y}, ${min.z}) -> (${max.x}, ${max.y}, ${max.z})"
    }
    private fun childCount(region: RegionDefinition): Int = regions.all().count { it.parentId.equals(region.id, true) }
    private fun statusText(region: RegionDefinition): String = when (region.statuses.firstOrNull()) {
        GlobalRegionStatus.OPEN -> editorText("value-status-open", "Open")
        GlobalRegionStatus.DANGEROUS -> editorText("value-status-dangerous", "Dangerous")
        GlobalRegionStatus.PEACEFUL -> editorText("value-status-peaceful", "Peaceful")
        GlobalRegionStatus.LOCKED -> editorText("value-status-locked", "Locked")
        null -> editorText("value-status-open", "Open")
    }

    private fun pageName(section: String): String = when {
        section == "main" -> editorText("page-main", "Properties")
        section == "data" -> editorText("page-data", "Data")
        section == "variables" -> editorText("page-variables", "Variables")
        section == "events" -> editorText("page-events", "Events")
        section == "particles" -> editorText("page-particles", "Region atmosphere")
        section.startsWith("action:") -> editorText("page-action", "Action parameters")
        section.startsWith("add:") -> editorText("page-add-action", "Add action")
        RegionEventMenu.entries.any { it.key == section } -> eventLabel(RegionEventMenu.entries.first { it.key == section })
        else -> editorText("page-editor", "Region editor")
    }

    private fun button(label: String, hover: String, command: String): Array<BaseComponent> = ComponentBuilder(color(label))
        .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(hover)))
        .create()

    private fun color(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
    private fun plain(value: String): String = ChatColor.stripColor(color(value)) ?: value
    private fun isCancellation(value: String): Boolean = value.equals("cancel", true) || value.equals("取消", true)
    private fun isConfirmation(value: String): Boolean = value.equals("confirm", true) || value.equals("确认", true)

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        input.remove(event.player.uniqueId)
    }

}
