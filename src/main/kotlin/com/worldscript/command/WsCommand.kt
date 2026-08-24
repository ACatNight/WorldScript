package com.worldscript.command

import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l2.rpg.PlayerVariableService
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File

class WsCommand(private val plugin: org.bukkit.plugin.java.JavaPlugin, private val regions: RegionCoreServiceImpl, private val selection: com.worldscript.modules.l1.region_core.SelectionService, private val state: PlayerVariableService) : CommandExecutor, TabCompleter {
    private val lang = Lang(plugin)
    var guiOpener: ((Player) -> Unit)? = null
    var settingsOpener: ((Player) -> Unit)? = null
    var chatEditor: RegionChatEditor? = null
    var reloadHandler: (() -> Unit)? = null
    var playerRefresh: ((Player) -> Unit)? = null

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("worldscript.admin")) return reply(sender, "no-permission")
        when (args.firstOrNull()?.lowercase()) {
            "wand" -> (sender as? Player)?.let { it.inventory.addItem(ItemStack(MaterialResolver.find(plugin.config.getString("selection.tool", "GOLDEN_AXE") ?: "GOLDEN_AXE", "GOLD_AXE") ?: Material.STICK)); reply(it, "wand-given") } ?: reply(sender, "only-player")
            "list" -> (sender as? Player)?.let { guiOpener?.invoke(it) ?: reply(it, "gui-unavailable") } ?: reply(sender, "only-player")
            "settings" -> (sender as? Player)?.let { settingsOpener?.invoke(it) ?: reply(it, "gui-unavailable") } ?: reply(sender, "only-player")
            "edit" -> edit(sender, args)
            "reload" -> reload(sender, args)
            "language" -> language(sender, args)
            "validate" -> validate(sender, args.getOrNull(1)?.takeUnless { it.isBlank() })
            "progress" -> progress(sender, args)
            "help" -> sendUsage(sender)
            "create" -> create(sender, args)
            "delete" -> if (args.size > 1 && regions.delete(args[1])) reply(sender, "region-deleted", args[1]) else reply(sender, "region-not-found", args.getOrNull(1) ?: "")
            "info" -> regions.find(args.getOrNull(1) ?: "")?.let { lang.send(sender, "region-info", "region" to it.id, "world" to it.worldName, "bounds" to it.bounds) } ?: reply(sender, "region-not-found", args.getOrNull(1) ?: "")
            else -> sendUsage(sender)
        }
        return true
    }

    private fun create(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) { reply(sender, "only-player"); return }
        val points = selection.get(player)
        if (args.size < 2 || points == null || points.any { it == null }) { reply(player, "need-selection"); return }
        val first = points[0] ?: return
        val second = points[1] ?: return
        if (!regions.isValidRegionId(args[1])) { reply(player, "region-invalid-id", args[1]); return }
        if (regions.find(args[1]) != null) { reply(player, "region-exists", args[1]); return }
        if (first.world?.uid != second.world?.uid) { reply(player, "region-world-mismatch"); return }
        val displayName = args.drop(2).joinToString(" ").ifBlank { args[1] }
        if (regions.create(args[1], displayName, first, second)) {
            selection.clear(player)
            reply(player, "region-created", args[1])
            regions.find(args[1])?.parentId?.let { parent -> lang.send(player, "region-parent-assigned", "region" to args[1], "parent" to parent) }
        } else reply(player, "region-create-failed", args[1])
    }

    private fun edit(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: run { reply(sender, "only-player"); return }
        val region = args.getOrNull(1) ?: run { sendUsage(sender); return }
        if (region.equals("close", true)) {
            chatEditor?.close(player)
            return
        }
        val eventKey = args.getOrNull(2)
        val actionPage = args.getOrNull(3)
        val section = EditorRoute.fromCommand(eventKey, actionPage)
        chatEditor?.open(player, region, section)
    }

    private fun validate(sender: CommandSender, regionId: String? = null) {
        val issues = regions.validate().filter { issue ->
            regionId == null || issue.startsWith("${regionId.trim()}.", true) || issue.startsWith("${regionId.trim()}:", true)
        }
        if (issues.isEmpty()) {
            lang.send(sender, "validation-clean")
            return
        }
        lang.send(sender, "validation-header", "count" to issues.size)
        issues.forEach { lang.send(sender, "validation-issue", "issue" to it) }
    }

    private fun progress(sender: CommandSender, args: Array<out String>) {
        if (args.size < 4) { lang.send(sender, "progress-usage"); return }
        val player = Bukkit.getPlayerExact(args[1]) ?: Bukkit.getOfflinePlayer(args[1]).takeIf { it.hasPlayedBefore() }
        if (player == null) { lang.send(sender, "progress-player-offline", "player" to args[1]); return }
        if (regions.find(args[2]) == null) { reply(sender, "region-not-found", args[2]); return }
        when (args[3].lowercase()) {
            "unlock" -> {
                state.unlockRegion(player.uniqueId, args[2])
                if (player is Player) playerRefresh?.invoke(player)
            }
            "complete" -> state.markRegionCompleted(player.uniqueId, args[2])
            else -> { lang.send(sender, "progress-usage"); return }
        }
        lang.send(sender, "progress-success", "player" to (player.name ?: args[1]), "region" to args[2], "status" to args[3].lowercase())
    }

    private fun sendUsage(sender: CommandSender) {
        listOf(
            "usage-header",
            "usage-root",
            "usage-wand",
            "usage-create",
            "usage-delete",
            "usage-list",
            "usage-settings",
            "usage-info",
            "usage-edit",
            "usage-reload",
            "usage-language",
            "usage-validate",
            "usage-progress",
            "usage-footer",
        ).forEachIndexed { index, key -> lang.send(sender, key, index == 0) }
    }

    private fun reply(sender: CommandSender, key: String, vararg values: Any): Boolean { lang.send(sender, key, "region" to values.firstOrNull()); return true }

    private fun reload(sender: CommandSender, args: Array<out String>) {
        plugin.reloadConfig()
        regions.load()
        reloadHandler?.invoke()
        if (args.getOrNull(1)?.equals("language", true) == true) Lang.reloadAll()
        reply(sender, "reload-success")
    }

    private fun language(sender: CommandSender, args: Array<out String>) {
        val requested = args.getOrNull(1)?.trim()
        if (requested.isNullOrBlank() || requested.equals("reload", true)) {
            Lang.reloadAll()
            lang.send(sender, "language-reloaded")
            return
        }
        if (!requested.matches(Regex("[A-Za-z0-9_-]+")) || !File(plugin.dataFolder, "lang/$requested.yml").isFile) {
            lang.send(sender, "language-not-found", "language" to requested)
            return
        }
        plugin.config.set("language", requested)
        plugin.saveConfig()
        Lang.reloadAll()
        lang.send(sender, "language-changed", "language" to requested)
    }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when {
        args.size == 1 -> listOf("wand", "create", "delete", "list", "settings", "info", "edit", "reload", "language", "validate", "progress", "help")
        args.size == 2 && args[0].equals("validate", true) -> regions.all().map { it.id }
        args.size == 3 && args[0].equals("progress", true) -> regions.all().map { it.id }
        args.size == 4 && args[0].equals("progress", true) -> listOf("unlock", "complete")
        args.size == 2 && args[0].equals("language", true) -> listOf("reload", "en_US", "zh_CN", "zh_TW")
        args.size == 2 -> regions.all().map { it.id }
        else -> emptyList()
    }
}
