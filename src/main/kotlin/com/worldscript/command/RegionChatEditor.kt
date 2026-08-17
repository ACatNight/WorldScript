package com.worldscript.command

import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.hover.content.Text
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.ChatColor
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
        player.sendMessage(color("&6区域编辑 &8> &e${region.id} &8> &f${region.displayName}"))
        player.sendMessage(color("&8${region.worldName} &7${region.bounds}"))
        when (section) {
            "main" -> main(player, region)
            "events" -> events(player, region)
            else -> when {
                section.startsWith("add:") -> addPreset(player, region, section.removePrefix("add:"))
                section.startsWith("action:") -> action(player, region, section.removePrefix("action:"))
                section.startsWith("set:") -> setInput(player, region, section.removePrefix("set:"))
                section.startsWith("remove:") -> removeAction(player, region, section.removePrefix("remove:"))
                else -> event(player, region, section)
            }
        }
        player.sendMessage(color("&7[返回] &8[<] &f1 &8/ &f1 &8[>] &7[聊天输入]"))
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
        row(player, Button("&d[区域粒子]", "打开区域粒子设置", "/ws edit ${region.id} main"))
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
        if (action.parameters.isEmpty()) line(player, "&7[value]", "当前值：${action.value}", "/ws edit ${region.id} $key set:$index:value")
        action.parameters.forEach { (name, current) ->
            line(player, "&b[$name] &f$current", "点击后在聊天栏输入新值", "/ws edit ${region.id} $key set:$index:$name")
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

    private fun line(player: Player, label: String, hover: String, command: String) {
        player.spigot().sendMessage(*button(label, hover, command))
    }

    private fun heading(player: Player, text: String) {
        player.sendMessage(color("&8$text &8&m--------------------"))
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

    private enum class RegionEventMenu(val key: String, val label: String, val type: RegionEventType) {
        ENTER("enter", "&a[进入区域]", RegionEventType.ENTER),
        LEAVE("leave", "&7[离开区域]", RegionEventType.LEAVE),
        LEFT("left-click", "&e[左键方块]", RegionEventType.LEFT_CLICK),
        RIGHT("right-click", "&e[右键方块]", RegionEventType.RIGHT_CLICK),
        INTERACT("interact", "&d[交互事件]", RegionEventType.INTERACT),
    }

}
