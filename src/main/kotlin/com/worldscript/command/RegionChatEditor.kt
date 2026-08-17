package com.worldscript.command

import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.RegionParticleDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.hover.content.Text
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.ChatColor
import org.bukkit.Sound
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/** A small chat-first editor for operators who prefer config files over inventories. */
class RegionChatEditor(private val plugin: JavaPlugin, private val regions: RegionCoreServiceImpl, private val presets: ActionPresetCatalog) : Listener {
    private val input = mutableMapOf<UUID, PendingInput>()
    fun open(player: Player, regionId: String, section: String = "main") {
        val region = regions.find(regionId) ?: run {
            player.sendMessage("${ChatColor.RED}区域不存在：$regionId")
            return
        }
        player.sendMessage(color("&6公共单位 &8> &e${region.id} &8> &f${region.displayName} &8> &6${pageName(section)}"))
        player.sendMessage(color("&8${region.worldName} &7${region.bounds} &8| &7父区域: &f${region.parentId ?: "无"} &8| &7子区域: &f${regions.all().count { it.parentId.equals(region.id, true) }}"))
        player.sendMessage(color("&7区域类型 &f${region.role.name.lowercase()} &8| &7内容 ID &f${region.contentId.ifBlank { "-" }}"))
        when (section) {
            "main" -> main(player, region)
            "events" -> events(player, region)
            else -> when {
                section == "particles" -> particles(player, region)
                section.startsWith("toggle:") -> toggleEvent(player, region, section.removePrefix("toggle:"))
                section.startsWith("cooldown:") -> adjustCooldown(player, region, section.removePrefix("cooldown:"))
                section.startsWith("mode:") -> toggleMode(player, region, section.removePrefix("mode:"))
                section.startsWith("add:") -> addPreset(player, region, section.removePrefix("add:"))
                section.startsWith("action:") -> action(player, region, section.removePrefix("action:"))
                section.startsWith("sound:") -> soundControl(player, region, section.removePrefix("sound:"))
                section.startsWith("select:") -> selectParameter(player, region, section.removePrefix("select:"))
                section.startsWith("particle:") -> particleControl(player, region, section.removePrefix("particle:"))
                section.startsWith("set:") -> setInput(player, region, section.removePrefix("set:"))
                section.startsWith("remove:") -> removeAction(player, region, section.removePrefix("remove:"))
                else -> event(player, region, section)
            }
        }
        row(player, Button("&7[返回]", "返回上一级", "/ws edit ${region.id} main"), Button("&8[<]", "上一页", "/ws edit ${region.id} main"), Button("&f1 / 1", "当前页", "/ws edit ${region.id} main"), Button("&8[>]", "下一页", "/ws edit ${region.id} main"), Button("&7[聊天输入]", "需要输入文字时点击属性值", "/ws edit ${region.id} main"))
    }

    private fun main(player: Player, region: RegionDefinition) {
        heading(player, "&6公共特性")
        row(player, Button("&e[区域状态]", "查看区域状态和父子关系", "/ws edit ${region.id} main"))
        row(player, Button("&e[继承关系]", "查看父区域与继承规则", "/ws edit ${region.id} main"))
        heading(player, "&e公共数据")
        row(player, Button("&f[坐标范围]", "查看区域坐标范围", "/ws edit ${region.id} main"))
        row(player, Button("&f[内容 ID]", "查看外部内容标识", "/ws edit ${region.id} main"))
        heading(player, "&b区域变量")
        row(player, Button("&b[变量列表]", "查看区域变量，编辑请使用 YAML", "/ws edit ${region.id} main"))
        heading(player, "&a事件与反馈")
        row(player, Button("&a[事件编辑]", "打开进入、离开和交互事件", "/ws edit ${region.id} events"))
        heading(player, "&d区域氛围")
        row(player, Button("&d[区域粒子]", "打开区域粒子设置", "/ws edit ${region.id} particles"))
        heading(player, "&7操作")
        row(player, Button("&7[刷新]", "重新读取当前页面", "/ws edit ${region.id} main"), Button("&c[关闭]", "关闭聊天编辑器", "/ws edit close"))
    }

