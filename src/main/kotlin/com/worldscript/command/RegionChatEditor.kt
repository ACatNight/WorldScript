package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.RegionParticleDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Particle
import org.bukkit.Sound
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
    private val input = mutableMapOf<UUID, PendingInput>()
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
        player.sendMessage(color(editorText("header", "&6公共单位 &8> &e%id% &8> &f%name%").replace("%id%", region.id).replace("%name%", region.displayName)))
        player.sendMessage(color(editorText("meta", "&7ID &f%id% &8· &7世界 &f%world%").replace("%id%", region.id).replace("%world%", region.worldName)))
        spacer(player)
        player.sendMessage(color(editorText("context", "&8观察者 &f(1) &8· &7编辑器 &f区域内容 &8· &7当前页 &f%page%").replace("%page%", pageName(section))))
        spacer(player)
        operationRow(player,
            Button(editorText("tab-identity", "&e[公共特性]"), editorText("hint-identity", "查看区域概览"), "/ws edit ${region.id} main"),
            Button(editorText("tab-data", "&e[公共数据]"), editorText("hint-data", "查看区域数据"), "/ws edit ${region.id} data"),
            Button(editorText("tab-variables", "&b[区域变量]"), editorText("hint-variables", "查看区域变量"), "/ws edit ${region.id} variables"),
            Button(editorText("tab-events", "&a[事件]"), editorText("hint-events", "编辑区域事件"), "/ws edit ${region.id} events"),
            Button(editorText("tab-particles", "&d[粒子]"), editorText("hint-particles", "编辑区域氛围"), "/ws edit ${region.id} particles"),
        )
        operationRow(player,
            Button(editorText("refresh", "&7[刷新]"), editorText("hint-refresh", "重新读取当前页面"), "/ws edit ${region.id} $section"),
            Button(editorText("close", "&c[关闭]"), editorText("hint-close", "关闭聊天编辑器"), "/ws edit close"),
        )
        spacer(player)
        player.sendMessage(color("&8&m----------------------------------------"))
    }

    private fun main(player: Player, region: RegionDefinition) {
        group(player, "&6公共特性")
        property(player, "&e[区域状态]", statusText(region), "&e[切换]", "/ws edit ${region.id} status:next")
        property(player, "&e父区域", region.parentId?.let { regions.find(it)?.displayName ?: it } ?: "无", "&8—")
        property(player, "&e子区域", "${childCount(region)} 个", "&8—")
        property(player, "&e继承关系", if (region.inheritParent) "继承父区域" else "独立配置", "&8—")

    }

    private fun data(player: Player, region: RegionDefinition) {
        group(player, "&e公共数据")
        property(player, "&f坐标范围", boundsText(region), "&8—")
        property(player, "&f内容 ID", region.contentId.ifBlank { "未设置" }, "&8—")
        property(player, "&f优先级", region.priority.toString(), "&8—")
        property(player, "&f世界", region.worldName, "&8—")
        property(player, "&f区域角色", region.role.name.lowercase(Locale.ROOT), "&8—")
    }

    private fun variables(player: Player, region: RegionDefinition) {
        group(player, "&b区域变量")
        property(player, "&b变量数量", "${region.variables.size} 个", "&8—")
        property(player, "&b父区域名称", region.parentId?.let { regions.find(it)?.displayName } ?: "无", "&8HUD")
        property(player, "&b当前区域名称", region.displayName, "&8HUD")
        region.variables.toSortedMap().forEach { (key, value) ->
            property(player, "&b$key", value.ifBlank { "未设置" }, "&8配置")
        }
    }

    private fun events(player: Player, region: RegionDefinition) {
        group(player, "&a事件与反馈")
        RegionEventMenu.entries.forEach { menu ->
            val script = regions.effective(region.id)?.events?.get(menu.type)
            val status = if (script?.enabled == false) "关闭" else "启用"
            property(player, eventLabel(menu), "$status &8| &f${script?.actions?.size ?: 0} 个动作", "&a[打开]", "/ws edit ${region.id} ${menu.key}")
        }
    }

    private fun event(player: Player, region: RegionDefinition, key: String) {
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val script = regions.effective(region.id)?.events?.get(menu.type)
        group(player, "&e${eventLabel(menu)}")
        property(player, "&e[启用状态]", if (script?.enabled == false) "关闭" else "启用", if (script?.enabled == false) "&a[打开]" else "&c[关闭]", "/ws edit ${region.id} ${menu.key} toggle")
        property(player, "&b[触发模式]", mode(script), "&b[切换]", "/ws edit ${region.id} ${menu.key} mode:next")
        stepper(player, "&e[冷却时间]", "${script?.cooldownSeconds ?: 0}s", "&c[-5]", "/ws edit ${region.id} ${menu.key} cooldown:-5", "&a[+5]", "/ws edit ${region.id} ${menu.key} cooldown:5")

        group(player, editorText("action-list", "&6动作列表"))
        operation(player, editorText("add-action", "&a[+ 添加动作]"), editorText("hint-add-action", "添加一个动作，不会覆盖已有动作"), "/ws edit ${region.id} add:$key")
        if (script?.actions.isNullOrEmpty()) {
            property(player, "&8动作", editorText("empty-actions", "尚未配置"), "&8—")
        } else {
            script?.actions?.forEachIndexed { index, action ->
                property(player, "&f[${index + 1}]", actionLabel(action), "&e[编辑]", "/ws edit ${region.id} ${menu.key} action:$index")
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
        group(player, "&6添加动作")
        if (presets.all().isEmpty()) {
            property(player, "&8[预设库]", "暂无可用动作", "&7[返回]", "/ws edit ${region.id} $key")
        } else {
            presets.all().forEach { preset ->
                property(player, "&b[${preset.name}]", preset.type.name.lowercase(Locale.ROOT), "&a[添加]", "/ws edit ${region.id} add:$key:${preset.id}")
            }
        }
        group(player, "&7操作")
        operation(player, "&7[返回]", "返回事件设置", "/ws edit ${region.id} $key")
    }

    private fun action(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val key = parts.firstOrNull() ?: return open(player, region.id, "events")
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return open(player, region.id, key)
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val action = regions.effective(region.id)?.events?.get(menu.type)?.actions?.getOrNull(index) ?: return open(player, region.id, key)

        group(player, "&6动作档案")
        property(player, "&7所属事件", eventLabel(menu), "&8—")
        property(player, "&7动作类型", actionLabel(action), "&8—")
        if (action.type == ActionType.SOUND) soundProperties(player, region, key, index, action)

        group(player, "&b动作参数")
        if (action.parameters.isEmpty()) {
            property(player, "&b动作内容", action.value.ifBlank { "未设置" }, "&e[输入]", "/ws edit ${region.id} $key set:$index:value")
        } else {
            action.parameters.toSortedMap().forEach { (name, current) ->
                val extra = if (name == "region") listOf(
                    Button("&e[上一项]", "选择上一个区域", "/ws edit ${region.id} $key select:$index:region:prev"),
                    Button("&e[下一项]", "选择下一个区域", "/ws edit ${region.id} $key select:$index:region:next"),
                ) else emptyList()
                property(player, "&b${parameterLabel(name)}", current.ifBlank { "未设置" }, "&e[输入]", "/ws edit ${region.id} $key set:$index:$name", extra)
            }
        }
        group(player, "&c危险操作")
        operation(player, "&c[删除动作]", "删除这个动作", "/ws edit ${region.id} $key remove:$index")
    }

    private fun soundProperties(player: Player, region: RegionDefinition, key: String, index: Int, action: ActionDefinition) {
        val sound = action.parameters["sound"] ?: action.value
        group(player, "&3音效属性")
        property(player, "&3[音效]", sound.ifBlank { "未设置" }, "&3[试听]", "/ws edit ${region.id} $key sound:$index:play", listOf(
            Button("&3[上一项]", "选择上一种音效", "/ws edit ${region.id} $key sound:$index:prev"),
            Button("&3[下一项]", "选择下一种音效", "/ws edit ${region.id} $key sound:$index:next"),
        ))
        stepper(player, "&e[音量]", action.parameters["volume"] ?: "1.0", "&c[-0.1]", "/ws edit ${region.id} $key sound:$index:volume-down", "&a[+0.1]", "/ws edit ${region.id} $key sound:$index:volume-up")
        stepper(player, "&e[音调]", action.parameters["pitch"] ?: "1.0", "&c[-0.1]", "/ws edit ${region.id} $key sound:$index:pitch-down", "&a[+0.1]", "/ws edit ${region.id} $key sound:$index:pitch-up")
    }

    private fun setInput(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val eventKey = target.eventKey
        val index = target.index
        val parameter = target.arguments.firstOrNull() ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == eventKey }?.type ?: return
        if (!ensureLocalAction(region, type, index)) return
        input[player.uniqueId] = PendingInput(region.id, eventKey, type, index, parameter, System.currentTimeMillis())
        sendEditor(player, "input-prompt", "&6Editing &f%parameter% &8| &7Enter a value or type &ccancel &7to stop.", "parameter" to parameter)
    }

    private fun removeAction(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val key = target.eventKey
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val index = target.index
        if (region.events[type]?.actions?.getOrNull(index) == null && regions.effective(region.id)?.events?.get(type)?.actions?.getOrNull(index) == null) return
        input[player.uniqueId] = PendingInput(region.id, key, type, index, "__delete__", System.currentTimeMillis())
        sendEditor(player, "delete-confirm", "&cDelete action %index%? &7Type &fconfirm &7in chat. Anything else cancels.", "index" to index)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val pending = input.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val message = event.message
        if (System.currentTimeMillis() - pending.createdAt > inputTimeoutMillis) {
            event.player.sendMessage(color(editorText("input-expired", "&e编辑会话已超时，请重新打开对应动作。")))
            return
        }
        if (message.equals("取消", true)) {
            sendEditor(event.player, "edit-cancelled", "&7Edit cancelled.")
            return
        }
        val player = event.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val region = regions.find(pending.regionId) ?: return@Runnable
            if (pending.parameter == "__delete__") {
                if (!message.equals("确认", true)) {
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
            sendEditor(player, "parameter-saved", "&aParameter saved: &f%parameter% &7= &f%value%", "parameter" to pending.parameter, "value" to message)
            open(player, pending.regionId, pending.eventKey)
        })
    }

    private fun toggleEvent(player: Player, region: RegionDefinition, key: String) {
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        regions.toggleEvent(region.id, type)
        val enabled = regions.effective(region.id)?.events?.get(type)?.enabled != false
            sendEditor(player, "event-toggled", "&a%event% &7is now %state%.&8 Refresh to view the full page.", "event" to plain(key), "state" to if (enabled) "enabled" else "disabled")
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
        script?.firstEntryOnly == true -> "首次进入"
        script?.repeatEntryOnly == true -> "重复进入"
        else -> "每次触发"
    }

    private fun soundControl(player: Player, region: RegionDefinition, value: String) {
        val target = EditorActionRef.parse(value) ?: return
        val key = target.eventKey
        val index = target.index
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val action = regions.effective(region.id)?.events?.get(type)?.actions?.getOrNull(index) ?: return
        val current = action.parameters["sound"] ?: action.value
        val sounds = SOUND_CHOICES.filter { resolveSound(it) != null }.ifEmpty { listOf(current) }
        val currentIndex = sounds.indexOf(current).coerceAtLeast(0)
        when (target.arguments.firstOrNull()) {
            "prev", "next" -> {
                val delta = if (target.arguments.first() == "next") 1 else -1
                val selected = sounds[(currentIndex + delta + sounds.size) % sounds.size]
                updateActionParameter(region, key, index, action.copy(parameters = action.parameters + ("sound" to selected)))
                sendEditor(player, "sound-selected", "&aSound changed to &f%value%", "value" to selected)
            }
            "play" -> {
                val sound = resolveSound(current)
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

    private fun resolveSound(value: String): Sound? {
        val name = value.trim().uppercase(Locale.ROOT)
        return runCatching { Sound.valueOf(name) }.getOrNull() ?: when (name) {
            "BLOCK_NOTE_BLOCK_PLING" -> runCatching { Sound.valueOf("BLOCK_NOTE_PLING") }.getOrNull()
            "BLOCK_NOTE_PLING" -> runCatching { Sound.valueOf("BLOCK_NOTE_BLOCK_PLING") }.getOrNull()
            else -> null
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
        group(player, "&d区域氛围")
        property(player, "&d[显示状态]", if (particle.enabled) "启用" else "关闭", if (particle.enabled) "&c[关闭]" else "&a[打开]", "/ws edit ${region.id} particle:toggle")
        property(player, "&d[视觉样式]", particle.preset, "&8[只读]")
        property(player, "&d[粒子类型]", particle.type, "&d[预览]", "/ws edit ${region.id} particle:preview", listOf(
            Button("&d[上一项]", "选择上一种粒子", "/ws edit ${region.id} particle:prev"),
            Button("&d[下一项]", "选择下一种粒子", "/ws edit ${region.id} particle:next"),
        ))
        stepper(player, "&e[粒子数量]", particle.count.toString(), "&c[-1]", "/ws edit ${region.id} particle:count:-1", "&a[+1]", "/ws edit ${region.id} particle:count:1")
        stepper(player, "&e[生成间隔]", "${particle.intervalTicks} tick", "&c[-5]", "/ws edit ${region.id} particle:interval:-5", "&a[+5]", "/ws edit ${region.id} particle:interval:5")
        property(player, "&b[扩散范围]", "${particle.spreadX}, ${particle.spreadY}, ${particle.spreadZ}", "&8[配置文件]")
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
                val choices = PARTICLE_CHOICES.filter { runCatching { Particle.valueOf(it) }.isSuccess }.ifEmpty { listOf(current.type) }
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
            "toggle" -> "粒子${if (updated.enabled) "已启用" else "已关闭"}"
            "prev", "next" -> "粒子类型已切换为 ${updated.type}"
            "count" -> "粒子数量已调整为 ${updated.count}"
            "interval" -> "生成间隔已调整为 ${updated.intervalTicks} tick"
            else -> "粒子配置已保存"
        }
        sendEditor(player, "particle-updated", "&a%value% &8| &7Refresh to view the full page.", "value" to message)
    }

    private fun previewParticle(player: Player, definition: RegionParticleDefinition) {
        val particle = runCatching { Particle.valueOf(definition.type.uppercase(Locale.ROOT)) }.getOrNull()
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
            Button("&7[返回]", "返回上一级", "/ws edit ${region.id} $back"),
            Button("&f[1 / 1]", "当前页面", "/ws edit ${region.id} $section"),
            Button("&7[刷新]", "重新读取当前页面", "/ws edit ${region.id} $section"),
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

    private fun property(player: Player, label: String, value: String, actionLabel: String, action: String? = null, extra: List<Button> = emptyList()) {
        val components = mutableListOf<BaseComponent>()
        components += TextComponent(color("$label &f$value"))
        if (action == null) components += TextComponent(color(" &8$actionLabel"))
        else {
            components += TextComponent(" ")
            components += button(actionLabel, "执行：${plain(actionLabel)}", action).toList()
        }
        extra.forEach {
            components += TextComponent(color(" &8| "))
            components += button(it.label, it.hover, it.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    private fun stepper(player: Player, label: String, value: String, decreaseLabel: String, decrease: String, increaseLabel: String, increase: String) {
        property(player, label, value, decreaseLabel, decrease, listOf(Button(increaseLabel, "增加数值", increase)))
    }

    private fun operation(player: Player, label: String, hover: String, command: String) {
        player.spigot().sendMessage(*button(label, hover, command))
    }

    private fun operationRow(player: Player, vararg buttons: Button) {
        val components = mutableListOf<BaseComponent>()
        buttons.forEachIndexed { index, button ->
            if (index > 0) components += TextComponent("  ")
            components += button(button.label, button.hover, button.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    private fun actionLabel(action: ActionDefinition): String {
        action.preset?.let { presetId ->
            presets.all().firstOrNull { it.id.equals(presetId, true) }?.name?.let { return it }
        }
        return mapOf(
        ActionType.TEXT_DISPLAY to "标题显示",
        ActionType.SOUND to "音效",
        ActionType.MESSAGE to "聊天消息",
        ActionType.PLAYER_COMMAND to "玩家命令",
        ActionType.CONSOLE_COMMAND to "控制台命令",
        ActionType.TELEPORT to "传送",
        ActionType.KETHER to "Kether 脚本",
        ActionType.SET_VARIABLE to "设置变量",
        ActionType.SET_REGION_STATUS to "区域状态",
        ActionType.GIVE_ITEM to "给予物品",
        ActionType.GIVE_EXPERIENCE to "给予经验",
        ActionType.GIVE_MONEY to "给予金钱",
        ActionType.UNLOCK_REGION to "解锁区域",
        ActionType.COMPLETE_REGION to "完成区域",
        )[action.type]?.let { editorText("action-${action.type.name.lowercase(Locale.ROOT)}", it) }
            ?: action.type.name.lowercase(Locale.ROOT).replace('_', ' ')
    }

    private fun parameterLabel(name: String): String = mapOf(
        "sound" to "音效", "volume" to "音量", "pitch" to "音调",
        "title" to "标题", "subtitle" to "副标题", "text" to "消息内容",
        "command" to "命令", "region" to "目标区域", "material" to "物品",
        "amount" to "数量", "location" to "坐标", "key" to "变量名", "value" to "变量值",
    )[name.lowercase(Locale.ROOT)] ?: name

    private fun eventLabel(menu: RegionEventMenu): String =
        editorText("event-${menu.key}", plain(menu.label))

    private fun boundsText(region: RegionDefinition): String {
        val min = region.bounds.min
        val max = region.bounds.max
        return "(${min.x}, ${min.y}, ${min.z}) -> (${max.x}, ${max.y}, ${max.z})"
    }
    private fun childCount(region: RegionDefinition): Int = regions.all().count { it.parentId.equals(region.id, true) }
    private fun statusText(region: RegionDefinition): String = when (region.statuses.firstOrNull()) {
        GlobalRegionStatus.OPEN -> "开放"
        GlobalRegionStatus.DANGEROUS -> "危险"
        GlobalRegionStatus.PEACEFUL -> "和平"
        GlobalRegionStatus.LOCKED -> "锁定"
        null -> "开放"
    }

    private fun pageName(section: String): String = when {
        section == "main" -> "公共特性"
        section == "data" -> "公共数据"
        section == "variables" -> "区域变量"
        section == "events" -> "事件列表"
        section == "particles" -> "区域氛围"
        section.startsWith("action:") -> "动作参数"
        section.startsWith("add:") -> "添加动作"
        RegionEventMenu.entries.any { it.key == section } -> eventLabel(RegionEventMenu.entries.first { it.key == section })
        else -> "区域编辑"
    }

    private fun button(label: String, hover: String, command: String): Array<BaseComponent> = ComponentBuilder(color(label))
        .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(hover)))
        .create()

    private fun color(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
    private fun plain(value: String): String = ChatColor.stripColor(color(value)) ?: value

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) {
        input.remove(event.player.uniqueId)
    }

    private data class Button(val label: String, val hover: String, val command: String)
    private data class PendingInput(
        val regionId: String,
        val eventKey: String,
        val type: RegionEventType,
        val index: Int,
        val parameter: String,
        val createdAt: Long,
    )

    private companion object {
        val SOUND_CHOICES = listOf("BLOCK_PORTAL_TRIGGER", "BLOCK_NOTE_BLOCK_PLING", "ENTITY_PLAYER_LEVELUP", "ENTITY_VILLAGER_TRADE", "ENTITY_GENERIC_EXPLODE", "ENTITY_PLAYER_ATTACK_STRONG")
        val PARTICLE_CHOICES = listOf("END_ROD", "FLAME", "ENCHANT", "PORTAL", "CLOUD", "SOUL_FIRE_FLAME", "HEART", "VILLAGER_HAPPY")
    }

    private enum class RegionEventMenu(val key: String, val label: String, val type: RegionEventType) {
        ENTER("enter", "&a[进入区域]", RegionEventType.ENTER),
        LEAVE("leave", "&7[离开区域]", RegionEventType.LEAVE),
        LEFT("left-click", "&e[左键方块]", RegionEventType.LEFT_CLICK),
        RIGHT("right-click", "&e[右键方块]", RegionEventType.RIGHT_CLICK),
        INTERACT("interact", "&d[交互事件]", RegionEventType.INTERACT),
    }
}
