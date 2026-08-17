package com.worldscript.command

import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.hover.content.Text
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.ChatColor
import org.bukkit.entity.Player

/** A small chat-first editor for operators who prefer config files over inventories. */
class RegionChatEditor(private val regions: RegionCoreServiceImpl) {
    fun open(player: Player, regionId: String, section: String = "main") {
        val region = regions.find(regionId) ?: run {
            player.sendMessage("${ChatColor.RED}区域不存在：$regionId")
            return
        }
        player.sendMessage(color("&8&m----------------------------------------"))
        player.sendMessage(color("&6⌁区域编辑 &8<临时编辑器> &f> &e${region.id}"))
        player.sendMessage(color("&7${region.displayName} &8| &7${region.worldName} &8| &7${region.bounds}"))
        when (section) {
            "main" -> main(player, region)
            "events" -> events(player, region)
            else -> if (section.startsWith("add:")) addPreset(player, region, section.removePrefix("add:")) else event(player, region, section)
        }
        player.sendMessage(color("&8&m----------------------------------------"))
    }

    private fun main(player: Player, region: RegionDefinition) {
        row(player,
            Button("&6[公共特性]", "区域状态、父子关系和基础信息", "/ws edit ${region.id} main"),
            Button("&e[公共数据]", "查看区域坐标和内容 ID", "/ws edit ${region.id} main"),
            Button("&b[区域变量]", "编辑请直接修改区域 YAML", "/ws edit ${region.id} main"),
            Button("&a[事件编辑]", "打开进入、离开和交互事件", "/ws edit ${region.id} events"),
            Button("&d[区域粒子]", "粒子效果请在区域 YAML 中编辑", "/ws edit ${region.id} main"),
        )
        row(player,
            Button("&7[刷新]", "重新读取当前页面", "/ws edit ${region.id} main"),
            Button("&c[关闭]", "关闭聊天编辑器", "/ws edit close"),
        )
    }

    private fun events(player: Player, region: RegionDefinition) {
        row(player, *RegionEventMenu.entries.map { menu ->
            val script = region.events[menu.type]
            val status = if (script?.enabled == false) "&8关闭" else "&a启用"
            Button("$status ${menu.label}", "动作 ${script?.actions?.size ?: 0} 个，点击查看", "/ws edit ${region.id} ${menu.key}")
        }.toTypedArray())
        line(player, "&7[返回]", "返回区域总览", "/ws edit ${region.id} main")
    }

    private fun event(player: Player, region: RegionDefinition, key: String) {
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val script = region.events[menu.type]
        player.sendMessage(color("&e${menu.label} &8| &7enabled=${script?.enabled ?: false} &7actions=${script?.actions?.size ?: 0}"))
        script?.actions?.forEachIndexed { index, action ->
            player.sendMessage(color("&8${index + 1}. &f${action.preset ?: action.type.name.lowercase()} &7${action.parameters.values.firstOrNull() ?: action.value}"))
        }
        line(player, "&a[添加预设动作]", "选择一个内置动作并写入区域配置", "/ws edit ${region.id} add:$key")
        line(player, "&b[配置文件]", "编辑 regions/${region.id}.yml", "/ws edit ${region.id} main")
        line(player, "&7[返回事件]", "返回事件列表", "/ws edit ${region.id} events")
    }

    private fun addPreset(player: Player, region: RegionDefinition, key: String) {
        if (key.contains(':')) {
            val parts = key.split(':', limit = 2)
            val menu = RegionEventMenu.entries.firstOrNull { it.key == parts[0] } ?: return open(player, region.id, "events")
            val action = preset(parts[1]) ?: return open(player, region.id, "add:${parts[0]}")
            regions.updateEvent(region.id, menu.type) { it.copy(actions = it.actions + action) }
            player.sendMessage(color("&a已添加预设动作：&f${parts[1]}"))
            return open(player, region.id, parts[0])
        }
        PRESETS.forEach { (id, label) -> line(player, label, "使用默认参数添加，之后可编辑 YAML", "/ws edit ${region.id} add:$key:$id") }
        line(player, "&7[返回]", "返回事件", "/ws edit ${region.id} $key")
    }

    private fun preset(id: String): ActionDefinition? = when (id) {
        "text-display" -> ActionDefinition(ActionType.TEXT_DISPLAY, parameters = mapOf("title" to "&b区域标题", "subtitle" to "&f区域副标题", "fade-in" to "20", "stay" to "100", "fade-out" to "20"), preset = id)
        "message" -> ActionDefinition(ActionType.MESSAGE, parameters = mapOf("text" to "&7区域消息"), preset = id)
        "sound" -> ActionDefinition(ActionType.SOUND, parameters = mapOf("sound" to "BLOCK_PORTAL_TRIGGER", "volume" to "1.0", "pitch" to "1.0"), preset = id)
        "set-variable" -> ActionDefinition(ActionType.SET_VARIABLE, parameters = mapOf("key" to "discovered", "value" to "true"), preset = id)
        "unlock-region" -> ActionDefinition(ActionType.UNLOCK_REGION, parameters = mapOf("region" to "target_region"), preset = id)
        "complete-region" -> ActionDefinition(ActionType.COMPLETE_REGION, parameters = mapOf("region" to "target_region"), preset = id)
        "player-command" -> ActionDefinition(ActionType.PLAYER_COMMAND, parameters = mapOf("command" to "spawn"), preset = id)
        "console-command" -> ActionDefinition(ActionType.CONSOLE_COMMAND, parameters = mapOf("command" to "say %player% entered %region%"), preset = id)
        else -> null
    }

    private fun line(player: Player, label: String, hover: String, command: String) {
        player.spigot().sendMessage(*button(label, hover, command))
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

    private enum class RegionEventMenu(val key: String, val label: String, val type: RegionEventType) {
        ENTER("enter", "&a[进入区域]", RegionEventType.ENTER),
        LEAVE("leave", "&7[离开区域]", RegionEventType.LEAVE),
        LEFT("left-click", "&e[左键方块]", RegionEventType.LEFT_CLICK),
        RIGHT("right-click", "&e[右键方块]", RegionEventType.RIGHT_CLICK),
        INTERACT("interact", "&d[交互事件]", RegionEventType.INTERACT),
    }

    private companion object {
        val PRESETS = listOf(
            "text-display" to "&b[区域标题]",
            "message" to "&e[聊天消息]",
            "sound" to "&d[播放音效]",
            "player-command" to "&6[玩家命令]",
            "console-command" to "&c[控制台命令]",
            "set-variable" to "&a[设置变量]",
            "unlock-region" to "&b[解锁区域]",
            "complete-region" to "&5[完成区域]",
        )
    }
}