    private fun events(player: Player, region: RegionDefinition) {
        heading(player, "&a事件列表")
        RegionEventMenu.entries.forEach { menu ->
            val script = region.events[menu.type]
            val status = if (script?.enabled == false) "&8关闭" else "&a启用"
            row(player, Button("$status ${menu.label}", "动作 ${script?.actions?.size ?: 0} 个，点击查看", "/ws edit ${region.id} ${menu.key}"))
        }
        heading(player, "&7操作")
        row(player, Button("&7[返回]", "返回区域总览", "/ws edit ${region.id} main"))
    }

    private fun event(player: Player, region: RegionDefinition, key: String) {
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val script = region.events[menu.type]
        heading(player, "&e${menu.label}")
        player.sendMessage(color("&7启用状态 &f${script?.enabled ?: false} &8| &7动作数量 &f${script?.actions?.size ?: 0}"))
        heading(player, "&6基础设置")
        row(player, Button(if (script?.enabled == false) "&8[关闭]" else "&a[启用]", "切换事件启用状态", "/ws edit ${region.id} ${menu.key} toggle"))
        row(player, Button("&e[冷却 ${script?.cooldownSeconds ?: 0}s]", "减少或增加冷却时间", "/ws edit ${region.id} ${menu.key} cooldown:5"), Button("&7[冷却 -5s]", "减少五秒冷却", "/ws edit ${region.id} ${menu.key} cooldown:-5"))
        row(player, Button("&b[模式: ${mode(script)}]", "切换总是、首次进入或重复进入", "/ws edit ${region.id} ${menu.key} mode:next"))
        heading(player, "&f动作列表")
        script?.actions?.forEachIndexed { index, action ->
            line(player, "&8${index + 1}. &f${action.preset ?: action.type.name.lowercase()}", "查看并编辑动作参数", "/ws edit ${region.id} ${menu.key} action:$index")
        }
        heading(player, "&7操作")
        row(player, Button("&a[添加预设动作]", "选择一个内置动作并写入区域配置", "/ws edit ${region.id} add:$key"))
        row(player, Button("&b[配置文件]", "编辑 regions/${region.id}.yml", "/ws edit ${region.id} main"))
        row(player, Button("&7[返回事件]", "返回事件列表", "/ws edit ${region.id} events"))
    }

    private fun action(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val key = parts.firstOrNull() ?: return open(player, region.id, "events")
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return open(player, region.id, key)
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val action = region.events[menu.type]?.actions?.getOrNull(index) ?: return open(player, region.id, key)
        player.sendMessage(color("&e动作 ${index + 1} &8| &f${action.preset ?: action.type.name.lowercase()}"))
        heading(player, "&f参数")
        if (action.type == com.worldscript.foundation.model.ActionType.SOUND) {
            row(player, Button("&3[上一音效]", "选择上一个音效", "/ws edit ${region.id} $key sound:$index:prev"), Button("&3[下一音效]", "选择下一个音效", "/ws edit ${region.id} $key sound:$index:next"))
            row(player, Button("&3[试听]", "试听当前音效", "/ws edit ${region.id} $key sound:$index:play"))
            row(player, Button("&c[音量 -]", "音量减少 0.1", "/ws edit ${region.id} $key sound:$index:volume-down"), Button("&a[音量 +]", "音量增加 0.1", "/ws edit ${region.id} $key sound:$index:volume-up"))
            row(player, Button("&c[音调 -]", "音调减少 0.1", "/ws edit ${region.id} $key sound:$index:pitch-down"), Button("&a[音调 +]", "音调增加 0.1", "/ws edit ${region.id} $key sound:$index:pitch-up"))
        }
        if (action.parameters.isEmpty()) line(player, "&7[value]", "当前值：${action.value}", "/ws edit ${region.id} $key set:$index:value")
        action.parameters.forEach { (name, current) ->
            line(player, "&b[$name] &f$current", "点击后在聊天栏输入新值", "/ws edit ${region.id} $key set:$index:$name")
            if (name == "region") row(player, Button("&e[上一地区]", "从现有区域中选择", "/ws edit ${region.id} $key select:$index:region:prev"), Button("&e[下一地区]", "从现有区域中选择", "/ws edit ${region.id} $key select:$index:region:next"))
        }
        row(player,
            Button("&c[删除动作]", "删除这个动作", "/ws edit ${region.id} $key remove:$index"),
            Button("&7[返回]", "返回事件动作列表", "/ws edit ${region.id} $key"),
        )
    }

