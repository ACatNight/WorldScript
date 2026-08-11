package com.worldscript.command

import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.Lang
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class WsCommand(private val plugin: org.bukkit.plugin.java.JavaPlugin, private val regions: RegionCoreServiceImpl, private val selection: com.worldscript.modules.l1.region_core.SelectionService) : CommandExecutor, TabCompleter {
    private val lang = Lang(plugin)
    var guiOpener: ((Player) -> Unit)? = null

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("worldscript.admin")) return reply(sender, "no-permission")
        when (args.firstOrNull()?.lowercase()) {
            "wand" -> (sender as? Player)?.let { it.inventory.addItem(ItemStack(Material.matchMaterial(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE") ?: Material.GOLDEN_AXE)); reply(it, "wand-given") } ?: reply(sender, "only-player")
            "gui" -> (sender as? Player)?.let { guiOpener?.invoke(it) ?: reply(it, "gui-unavailable") } ?: reply(sender, "only-player")
            "list" -> { if (regions.all().isEmpty()) reply(sender, "region-list-empty") else regions.all().forEach { lang.send(sender, "region-list-item", "region" to it.id) } }
            "reload" -> { plugin.reloadConfig(); regions.load(); reply(sender, "reload-success") }
            "create" -> create(sender, args)
            "delete" -> if (args.size > 1 && regions.delete(args[1])) reply(sender, "region-deleted", args[1]) else reply(sender, "region-not-found", args.getOrNull(1) ?: "")
            "info" -> regions.find(args.getOrNull(1) ?: "")?.let { lang.send(sender, "region-info", "region" to it.id, "world" to it.worldName, "bounds" to it.bounds) } ?: reply(sender, "region-not-found", args.getOrNull(1) ?: "")
            else -> reply(sender, "usage")
        }
        return true
    }

    private fun create(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) { reply(sender, "only-player"); return }
        val points = selection.get(player)
        if (args.size < 2 || points == null || points.any { it == null }) { reply(player, "need-selection"); return }
        if (regions.create(args[1], args[1], points[0]!!, points[1]!!)) { selection.clear(player); reply(player, "region-created", args[1]) } else reply(player, "region-exists", args[1])
    }

    private fun reply(sender: CommandSender, key: String, vararg values: Any): Boolean { lang.send(sender, key, "region" to values.firstOrNull()); return true }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = if (args.size == 1) listOf("wand", "create", "delete", "list", "info", "gui", "reload") else if (args.size == 2) regions.all().map { it.id } else emptyList()
}
