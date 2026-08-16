package com.worldscript.command

import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.hover.content.Text
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
        player.sendMessage(color("&6区域编辑 &8<临时编辑器> &f${region.id}"))
        player.sendMessage(color("&7${region.displayName} &8| &7${region.worldName} &8| &7${region.bounds}"))
        when (section) {
            "main" -> main(player, region)
            "events" -> events(player, region)
            else -> event(player, region, section)
        }
        player.sendMessage(color("&8&m----------------------------------------"))
    }

    private fun main(player: Player, region: RegionDefinition) {
        line(player, "&6[公共特性]", "区域状态、父子关系和基础信息", "/ws edit ${region.id} main")
        line(player, "&e[公共数据]", "查看区域坐标和内容 ID", "/ws edit ${region.id} main")
        line(player, "&b[区域变量]", "编辑请直接修改区域 YAML", "/ws edit ${region.id} main")
        player.sendMessage("")
        line(player, "&a[事件编辑]", "打开进入、离开和交互事件", "/ws edit ${region.id} events")
        line(player, "&d[区域粒子]", "粒子效果请在区域 YAML 中编辑", "/ws edit ${region.id} main")
        line(player, "&7[刷新]", "重新读取当前页面", "/ws edit ${region.id} main")
        line(player, "&c[关闭]", "关闭聊天编辑器", "/ws edit close")
    }

    private fun events(player: Player, region: RegionDefinition) {
        RegionEventMenu.entries.forEach { menu ->
            val script = region.events[menu.type]
            val status = if (script?.enabled == false) "&8关闭" else "&a启用"
            line(player, "$status ${menu.label}", "动作 ${script?.actions?.size ?: 0} 个，配置请编辑区域文件", "/ws edit ${region.id} ${menu.key}")
        }
        line(player, "&7[返回]", "返回区域总览", "/ws edit ${region.id} main")
    }

    private fun event(player: Player, region: RegionDefinition, key: String) {
        val menu = RegionEventMenu.entries.firstOrNull { it.key == key } ?: return open(player, region.id, "events")
        val script = region.events[menu.type]
        player.sendMessage(color("&e${menu.label} &8| &7enabled=${script?.enabled ?: false} &7actions=${script?.actions?.size ?: 0}"))
        line(player, "&b[配置文件]", "编辑 regions/${region.id}.yml", "/ws edit ${region.id} main")
        line(player, "&7[返回事件]", "返回事件列表", "/ws edit ${region.id} events")
    }

    private fun line(player: Player, label: String, hover: String, command: String) {
        val component = ComponentBuilder(color(label))
            .event(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .event(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(hover)))
            .create()
        player.spigot().sendMessage(*component)
    }

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)

    private enum class RegionEventMenu(val key: String, val label: String, val type: RegionEventType) {
        ENTER("enter", "&a[进入区域]", RegionEventType.ENTER),
        LEAVE("leave", "&7[离开区域]", RegionEventType.LEAVE),
        LEFT("left-click", "&e[左键方块]", RegionEventType.LEFT_CLICK),
        RIGHT("right-click", "&e[右键方块]", RegionEventType.RIGHT_CLICK),
        INTERACT("interact", "&d[交互事件]", RegionEventType.INTERACT),
    }
}