    private fun setInput(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 3)
        val eventKey = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        val parameter = parts.getOrNull(2) ?: return
        val type = RegionEventMenu.entries.firstOrNull { it.key == eventKey }?.type ?: return
        region.events[type]?.actions?.getOrNull(index) ?: return
        input[player.uniqueId] = PendingInput(region.id, type, index, parameter)
        player.sendMessage(color("&6请输入 &f$parameter &6的新值，输入 &c取消 &6放弃修改。"))
    }

    private fun removeAction(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 2)
        val type = RegionEventMenu.entries.firstOrNull { it.key == parts[0] }?.type ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        regions.removeAction(region.id, type, index)
        player.sendMessage(color("&a动作已删除。"))
        open(player, region.id, parts[0])
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val pending = input.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val player = event.player
        val message = event.message
        if (message.equals("取消", true)) {
            event.player.sendMessage(color("&7已取消修改。"))
            return
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            val action = currentAction(regions.find(pending.regionId), pending.type, pending.index) ?: return@Runnable
            val updated = if (pending.parameter == "value") action.copy(value = message) else action.copy(parameters = action.parameters + (pending.parameter to message))
            regions.updateAction(pending.regionId, pending.type, pending.index, updated)
            player.sendMessage(color("&a参数已保存：&f${pending.parameter} &7= &f$message"))
            open(player, pending.regionId, pending.type.name.lowercase().replace('_', '-'))
        })
    }

    private fun currentAction(region: RegionDefinition?, type: RegionEventType, index: Int): ActionDefinition? = region?.events?.get(type)?.actions?.getOrNull(index)

    private fun addPreset(player: Player, region: RegionDefinition, key: String) {
        if (key.contains(':')) {
            val parts = key.split(':', limit = 2)
            val menu = RegionEventMenu.entries.firstOrNull { it.key == parts[0] } ?: return open(player, region.id, "events")
            val action = preset(parts[1]) ?: return open(player, region.id, "add:${parts[0]}")
            regions.updateEvent(region.id, menu.type) { it.copy(actions = it.actions + action) }
            player.sendMessage(color("&a已添加预设动作：&f${parts[1]}"))
            return open(player, region.id, parts[0])
        }
        presets.all().forEach { preset ->
            line(player, "&b[${preset.name}]", "使用默认参数添加，随后可在游戏内修改", "/ws edit ${region.id} add:$key:${preset.id}")
        }
        line(player, "&7[返回]", "返回事件", "/ws edit ${region.id} $key")
    }

    private fun preset(id: String): ActionDefinition? = presets.create(id)

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
        script?.firstEntryOnly == true -> "首次"
        script?.repeatEntryOnly == true -> "重复"
        else -> "总是"
    }

    private fun soundControl(player: Player, region: RegionDefinition, value: String) {
        val parts = value.split(':', limit = 3)
        val key = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        val action = region.events[RegionEventMenu.entries.firstOrNull { it.key == key }?.type]?.actions?.getOrNull(index) ?: return
        val current = action.parameters["sound"] ?: action.value
        val sounds = SOUND_CHOICES.filter { runCatching { Sound.valueOf(it) }.isSuccess }.ifEmpty { listOf(current) }
        val currentIndex = sounds.indexOf(current).coerceAtLeast(0)
        when (parts.getOrNull(2)) {
            "prev", "next" -> {
                val delta = if (parts[2] == "next") 1 else -1
                val selected = sounds[(currentIndex + delta + sounds.size) % sounds.size]
                updateActionParameter(player, region, key, index, action.copy(parameters = action.parameters + ("sound" to selected)))
            }
            "play" -> {
                val sound = resolveSound(current)
                if (sound == null) {
                    player.sendMessage(color("&c当前服务器不支持音效：&f$current"))
                } else {
                    player.playSound(player.location, sound, action.parameters["volume"]?.toFloatOrNull() ?: 1f, action.parameters["pitch"]?.toFloatOrNull() ?: 1f)
                    player.sendMessage(color("&a已试听：&f$current"))
                }
            }
            "volume-down", "volume-up", "pitch-down", "pitch-up" -> {
                val name = if (parts[2].startsWith("volume")) "volume" else "pitch"
                val delta = if (parts[2].endsWith("up")) 0.1 else -0.1
                val next = ((action.parameters[name]?.toDoubleOrNull() ?: 1.0) + delta).coerceIn(0.0, 2.0)
                updateActionParameter(player, region, key, index, action.copy(parameters = action.parameters + (name to "%.1f".format(java.util.Locale.US, next))))
            }
        }
        open(player, region.id, "action:$key:$index")
    }

    private fun resolveSound(value: String): Sound? {
        val name = value.trim().uppercase()
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
        val options = regions.all().map { it.id }
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
        player.sendMessage(color("&a已保存参数。"))
    }

    private fun particles(player: Player, region: RegionDefinition) {
        val particle = region.particle ?: regions.effective(region.id)?.particle ?: RegionParticleDefinition(enabled = false)
        heading(player, "&d区域粒子")
        player.sendMessage(color("&7状态 &f${particle.enabled} &8| &7类型 &b${particle.type} &8| &7预设 &d${particle.preset}"))
        row(player, Button("&d[立即预览]", "在当前位置显示一次粒子", "/ws edit ${region.id} particle:preview"))
        row(player, Button("&a[启用/关闭]", "切换粒子显示", "/ws edit ${region.id} particle:toggle"))
        row(player, Button("&d[上一类型]", "选择上一个粒子", "/ws edit ${region.id} particle:prev"), Button("&d[下一类型]", "选择下一个粒子", "/ws edit ${region.id} particle:next"))
        row(player, Button("&e[数量 -]", "减少粒子数量", "/ws edit ${region.id} particle:count:-1"), Button("&e[数量 +]", "增加粒子数量", "/ws edit ${region.id} particle:count:1"))
        row(player, Button("&7[返回]", "返回区域总览", "/ws edit ${region.id} main"))
    }

    private fun particleControl(player: Player, region: RegionDefinition, value: String) {
        val current = region.particle ?: RegionParticleDefinition(enabled = false)
        val parts = value.split(':', limit = 2)
        val updated = when (parts[0]) {
            "toggle" -> current.copy(enabled = !current.enabled)
            "preview" -> {
                previewParticle(player, current)
                current
            }
            "prev", "next" -> {
                val choices = PARTICLE_CHOICES.filter { runCatching { Particle.valueOf(it) }.isSuccess }.ifEmpty { listOf(current.type) }
                val index = choices.indexOf(current.type).coerceAtLeast(0)
                val delta = if (parts[0] == "next") 1 else -1
                current.copy(type = choices[(index + delta + choices.size) % choices.size])
            }
            "count" -> current.copy(count = (current.count + (parts.getOrNull(1)?.toIntOrNull() ?: 0)).coerceIn(1, 64))
            else -> current
        }
        regions.updateParticle(region.id, updated)
        open(player, region.id, "particles")
    }

    private fun previewParticle(player: Player, definition: RegionParticleDefinition) {
        val particle = runCatching { Particle.valueOf(definition.type.uppercase()) }.getOrNull()
        if (particle == null) {
            player.sendMessage(color("&c当前服务器不支持粒子：&f${definition.type}"))
            return
        }
        player.spawnParticle(particle, player.location.clone().add(0.0, 1.0, 0.0), definition.count, definition.spreadX, definition.spreadY, definition.spreadZ, definition.speed)
        player.sendMessage(color("&a已预览粒子：&f${definition.type}"))
    }

    private fun line(player: Player, label: String, hover: String, command: String) {
        player.spigot().sendMessage(*button(label, hover, command))
    }

    private fun heading(player: Player, text: String) {
        player.sendMessage(color("$text &8&m----------------------------------------"))
    }

    private fun pageName(section: String): String = when {
        section == "main" -> "公共特性"
        section == "events" -> "事件列表"
        section == "particles" -> "区域粒子"
        section.startsWith("action:") -> "动作参数"
        section.startsWith("add:") -> "添加动作"
        else -> "区域编辑"
    }

    private fun row(player: Player, vararg buttons: Button) {
        val components = mutableListOf<BaseComponent>()
        buttons.forEachIndexed { index, button ->
            if (index > 0) components += ComponentBuilder(color(" &8| ")).create().first()
            components += button(button.label, button.hover, button.command)
        }
        player.spigot().sendMessage(*components.toTypedArray())
    }

    private fun button(label: String, hover: String, command: String): Array<BaseComponent> = ComponentBuilder(color(label))
            .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(hover)))
            .create()

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)

    private data class Button(val label: String, val hover: String, val command: String)
    private data class PendingInput(val regionId: String, val type: RegionEventType, val index: Int, val parameter: String)

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
