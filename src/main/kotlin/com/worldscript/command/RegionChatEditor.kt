@file:Suppress("DEPRECATION")

package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.Lang
import com.worldscript.foundation.SettingsLayout
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.script_actions.ToastService
import org.bukkit.Bukkit
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
    private val toasts: ToastService,
): Listener {
    private val sessions = EditorSessionStore()
    private val lang = Lang(plugin)
    private val renderer = EditorRenderer(lang)
    private val actionStore = RegionActionStore(regions)
    private val conditionController = EditorConditionController(regions, renderer, sessions, ::open, ::enableGlobalSetting, ::actionLabel)
    private val discoveryController = EditorDiscoveryController(regions, renderer, sessions, ::open, ::enableGlobalSetting, ::actionLabel, ::previewDiscoveryToast)
    private val particleController = EditorParticleController(regions, renderer, ::sendEditor, ::editorMessage)
    private val inputTimeoutMillis: Long
        get() = plugin.config.getLong("editor.input-timeout-seconds", 120).coerceIn(15, 600) * 1000
    private val pageTopSpacerLines: Int
        get() = plugin.config.getInt("editor.page-top-spacer-lines", 12).coerceIn(0, 40)

    private fun editorText(key: String, fallback: String): String = renderer.text(key, fallback)

    private fun editorMessage(key: String, fallback: String, vararg replacements: Pair<String, Any?>): String {
        return renderer.message(key, fallback, *replacements)
    }

    private fun sendEditor(player: Player, key: String, fallback: String, vararg replacements: Pair<String, Any?>) {
        renderer.send(player, key, fallback, *replacements)
    }

    fun open(player: Player, regionId: String, section: String = "main") {
        sessions.clear(player.uniqueId)
        val region = regions.find(regionId) ?: run {
            sendEditor(player, "region-not-found", "&cRegion not found: &f%region%", "region" to regionId)
            return
        }

        EditorRoute.mutation(section)?.let { mutation ->
            return when (mutation.operation) {
                EditorOperation.STATUS -> cycleStatus(player, region)
                EditorOperation.NAME -> nameInput(player, region)
                EditorOperation.VARIABLE -> variableControl(player, region, mutation.payload)
                EditorOperation.TOGGLE -> toggleEvent(player, region, mutation.payload)
                EditorOperation.COOLDOWN -> adjustCooldown(player, region, mutation.payload)
                EditorOperation.MODE -> toggleMode(player, region, mutation.payload)
                EditorOperation.SOUND -> soundControl(player, region, mutation.payload)
                EditorOperation.SELECT -> selectParameter(player, region, mutation.payload)
                EditorOperation.TOAST -> toastControl(player, region, mutation.payload)
                EditorOperation.PARTICLE -> particleController.control(player, region, mutation.payload)
                EditorOperation.DISCOVERY -> discoveryController.control(player, region, mutation.payload)
                EditorOperation.CONDITION -> conditionController.control(player, region, mutation.payload)
                EditorOperation.SET -> setInput(player, region, mutation.payload)
                EditorOperation.REMOVE -> removeAction(player, region, mutation.payload)
            }
        }

        repeat(pageTopSpacerLines) { spacer(player) }
        header(player, region, section)
        when {
            section == "main" -> main(player, region)
            section == "data" -> data(player, region)
            section == "variables" -> variables(player, region)
            section == "events" -> events(player, region)
            section == "particles" -> particleController.render(player, region)
            section == "discovery" -> discoveryController.render(player, region)
            section == "conditions" -> conditionController.render(player, region)
            section.startsWith("add:") -> addPreset(player, region, section.removePrefix("add:"))
            section.startsWith("action:") -> action(player, region, section.removePrefix("action:"))
            else -> event(player, region, section)
        }
        footer(player, region, section)
    }

    fun reset() = sessions.clearAll()

    fun close(player: Player) {
        sessions.clear(player.uniqueId)
    }

    private fun header(player: Player, region: RegionDefinition, section: String) {
        player.sendMessage(color(editorText("header", "&6Region Editor &8› &f%name%").replace("%id%", region.id).replace("%name%", region.displayName)))
        player.sendMessage(color(editorText("meta", "&7ID &f%id% &8· &7World &f%world%").replace("%id%", region.id).replace("%world%", region.worldName)))
        spacer(player)
        player.sendMessage(color(editorText("context", "&8Observer &f(1) &8· &7Page &f%page%").replace("%page%", pageName(section))))
        spacer(player)
        operationRow(player,
            ChatEditorButton(editorText("tab-identity", "&e[Overview]"), editorText("hint-identity", "&7View region overview"), "/ws edit ${region.id} main"),
            ChatEditorButton(editorText("tab-data", "&f[Advanced]"), editorText("hint-data", "&7Open advanced settings"), "/ws edit ${region.id} data"),
            ChatEditorButton(editorText("tab-events", "&f[Events]"), editorText("hint-events", "&7Edit region events"), "/ws edit ${region.id} events"),
            ChatEditorButton(editorText("tab-discovery", "&f[Discovery]"), editorText("hint-discovery", "&7Edit first discovery feedback"), "/ws edit ${region.id} discovery"),
            ChatEditorButton(editorText("tab-conditions", "&f[Conditions]"), editorText("hint-conditions", "&7Edit entry conditions"), "/ws edit ${region.id} conditions"),
        )
        spacer(player)
        renderer.divider(player)
    }

    private fun main(player: Player, region: RegionDefinition) {
        group(player, editorText("group-identity", "&6Properties"))
        property(player, editorText("label-status", "&e[Region status]"), statusText(region), editorText("button-cycle", "&e[Cycle]"), "/ws edit ${region.id} status:next")
        property(player, editorText("label-display-name", "&eDisplay name"), region.displayName, editorText("button-input", "&e[Edit]"), "/ws edit ${region.id} name")
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
        group(player, editorText("group-advanced", "&8Advanced"))
        property(player, editorText("label-custom-variables", "&bCustom variables"), "${region.variables.size}", editorText("button-open", "&a[Open]"), "/ws edit ${region.id} variables")
        property(player, editorText("label-particle-state", "&dAtmosphere"), if ((region.particle ?: regions.effective(region.id)?.particle)?.enabled == true) editorText("value-enabled", "Enabled") else editorText("value-disabled", "Disabled"), editorText("button-open", "&a[Open]"), "/ws edit ${region.id} particles")
    }

    private fun variables(player: Player, region: RegionDefinition) {
        group(player, editorText("group-variables", "&bVariables"))
        val effective = regions.effective(region.id)
        val inheritedCount = (effective?.variables?.keys.orEmpty() - region.variables.keys).size
        property(player, editorText("label-variable-count", "&bVariables"), "${region.variables.size} &8local, $inheritedCount inherited", editorText("button-add", "&a[Add]"), "/ws edit ${region.id} variable add")
        property(player, editorText("label-parent-name", "&bParent name"), region.parentId?.let { regions.find(it)?.displayName } ?: editorText("value-none", "None"), editorText("value-hud", "&8HUD"))
        property(player, editorText("label-current-name", "&bCurrent name"), region.displayName, editorText("value-hud", "&8HUD"))
        effective?.variables?.toSortedMap(String.CASE_INSENSITIVE_ORDER)?.forEach { (key, value) ->
            val source = regions.variableSource(region.id, key)
            val sourceText = if (source.equals(region.id, true)) editorText("value-local", "local") else editorMessage("value-inherited-from", "inherited from %source%", "source" to (source ?: "parent"))
            property(player, "&b$key", "$value &8($sourceText)", if (source.equals(region.id, true)) editorText("button-edit", "&e[Edit]") else editorText("button-readonly", "&8[Inherited]"), if (source.equals(region.id, true)) "/ws edit ${region.id} variable edit:$key" else null, if (source.equals(region.id, true)) listOf(ChatEditorButton(editorText("button-delete", "&c[Delete]"), editorText("hint-delete-variable", "&cRemove local override"), "/ws edit ${region.id} variable remove:$key")) else emptyList())
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
            if (key.equals("discovery", true)) {
                val action = presets.create(parts[1]) ?: return open(player, region.id, "add:discovery")
                actionStore.add(region.id, key, action)
                sendEditor(player, "action-added", "&aAction added: &f%value%", "value" to parts[1])
                return open(player, region.id, "discovery")
            }
            if (!actionStore.isKnown(key)) return open(player, region.id, "events")
            val action = presets.create(parts[1]) ?: return open(player, region.id, "add:$key")
            actionStore.add(region.id, key, action)
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
        operation(player, editorText("button-back", "&7[Back]"), editorText("hint-back-event", "&7Return to event settings"), "/ws edit ${region.id} ${if (key.equals("discovery", true)) "discovery" else key}")
    }

    private fun action(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val key = parts.firstOrNull() ?: return open(player, region.id, "events")
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return open(player, region.id, key)
        if (key.equals("discovery", true)) {
            val action = actionStore.get(region, key, index) ?: return open(player, region.id, "discovery")
            actionProfile(player, region, key, index, action, editorText("group-discovery-reward", "&6First discovery rewards"))
            return
        }
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val action = actionStore.get(region, key, index) ?: return open(player, region.id, key)

        group(player, editorText("group-profile", "&6Action profile"))
        property(player, editorText("label-owner", "&7Event"), eventLabel(menu), "&8—")
        property(player, editorText("label-action-type", "&7Action type"), actionLabel(action), "&8—")
        if (action.type == ActionType.SOUND) soundProperties(player, region, key, index, action)

        group(player, editorText("group-parameters", "&bAction parameters"))
        if (action.type == ActionType.TOAST) {
            toastProperties(player, region, key, index, action)
            group(player, editorText("group-danger", "&cDanger zone"))
            operation(player, editorText("button-delete", "&c[Delete action]"), editorText("hint-delete-action", "&cDelete this action"), "/ws edit ${region.id} $key remove:$index")
            return
        }
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

    private fun actionProfile(player: Player, region: RegionDefinition, key: String, index: Int, action: ActionDefinition, owner: String) {
        group(player, owner)
        property(player, editorText("label-action-type", "&7Action type"), actionLabel(action), "&8—")
        if (action.type == ActionType.SOUND) soundProperties(player, region, key, index, action)
        group(player, editorText("group-parameters", "&bAction parameters"))
        if (action.type == ActionType.TOAST) {
            toastProperties(player, region, key, index, action)
            group(player, editorText("group-danger", "&cDanger zone"))
            operation(player, editorText("button-delete", "&c[Delete action]"), editorText("hint-delete-action", "&cDelete this action"), "/ws edit ${region.id} $key remove:$index")
            return
        }
        if (action.parameters.isEmpty()) {
            property(player, editorText("label-action-value", "&bAction value"), action.value.ifBlank { editorText("value-unset", "Not set") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:value")
        } else {
            action.parameters.toSortedMap().forEach { (name, current) ->
                property(player, "&b${parameterLabel(name)}", current.ifBlank { editorText("value-unset", "Not set") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:$name")
            }
        }
        group(player, editorText("group-danger", "&cDanger zone"))
        operation(player, editorText("button-delete", "&c[Delete action]"), editorText("hint-delete-action", "&cDelete this action"), "/ws edit ${region.id} $key remove:$index")
    }

    private fun toastProperties(player: Player, region: RegionDefinition, key: String, index: Int, action: ActionDefinition) {
        val source = action.parameters["source"].orEmpty()
        val title = action.parameters["title"].orEmpty()
        val description = action.parameters["description"].orEmpty()
        val icon = action.parameters["icon"].orEmpty()
        val frame = action.parameters["frame"].orEmpty().ifBlank { editorText("value-global-default", "Global default") }
        property(player, editorText("editor-parameter-source", "&7Toast source"), source.ifBlank { editorText("value-global-default", "Global default") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:source")
        property(player, editorText("editor-parameter-toast-title", "&7Toast title"), title.ifBlank { editorText("value-global-default", "Global default") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:title")
        property(player, editorText("editor-parameter-toast-description", "&7Toast description"), description.ifBlank { editorText("value-global-default", "Global default") }, editorText("button-input", "&e[Input]"), "/ws edit ${region.id} $key set:$index:description")
        property(player, editorText("editor-parameter-toast-icon", "&7Toast icon"), icon.ifBlank { editorText("value-region-default", "Region default") }, editorText("button-use-held-item", "&b[Use held item]"), "/ws edit ${region.id} $key toast:$index:held-item", listOf(
            ChatEditorButton(editorText("button-input", "&e[Input]"), editorText("hint-toast-icon-input", "&7Enter a Bukkit material name"), "/ws edit ${region.id} $key set:$index:icon"),
            ChatEditorButton(editorText("button-reset", "&c[Reset]"), editorText("hint-toast-reset", "&7Use the region default icon again"), "/ws edit ${region.id} $key toast:$index:reset-icon"),
        ))
        property(player, editorText("editor-parameter-frame", "&7Toast frame"), frame, editorText("button-cycle", "&e[Cycle]"), "/ws edit ${region.id} $key toast:$index:next-frame")
        operation(player, editorText("button-preview", "&d[Preview]"), editorText("hint-toast-preview", "&7Show this Toast only to you"), "/ws edit ${region.id} $key toast:$index:preview")
    }

    private fun toastControl(player: Player, region: RegionDefinition, payload: String) {
        val parts = payload.split(':')
        val key = parts.firstOrNull() ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        val action = actionAt(region, key, index) ?: return
        when (parts.getOrNull(2)?.lowercase()) {
            "held-item" -> {
                val item = player.inventory.itemInMainHand
                if (item.type == org.bukkit.Material.AIR) sendEditor(player, "toast-icon-held-empty", "&cHold an item in your main hand first.")
                else updateActionParameter(region, key, index, action.copy(parameters = action.parameters + ("icon" to item.type.name)))
            }
            "reset-icon" -> updateActionParameter(region, key, index, action.copy(parameters = action.parameters - "icon"))
            "next-frame" -> {
                val frames = listOf("", "task", "goal", "challenge")
                val current = frames.indexOf(action.parameters["frame"]).coerceAtLeast(0)
                updateActionParameter(region, key, index, action.copy(parameters = action.parameters + ("frame" to frames[(current + 1) % frames.size])))
            }
            "preview" -> previewActionToast(player, region, action)
        }
        open(player, region.id, key)
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
        if (eventKey.equals("discovery", true)) {
            if (!actionStore.ensureLocal(region, eventKey, index)) return
            sessions.begin(player.uniqueId, EditorPendingInput(region.id, eventKey, RegionEventType.ENTER, index, parameter, System.currentTimeMillis()))
            sendEditor(player, "input-prompt", "&6Editing &f%parameter% &8| &7Enter a value or type &ccancel &7to stop.", "parameter" to parameterLabel(parameter))
            return
        }
        val type = RegionEventMenu.entries.firstOrNull { it.key == eventKey }?.type ?: return
        if (!actionStore.ensureLocal(region, eventKey, index)) return
        sessions.begin(player.uniqueId, EditorPendingInput(region.id, eventKey, type, index, parameter, System.currentTimeMillis()))
        sendEditor(player, "input-prompt", "&6Editing &f%parameter% &8| &7Enter a value or type &ccancel &7to stop.", "parameter" to parameterLabel(parameter))
    }

    private fun nameInput(player: Player, region: RegionDefinition) {
        sessions.begin(player.uniqueId, EditorPendingInput(region.id, "main", RegionEventType.ENTER, -1, "__region_name__", System.currentTimeMillis()))
        sendEditor(player, "name-input-prompt", "&6Editing display name &8| &7Enter a new name, or type &ccancel &7to stop.")
    }

    private fun variableControl(player: Player, region: RegionDefinition, payload: String) {
        val parts = payload.split(':', limit = 2)
        when (parts.firstOrNull()?.lowercase(Locale.ROOT)) {
            "add" -> {
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, "variables", RegionEventType.ENTER, -1, "__variable_add__", System.currentTimeMillis()))
                sendEditor(player, "variable-add-prompt", "&6Add variable &8| &7Enter key=value, or type &ccancel &7to stop.")
            }
            "edit" -> {
                val key = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, key, RegionEventType.ENTER, -1, "__variable_value__", System.currentTimeMillis()))
                sendEditor(player, "variable-edit-prompt", "&6Editing variable &f%key% &8| &7Enter a new value, or type &ccancel &7to stop.", "key" to key)
            }
            "remove" -> {
                val key = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return
                sessions.begin(player.uniqueId, EditorPendingInput(region.id, key, RegionEventType.ENTER, -1, "__variable_delete__", System.currentTimeMillis()))
                sendEditor(player, "variable-delete-confirm", "&cRemove local variable %key%? &7Type &fconfirm &7in chat. Anything else cancels.", "key" to key)
            }
        }
    }

    private fun removeAction(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val key = target.eventKey
        if (key.equals("discovery", true)) {
            if (actionStore.get(region, key, target.index) == null) return
            sessions.begin(player.uniqueId, EditorPendingInput(region.id, key, RegionEventType.ENTER, target.index, "__delete__", System.currentTimeMillis()))
            sendEditor(player, "delete-confirm", "&cDelete action %index%? &7Type &fconfirm &7in chat. Anything else cancels.", "index" to target.index)
            return
        }
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val index = target.index
        if (actionStore.get(region, key, index) == null) return
        sessions.begin(player.uniqueId, EditorPendingInput(region.id, key, type, index, "__delete__", System.currentTimeMillis()))
        sendEditor(player, "delete-confirm", "&cDelete action %index%? &7Type &fconfirm &7in chat. Anything else cancels.", "index" to index)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val pending = sessions.take(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = event.message
        if (System.currentTimeMillis() - pending.createdAt > inputTimeoutMillis) {
            event.player.sendMessage(color(editorText("input-expired", "&eThe editor session expired. Open the action again.")))
            return
        }
        if (EditorInputParser.isCancellation(message)) {
            sendEditor(event.player, "edit-cancelled", "&7Edit cancelled.")
            return
        }
        val player = event.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val region = regions.find(pending.regionId) ?: return@Runnable
            if (pending.parameter == "__delete__") {
                if (!EditorInputParser.isConfirmation(message)) {
                    sendEditor(player, "delete-cancelled", "&7Deletion cancelled.")
                    return@Runnable
                }
                if (pending.eventKey.equals("discovery", true)) {
                    if (actionStore.ensureLocal(region, pending.eventKey, pending.index)) {
                        actionStore.remove(pending.regionId, pending.eventKey, pending.index)
                        sendEditor(player, "action-deleted", "&aAction deleted.")
                        open(player, pending.regionId, "discovery")
                    }
                } else if (actionStore.ensureLocal(region, pending.eventKey, pending.index)) {
                    actionStore.remove(pending.regionId, pending.eventKey, pending.index)
                    sendEditor(player, "action-deleted", "&aAction deleted.")
                    open(player, pending.regionId, pending.eventKey)
                }
                return@Runnable
            }
            if (pending.parameter == "__variable_delete__") {
                if (!EditorInputParser.isConfirmation(message)) {
                    sendEditor(player, "variable-delete-cancelled", "&7Variable removal cancelled.")
                    return@Runnable
                }
                if (regions.removeVariable(pending.regionId, pending.eventKey)) {
                    sendEditor(player, "variable-removed", "&aRemoved local variable: &f%key%", "key" to pending.eventKey)
                    open(player, pending.regionId, "variables")
                }
                return@Runnable
            }
            if (pending.parameter == "__region_name__") {
                if (regions.setDisplayName(pending.regionId, message)) {
                    sendEditor(player, "name-saved", "&aDisplay name saved: &f%value%", "value" to message)
                    open(player, pending.regionId, "main")
                } else {
                    sendEditor(player, "name-invalid", "&cDisplay name cannot be empty.")
                }
                return@Runnable
            }
            if (pending.parameter == "__discovery_title__") {
                regions.updateDiscovery(pending.regionId) { it.copy(title = message) }
                sendEditor(player, "discovery-title-saved", "&aDiscovery Title saved.")
                open(player, pending.regionId, "discovery")
                return@Runnable
            }
            if (pending.parameter == "__discovery_subtitle__") {
                regions.updateDiscovery(pending.regionId) { it.copy(subtitle = message) }
                sendEditor(player, "discovery-subtitle-saved", "&aDiscovery subtitle saved.")
                open(player, pending.regionId, "discovery")
                return@Runnable
            }
            if (pending.parameter == "__discovery_toast_title__") {
                saveToastDiscovery(pending.regionId) { it.copy(toastTitle = message) }
                sendEditor(player, "toast-title-saved", "&aToast title saved.")
                open(player, pending.regionId, "discovery")
                return@Runnable
            }
            if (pending.parameter == "__discovery_toast_description__") {
                saveToastDiscovery(pending.regionId) { it.copy(toastDescription = message) }
                sendEditor(player, "toast-description-saved", "&aToast description saved.")
                open(player, pending.regionId, "discovery")
                return@Runnable
            }
            if (pending.parameter == "__discovery_toast_icon__") {
                val material = com.worldscript.foundation.MaterialResolver.find(message)
                if (material == null) {
                    sendEditor(player, "toast-icon-invalid", "&cThis server does not support material: &f%value%", "value" to message)
                } else {
                    saveToastDiscovery(pending.regionId) { it.copy(toastIcon = material.name) }
                    sendEditor(player, "toast-icon-saved", "&aToast icon saved: &f%value%", "value" to material.name)
                    open(player, pending.regionId, "discovery")
                }
                return@Runnable
            }
            if (pending.parameter == "__discovery_sound__") {
                if (BukkitCompatibility.resolveSound(message) == null) {
                    sendEditor(player, "discovery-sound-invalid", "&cThis server does not support sound: &f%value%", "value" to message)
                } else {
                    regions.updateDiscovery(pending.regionId) { it.copy(sound = message) }
                    sendEditor(player, "discovery-sound-saved", "&aDiscovery sound saved: &f%value%", "value" to message)
                    open(player, pending.regionId, "discovery")
                }
                return@Runnable
            }
            if (pending.parameter == "__condition_add__") {
                val condition = EditorInputParser.condition(message)
                if (condition == null) {
                    sendEditor(player, "condition-invalid", "&cInvalid condition. Use %placeholder% >= number or permission: node.")
                } else {
                    regions.updateEvent(pending.regionId, RegionEventType.ENTER) { it.copy(conditions = it.conditions + condition) }
                    enableGlobalSetting("conditions.enabled")
                    sendEditor(player, "condition-saved", "&aCondition added.")
                    open(player, pending.regionId, "conditions")
                }
                return@Runnable
            }
            if (pending.parameter == "__condition_delete__") {
                if (!EditorInputParser.isConfirmation(message)) {
                    sendEditor(player, "delete-cancelled", "&7Deletion cancelled.")
                    return@Runnable
                }
                val index = pending.index
                val existing = regions.effective(pending.regionId)?.events?.get(RegionEventType.ENTER)?.conditions.orEmpty()
                if (index in existing.indices) {
                    regions.updateEvent(pending.regionId, RegionEventType.ENTER) {
                        it.copy(conditions = it.conditions.toMutableList().also { values -> values.removeAt(index) })
                    }
                    sendEditor(player, "condition-removed", "&aCondition removed.")
                }
                open(player, pending.regionId, "conditions")
                return@Runnable
            }
            if (pending.parameter == "__condition_edit__") {
                val condition = EditorInputParser.condition(message)
                if (condition == null) {
                    sendEditor(player, "condition-invalid", "&cInvalid condition. Use a placeholder comparison or permission: node.")
                } else {
                    val index = pending.index
                    val existing = regions.effective(pending.regionId)?.events?.get(RegionEventType.ENTER)?.conditions.orEmpty()
                    if (index in existing.indices) {
                        regions.updateEvent(pending.regionId, RegionEventType.ENTER) {
                            it.copy(conditions = it.conditions.toMutableList().also { values -> values[index] = condition })
                        }
                        sendEditor(player, "condition-saved", "&aCondition updated.")
                        open(player, pending.regionId, "conditions")
                    }
                }
                return@Runnable
            }
            if (pending.parameter == "__condition_failure_action_add__") {
                val action = EditorInputParser.discoveryAction(message)
                if (action == null) sendEditor(player, "condition-failure-action-invalid", "&cInvalid action. Use type=value.")
                else {
                    regions.updateEvent(pending.regionId, RegionEventType.ENTER) { it.copy(conditionFailureActions = it.conditionFailureActions + action) }
                    sendEditor(player, "condition-failure-action-saved", "&aFailure action added.")
                    open(player, pending.regionId, "conditions")
                }
                return@Runnable
            }
            if (pending.parameter == "__condition_failure_action_delete__") {
                if (!EditorInputParser.isConfirmation(message)) {
                    sendEditor(player, "delete-cancelled", "&7Deletion cancelled.")
                    return@Runnable
                }
                regions.updateEvent(pending.regionId, RegionEventType.ENTER) { script ->
                    script.copy(conditionFailureActions = script.conditionFailureActions.toMutableList().also { actions ->
                        if (pending.index in actions.indices) actions.removeAt(pending.index)
                    })
                }
                sendEditor(player, "condition-failure-action-removed", "&aFailure action removed.")
                open(player, pending.regionId, "conditions")
                return@Runnable
            }
            if (pending.parameter == "__condition_failure_action_edit__") {
                val action = EditorInputParser.discoveryAction(message)
                if (action == null) sendEditor(player, "condition-failure-action-invalid", "&cInvalid action. Use type=value.")
                else {
                    regions.updateEvent(pending.regionId, RegionEventType.ENTER) { script ->
                        script.copy(conditionFailureActions = script.conditionFailureActions.toMutableList().also { actions ->
                            if (pending.index in actions.indices) actions[pending.index] = action
                        })
                    }
                    sendEditor(player, "condition-failure-action-saved", "&aFailure action saved.")
                    open(player, pending.regionId, "conditions")
                }
                return@Runnable
            }
            if (pending.parameter == "__discovery_reward_add__") {
                val action = EditorInputParser.discoveryAction(message)
                if (action == null) {
                    sendEditor(player, "discovery-reward-invalid", "&cInvalid reward. Enter a command directly or use type=value, for example console-command=say Welcome.")
                } else {
                    regions.updateDiscovery(pending.regionId) { it.copy(actions = it.configuredActions() + action) }
                    sendEditor(player, "discovery-reward-saved", "&aDiscovery reward added.")
                    open(player, pending.regionId, "discovery")
                }
                return@Runnable
            }
            if (pending.parameter == "__variable_add__") {
                val parts = message.split('=', limit = 2)
                val key = parts.firstOrNull()?.trim().orEmpty()
                val value = parts.getOrNull(1)?.trim()
                if (key.isBlank() || value == null || !regions.setVariable(pending.regionId, key, value)) {
                    sendEditor(player, "variable-invalid", "&cUse the format key=value; the key cannot be empty.")
                } else {
                    sendEditor(player, "variable-saved", "&aVariable saved: &f%key% &7= &f%value%", "key" to key, "value" to value)
                    open(player, pending.regionId, "variables")
                }
                return@Runnable
            }
            if (pending.parameter == "__variable_value__") {
                if (regions.setVariable(pending.regionId, pending.eventKey, message)) {
                    sendEditor(player, "variable-saved", "&aVariable saved: &f%key% &7= &f%value%", "key" to pending.eventKey, "value" to message)
                    open(player, pending.regionId, "variables")
                }
                return@Runnable
            }
            if (pending.eventKey.equals("discovery", true)) {
                if (!actionStore.ensureLocal(region, pending.eventKey, pending.index)) return@Runnable
                val action = actionStore.get(region, pending.eventKey, pending.index) ?: return@Runnable
                val updated = if (pending.parameter == "value") action.copy(value = message) else action.copy(parameters = action.parameters + (pending.parameter to message))
                actionStore.update(pending.regionId, pending.eventKey, pending.index, updated)
                sendEditor(player, "parameter-saved", "&aParameter saved: &f%parameter% &7= &f%value%", "parameter" to parameterLabel(pending.parameter), "value" to message)
                open(player, pending.regionId, pending.eventKey)
                return@Runnable
            }
            if (!actionStore.ensureLocal(region, pending.eventKey, pending.index)) return@Runnable
            val action = actionStore.get(region, pending.eventKey, pending.index) ?: return@Runnable
            val updated = if (pending.parameter == "value") action.copy(value = message) else action.copy(parameters = action.parameters + (pending.parameter to message))
            actionStore.update(pending.regionId, pending.eventKey, pending.index, updated)
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
        val action = actionAt(region, key, index) ?: return
        handleSoundAction(player, region, key, index, action, target.arguments.firstOrNull())
    }

    private fun handleSoundAction(player: Player, region: RegionDefinition, key: String, index: Int, action: ActionDefinition, operation: String?) {
        val current = action.parameters["sound"] ?: action.value
        val sounds = EditorCatalog.soundChoices.filter { BukkitCompatibility.resolveSound(it) != null }.ifEmpty { listOf(current) }
        val currentIndex = sounds.indexOf(current).coerceAtLeast(0)
        when (operation) {
            "prev", "next" -> {
                val delta = if (operation == "next") 1 else -1
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
                val delta = if (operation.endsWith("up")) 0.1 else -0.1
                val name = if (operation.startsWith("volume")) "volume" else "pitch"
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
        if (key.equals("discovery", true)) {
            val action = actionStore.get(region, key, index) ?: return
            val options = regions.all().map { it.id }.sorted()
            val current = options.indexOf(action.parameters[parameter]).coerceAtLeast(0)
            val delta = if (direction == "next") 1 else -1
            actionStore.update(region.id, key, index, action.copy(parameters = action.parameters + (parameter to options[(current + delta + options.size) % options.size])))
            return
        }
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
        if (key.equals("discovery", true)) {
            actionStore.update(region.id, key, index, action)
            return
        }
        if (!actionStore.ensureLocal(region, key, index)) return
        actionStore.update(region.id, key, index, action)
    }

    private fun previewDiscoveryToast(player: Player, region: RegionDefinition) {
        val discovery = region.discovery
        toasts.showDiscoveryPreview(
            player, region.id, region.displayName, region.role,
            discovery?.toastTitle.orEmpty(), discovery?.toastDescription.orEmpty(), discovery?.toastIcon.orEmpty(),
        )
    }

    private fun previewActionToast(player: Player, requestedRegion: RegionDefinition, action: ActionDefinition) {
        val region = regions.effective(requestedRegion.id) ?: requestedRegion
        val values = action.parameters
        toasts.showActionPreview(
            player, region.id, region.displayName, region.role,
            values["title"].orEmpty(),
            values["description"].orEmpty(),
            values["icon"].orEmpty(),
            values["frame"].orEmpty(),
        )
        sendEditor(player, "toast-preview-sent", "&aToast preview sent.")
    }

    private fun actionAt(region: RegionDefinition, key: String, index: Int): ActionDefinition? =
        actionStore.get(region, key, index)

    private fun enableGlobalSetting(path: String) {
        if (!plugin.config.getBoolean(path, false)) {
            plugin.config.set(path, true)
            SettingsLayout.saveForPath(plugin, path)
        }
    }

    /** Any saved regional Toast value also enables the two prerequisites needed to display it. */
    private fun saveToastDiscovery(regionId: String, update: (com.worldscript.foundation.model.DiscoveryDefinition) -> com.worldscript.foundation.model.DiscoveryDefinition) {
        regions.updateDiscovery(regionId) { update(it.copy(enabled = true, toastEnabled = true)) }
        enableGlobalSetting("discovery.enabled")
        enableGlobalSetting("discovery.display.toast.enabled")
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
            section == "variables" -> "data"
            section == "data" || section == "events" || section == "particles" || section == "discovery" || section == "conditions" -> "main"
            section.startsWith("action:") -> section.removePrefix("action:").substringBefore(':')
            section.startsWith("add:") -> section.removePrefix("add:").substringBefore(':')
            RegionEventMenu.entries.any { it.key == section } -> "events"
            else -> "main"
        }
        spacer(player)
        renderer.divider(player)
        operationRow(player,
            ChatEditorButton(editorText("button-back", "&7[Back]"), editorText("hint-back", "&7Return to the previous page"), "/ws edit ${region.id} $back"),
            ChatEditorButton(editorText("refresh", "&7[Refresh]"), editorText("hint-refresh", "&7Reload this page"), "/ws edit ${region.id} $section"),
            ChatEditorButton(editorText("close", "&c[Close]"), editorText("hint-close", "&7Close the chat editor"), "/ws edit close"),
        )
        sendEditor(player, "footer-hint", "&8Hint &7Click colored text to operate; text parameters open chat input.")
    }

    private fun group(player: Player, title: String) {
        renderer.group(player, title)
    }

    private fun spacer(player: Player) {
        renderer.spacer(player)
    }

    private fun property(player: Player, label: String, value: String, actionLabel: String, action: String? = null, extra: List<ChatEditorButton> = emptyList()) {
        renderer.property(player, label, value, actionLabel, action, extra)
    }

    private fun stepper(player: Player, label: String, value: String, decreaseLabel: String, decrease: String, increaseLabel: String, increase: String) {
        renderer.stepper(player, label, value, decreaseLabel, decrease, increaseLabel, increase)
    }

    private fun operation(player: Player, label: String, hover: String, command: String) {
        renderer.operation(player, label, hover, command)
    }

    private fun operationRow(player: Player, vararg buttons: ChatEditorButton) {
        renderer.operationRow(player, *buttons)
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
        section == "discovery" -> editorText("page-discovery", "Discovery")
        section == "conditions" -> editorText("page-conditions", "Conditions")
        section.startsWith("action:") -> editorText("page-action", "Action parameters")
        section.startsWith("add:") -> editorText("page-add-action", "Add action")
        RegionEventMenu.entries.any { it.key == section } -> eventLabel(RegionEventMenu.entries.first { it.key == section })
        else -> editorText("page-editor", "Region editor")
    }

    private fun color(value: String): String = renderer.color(value)
    private fun plain(value: String): String = renderer.plain(value)
    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        sessions.clear(event.player.uniqueId)
    }

}
