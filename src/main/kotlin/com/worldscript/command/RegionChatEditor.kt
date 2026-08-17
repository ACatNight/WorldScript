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

/**
 * Single-column property editor inspired by mature in-game administration tools.
 * Every page uses the same hierarchy: breadcrumb, grouped properties, operations, footer.
 */
class RegionChatEditor(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val presets: ActionPresetCatalog,
): Listener {
    private val input = mutableMapOf<UUID, PendingInput>()

    fun open(player: Player, regionId: String, section: String = "main") {
        val region = regions.find(regionId) ?: run {
            player.sendMessage(color("&c区域不存在：&f$regionId"))
            return
        }

        when {
            section.startsWith("status:") -> return cycleStatus(player, region)
            section.startsWith("toggle:") -> return toggleEvent(player, region, section.removePrefix("toggle:"))
            section.startsWith("cooldown:") -> return adjustCooldown(player, region, section.removePrefix("cooldown:"))
            section.startsWith("mode:") -> return toggleMode(player, region, section.removePrefix("mode:"))
            section.startsWith("sound:") -> return soundControl(player, region, section.removePrefix("sound:"))
            section.startsWith("select:") -> return selectParameter(player, region, section.removePrefix("select:"))
            section.startsWith("particle:") -> return particleControl(player, region, section.removePrefix("particle:"))
            section.startsWith("set:") -> return setInput(player, region, section.removePrefix("set:"))
            section.startsWith("remove:") -> return removeAction(player, region, section.removePrefix("remove:"))
        }

        header(player, region, section)
        when {
            section == "main" -> main(player, region)
            section == "events" -> events(player, region)
            section == "particles" -> particles(player, region)
            section.startsWith("add:") -> addPreset(player, region, section.removePrefix("add:"))
            section.startsWith("action:") -> action(player, region, section.removePrefix("action:"))
            else -> event(player, region, section)
        }
        footer(player, region, section)
    }

    private fun header(player: Player, region: RegionDefinition, section: String) {
        player.sendMessage(color("&6公共单位 &8> &e${region.id} &8> &f${region.displayName} &8> &6${pageName(section)}"))
        player.sendMessage(color("&8世界 &f${region.worldName} &8| &7坐标范围 &f${region.bounds}"))
        player.sendMessage(color("&7区域角色 &f${region.role.name.lowercase(Locale.ROOT)} &8| &7内容 ID &f${region.contentId.ifBlank { "-" }}"))
    }

    private fun main(player: Player, region: RegionDefinition) {
        group(player, "&6公共特性")
        property(player, "&e[区域状态]", statusText(region), "&e[切换]", "/ws edit ${region.id} status:next")
        property(player, "&e[父区域]", region.parentId ?: "无", "&8[只读]")
        property(player, "&e[子区域]", childCount(region).toString(), "&8[只读]")
        property(player, "&e[继承关系]", if (region.inheritParent) "继承父区域" else "独立配置", "&8[配置文件]")

        group(player, "&e公共数据")
        property(player, "&f[坐标范围]", region.bounds.toString(), "&8[只读]")
        property(player, "&f[内容 ID]", region.contentId.ifBlank { "-" }, "&8[只读]")
        property(player, "&f[优先级]", region.priority.toString(), "&8[配置文件]")

        group(player, "&b区域变量")
        property(player, "&b[变量数量]", region.variables.size.toString(), "&8[配置文件]")
        property(player, "&b[父区域名称]", region.parentId?.let { regions.find(it)?.displayName } ?: "无", "&8[HUD 可用]")
        property(player, "&b[当前区域名称]", region.displayName, "&8[HUD 可用]")

        group(player, "&a事件与反馈")
        property(player, "&a[事件编辑]", "${region.events.values.count { it.enabled }} 个启用", "&a[打开]", "/ws edit ${region.id} events")

        group(player, "&d区域氛围")
        val particle = region.particle ?: regions.effective(region.id)?.particle
        property(player, "&d[区域粒子]", particle?.takeIf { it.enabled }?.type ?: "关闭", "&d[打开]", "/ws edit ${region.id} particles")

    }

    private fun events(player: Player, region: RegionDefinition) {
        group(player, "&a事件与反馈")
        RegionEventMenu.entries.forEach { menu ->
            val script = region.events[menu.type]
            val status = if (script?.enabled == false) "关闭" else "启用"
            property(player, menu.label, "$status &8| &f${script?.actions?.size ?: 0} 个动作", "&a[打开]", "/ws edit ${region.id} ${menu.key}")
        }
    }

    private fun event(player: Player, region: RegionDefinition, key: String) {
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val script = region.events[menu.type]
        group(player, "&e${plain(menu.label)}")
        property(player, "&e[启用状态]", if (script?.enabled == false) "关闭" else "启用", if (script?.enabled == false) "&a[打开]" else "&c[关闭]", "/ws edit ${region.id} ${menu.key} toggle")
        property(player, "&b[触发模式]", mode(script), "&b[切换]", "/ws edit ${region.id} ${menu.key} mode:next")
        stepper(player, "&e[冷却时间]", "${script?.cooldownSeconds ?: 0}s", "&c[-5]", "/ws edit ${region.id} ${menu.key} cooldown:-5", "&a[+5]", "/ws edit ${region.id} ${menu.key} cooldown:5")

        group(player, "&6动作列表")
        if (script?.actions.isNullOrEmpty()) {
            property(player, "&8[动作]", "尚未配置", "&a[添加]", "/ws edit ${region.id} add:$key")
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
            player.sendMessage(color("&a已添加动作：&f${parts[1]}"))
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
        val action = region.events[menu.type]?.actions?.getOrNull(index) ?: return open(player, region.id, key)

        group(player, "&6动作档案")
        property(player, "&7[所属事件]", plain(menu.label), "&8[只读]")
        property(player, "&7[动作类型]", actionLabel(action), "&8[只读]")
        if (action.type == ActionType.SOUND) soundProperties(player, region, key, index, action)

        group(player, "&b动作参数")
        if (action.parameters.isEmpty()) {
            property(player, "&b[value]", action.value.ifBlank { "未设置" }, "&e[输入]", "/ws edit ${region.id} $key set:$index:value")
        } else {
            action.parameters.toSortedMap().forEach { (name, current) ->
                val extra = if (name == "region") listOf(
                    Button("&e[上一项]", "选择上一个区域", "/ws edit ${region.id} $key select:$index:region:prev"),
                    Button("&e[下一项]", "选择下一个区域", "/ws edit ${region.id} $key select:$index:region:next"),
                ) else emptyList()
                property(player, "&b[$name]", current.ifBlank { "未设置" }, "&e[输入]", "/ws edit ${region.id} $key set:$index:$name", extra)
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
        val parts = value.split(':', limit = 3)
        val eventKey = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        val parameter = parts.getOrNull(2) ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == eventKey }?.type ?: return
        if (region.events[type]?.actions?.getOrNull(index) == null) return
        input[player.uniqueId] = PendingInput(region.id, eventKey, type, index, parameter)
        player.sendMessage(color("&6正在编辑 &f$parameter &8| &7请输入新值，输入 &c取消 &7放弃。"))
    }

    private fun removeAction(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val key = parts.firstOrNull() ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        regions.removeAction(region.id, type, index)
        player.sendMessage(color("&a动作已删除。"))
        open(player, region.id, key)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val pending = input.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        if (event.message.equals("取消", true)) {
            event.player.sendMessage(color("&7已取消修改。"))
            return
        }
        val player = event.player
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val action = regions.find(pending.regionId)?.events?.get(pending.type)?.actions?.getOrNull(pending.index) ?: return@Runnable
            val updated = if (pending.parameter == "value") action.copy(value = event.message) else action.copy(parameters = action.parameters + (pending.parameter to event.message))
            regions.updateAction(pending.regionId, pending.type, pending.index, updated)
            player.sendMessage(color("&a参数已保存：&f${pending.parameter} &7= &f${event.message}"))
            open(player, pending.regionId, pending.eventKey)
        })
    }

    private fun toggleEvent(player: Player, region: RegionDefinition, key: String) {
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        regions.toggleEvent(region.id, type)
        open(player, region.id, key)
    }

    private fun adjustCooldown(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val type = RegionEventMenu.entries.firstOrNull { it.key == parts[0] }?.type ?: return
        val delta = parts.getOrNull(1)?.toLongOrNull() ?: return
        regions.updateEvent(region.id, type) { it.copy(cooldownSeconds = (it.cooldownSeconds + delta).coerceAtLeast(0)) }
        open(player, region.id, parts[0])
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
        open(player, region.id, parts[0])
    }

    private fun mode(script: com.worldscript.foundation.model.ScriptDefinition?): String = when {
        script?.firstEntryOnly == true -> "首次进入"
        script?.repeatEntryOnly == true -> "重复进入"
        else -> "每次触发"
    }

    private fun soundControl(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 3)
        val key = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val action = region.events[type]?.actions?.getOrNull(index) ?: return
        val current = action.parameters["sound"] ?: action.value
        val sounds = SOUND_CHOICES.filter { resolveSound(it) != null }.ifEmpty { listOf(current) }
        val currentIndex = sounds.indexOf(current).coerceAtLeast(0)
        when (parts.getOrNull(2)) {
            "prev", "next" -> {
                val delta = if (parts[2] == "next") 1 else -1
                val selected = sounds[(currentIndex + delta + sounds.size) % sounds.size]
                updateActionParameter(player, region, key, index, action.copy(parameters = action.parameters + ("sound" to selected)))
            }
            "play" -> {
                val sound = resolveSound(current)
                if (sound == null) player.sendMessage(color("&c当前服务器不支持音效：&f$current"))
                else {
                    player.playSound(player.location, sound, action.parameters["volume"]?.toFloatOrNull() ?: 1f, action.parameters["pitch"]?.toFloatOrNull() ?: 1f)
                    player.sendMessage(color("&a已试听：&f$current"))
                }
            }
            "volume-down", "volume-up", "pitch-down", "pitch-up" -> {
                val name = if (parts[2].startsWith("volume")) "volume" else "pitch"
                val delta = if (parts[2].endsWith("up")) 0.1 else -0.1
                val next = ((action.parameters[name]?.toDoubleOrNull() ?: 1.0) + delta).coerceIn(0.0, 2.0)
                updateActionParameter(player, region, key, index, action.copy(parameters = action.parameters + (name to "%.1f".format(Locale.US, next))))
            }
        }
        open(player, region.id, "action:$key:$index")
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
        val parts = value.split(':', limit = 4)
        val key = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        val parameter = parts.getOrNull(2) ?: return
        val direction = parts.getOrNull(3) ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        val action = region.events[type]?.actions?.getOrNull(index) ?: return
        val options = regions.all().map { it.id }.sorted()
        if (options.isEmpty()) return
        val current = options.indexOf(action.parameters[parameter]).coerceAtLeast(0)
        val delta = if (direction == "next") 1 else -1
        val selected = options[(current + delta + options.size) % options.size]
        updateActionParameter(player, region, key, index, action.copy(parameters = action.parameters + (parameter to selected)))
        open(player, region.id, "action:$key:$index")
    }

    private fun updateActionParameter(player: Player, region: RegionDefinition, key: String, index: Int, action: ActionDefinition) {
        val type = RegionEventMenu.entries.firstOrNull { it.key == key }?.type ?: return
        regions.updateAction(region.id, type, index, action)
        player.sendMessage(color("&a参数已保存。"))
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
        if (local == null && region.parentId != null) player.sendMessage(color("&8当前粒子继承自父区域；首次修改会在本区域写入覆盖配置。"))
    }

    private fun particleControl(player: Player, region: RegionDefinition, value: String) {
        val current = region.particle ?: regions.effective(region.id)?.particle ?: RegionParticleDefinition(enabled = false)
        val parts = value.split(':', limit = 2)
        val updated = when (parts[0]) {
            "toggle" -> current.copy(enabled = !current.enabled)
            "preview" -> { previewParticle(player, current); current }
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
        open(player, region.id, "particles")
    }

    private fun previewParticle(player: Player, definition: RegionParticleDefinition) {
        val particle = runCatching { Particle.valueOf(definition.type.uppercase(Locale.ROOT)) }.getOrNull()
        if (particle == null) {
            player.sendMessage(color("&c当前服务器不支持粒子：&f${definition.type}"))
            return
        }
        player.spawnParticle(particle, player.location.clone().add(0.0, 1.0, 0.0), definition.count, definition.spreadX, definition.spreadY, definition.spreadZ, definition.speed)
        player.sendMessage(color("&a已预览粒子：&f${definition.type}"))
    }

    private fun cycleStatus(player: Player, region: RegionDefinition) {
        val statuses = listOf(GlobalRegionStatus.OPEN, GlobalRegionStatus.DANGEROUS, GlobalRegionStatus.PEACEFUL, GlobalRegionStatus.LOCKED)
        val current = statuses.indexOf(region.statuses.firstOrNull()).coerceAtLeast(0)
        val next = statuses[(current + 1) % statuses.size]
        statuses.filter { it != next }.forEach { regions.setStatus(region.id, it, false) }
        regions.setStatus(region.id, next, true)
        open(player, region.id, "main")
    }

    private fun footer(player: Player, region: RegionDefinition, section: String) {
        val back = when {
            section == "main" -> "main"
            section == "events" || section == "particles" -> "main"
            section.startsWith("action:") -> section.removePrefix("action:").substringBefore(':')
            section.startsWith("add:") -> section.removePrefix("add:").substringBefore(':')
            RegionEventMenu.entries.any { it.key == section } -> "events"
            else -> "main"
        }
        group(player, "&7操作")
        operationRow(player,
            Button("&7[返回]", "返回上一级", "/ws edit ${region.id} $back"),
            Button("&f[1 / 1]", "当前页面", "/ws edit ${region.id} $section"),
            Button("&7[刷新]", "重新读取当前页面", "/ws edit ${region.id} $section"),
        )
        if (section == "main") operation(player, "&c[关闭]", "关闭聊天编辑器", "/ws edit close")
        player.sendMessage(color("&8提示：点击彩色文字操作；文本参数使用聊天栏输入。"))
    }

    private fun group(player: Player, title: String) {
        player.sendMessage(color("$title &8&m----------------------------------------"))
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
            if (index > 0) components += TextComponent(color(" &8| "))
            components += button(button.label, button.hover, button.command).toList()
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    private fun actionLabel(action: ActionDefinition): String = action.preset ?: action.type.name.lowercase(Locale.ROOT).replace('_', ' ')
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
        section == "events" -> "事件列表"
        section == "particles" -> "区域氛围"
        section.startsWith("action:") -> "动作参数"
        section.startsWith("add:") -> "添加动作"
        RegionEventMenu.entries.any { it.key == section } -> plain(RegionEventMenu.entries.first { it.key == section }.label)
        else -> "区域编辑"
    }

    private fun button(label: String, hover: String, command: String): Array<BaseComponent> = ComponentBuilder(color(label))
        .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(hover)))
        .create()

    private fun color(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
    private fun plain(value: String): String = ChatColor.stripColor(color(value)) ?: value

    private data class Button(val label: String, val hover: String, val command: String)
    private data class PendingInput(val regionId: String, val eventKey: String, val type: RegionEventType, val index: Int, val parameter: String)

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
