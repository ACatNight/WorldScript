package com.worldscript.command

import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_core.PolygonEditingService
import com.worldscript.foundation.SettingsLayout
import com.worldscript.foundation.module.ModuleState
import com.worldscript.foundation.module.WorldScriptModuleManager
import com.worldscript.modules.l2.rpg.PlayerVariableService
import com.worldscript.modules.l2.script_actions.ToastService
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File

class WsCommand(private val plugin: org.bukkit.plugin.java.JavaPlugin, private val regions: RegionCoreServiceImpl, private val selection: com.worldscript.modules.l1.region_core.SelectionService, private val polygons: PolygonEditingService, private val state: PlayerVariableService, private val toasts: ToastService, private val editorTools: com.worldscript.modules.l1.region_core.EditorToolService, private val moduleManager: WorldScriptModuleManager) : CommandExecutor, TabCompleter {
    private val lang = Lang(plugin)
    var guiOpener: ((Player) -> Unit)? = null
    var settingsOpener: ((Player) -> Unit)? = null
    var chatEditor: RegionChatEditor? = null
    var reloadHandler: (() -> Unit)? = null
    var playerRefresh: ((Player) -> Unit)? = null

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("worldscript.admin")) return reply(sender, "no-permission")
        when (args.firstOrNull()?.lowercase()) {
            "wand" -> (sender as? Player)?.let { it.inventory.addItem(ItemStack(editorTools.selectionTool())); reply(it, "wand-given") } ?: reply(sender, "only-player")
            "selection" -> selection(sender, args)
            "list" -> (sender as? Player)?.let { guiOpener?.invoke(it) ?: reply(it, "gui-unavailable") } ?: reply(sender, "only-player")
            "settings" -> (sender as? Player)?.let { settingsOpener?.invoke(it) ?: reply(it, "gui-unavailable") } ?: reply(sender, "only-player")
            "polygon" -> polygon(sender, args)
            "edit" -> edit(sender, args)
            "reload" -> reload(sender, args)
            "language" -> language(sender, args)
            "validate" -> validate(sender, args.getOrNull(1)?.takeUnless { it.isBlank() })
            "progress" -> progress(sender, args)
            "toast" -> toast(sender, args)
            "modules" -> modules(sender, args)
            "test" -> test(sender, args)
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

    private fun selection(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: run { reply(sender, "only-player"); return }
        when (args.getOrNull(1)?.lowercase()) {
            "preview" -> if (selection.preview(player)) lang.send(player, "selection-preview-shown") else lang.send(player, "selection-empty")
            "cancel", "clear" -> {
                selection.clear(player)
                lang.send(player, "selection-cleared")
            }
            else -> lang.send(player, "selection-usage")
        }
    }

    private fun polygon(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: run { reply(sender, "only-player"); return }
        when (val operation = args.getOrNull(1)?.lowercase()) {
            null -> lang.send(player, "polygon-usage")
            "start" -> {
                val regionId = args.getOrNull(2)
                if (regionId == null) lang.send(player, "polygon-start-usage")
                else if (regions.find(regionId) == null) reply(player, "region-not-found", regionId)
                else polygons.start(player, regionId)
            }
            "cancel" -> if (!polygons.cancel(player)) lang.send(player, "polygon-not-editing")
            "status" -> polygons.status(player)
            "preview" -> polygons.preview(player)
            "finish" -> polygons.finish(player)
            "undo" -> polygons.undo(player)
            "redo" -> polygons.redo(player)
            "remove" -> args.getOrNull(2)?.toIntOrNull()?.let { polygons.removePoint(player, it) }
                ?: lang.send(player, "polygon-remove-usage")
            "move" -> {
                val from = args.getOrNull(2)?.toIntOrNull()
                val to = args.getOrNull(3)?.toIntOrNull()
                if (from == null || to == null) lang.send(player, "polygon-move-usage")
                else polygons.movePoint(player, from, to)
            }
            "reset" -> {
                val regionId = args.getOrNull(2) ?: polygons.activeRegion(player)
                if (regionId == null) lang.send(player, "polygon-reset-usage")
                else if (regions.find(regionId) == null) reply(player, "region-not-found", regionId)
                else polygons.reset(player, regionId)
            }
            else -> {
                if (regions.find(operation) == null) reply(player, "region-not-found", operation)
                else polygons.start(player, operation)
            }
        }
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
            "reset" -> {
                state.resetRegionProgress(player.uniqueId, args[2])
                if (player is Player) playerRefresh?.invoke(player)
            }
            else -> { lang.send(sender, "progress-usage"); return }
        }
        lang.send(sender, "progress-success", "player" to (player.name ?: args[1]), "region" to args[2], "status" to args[3].lowercase())
    }

    private fun toast(sender: CommandSender, args: Array<out String>) {
        if (args.getOrNull(1).equals("diagnose", true)) {
            val regionId = args.getOrNull(2) ?: run { lang.send(sender, "toast-diagnose-usage"); return }
            val region = regions.effective(regionId) ?: run { reply(sender, "region-not-found", regionId); return }
            val discovery = region.discovery
            val diagnostic = toasts.diagnose(
                regionName = region.displayName,
                role = region.role,
                regionEnabled = discovery?.toastEnabled ?: true,
                titleOverride = discovery?.toastTitle.orEmpty(),
                descriptionOverride = discovery?.toastDescription.orEmpty(),
                iconOverride = discovery?.toastIcon.orEmpty(),
            )
            lang.send(sender, "toast-diagnose-header", "region" to region.id)
            lang.send(sender, "toast-diagnose-switches", "global" to diagnostic.globalEnabled, "region" to diagnostic.regionEnabled, "api" to diagnostic.loadApiAvailable)
            lang.send(sender, "toast-diagnose-content", "frame" to diagnostic.frame, "frame-valid" to diagnostic.frameValid, "icon" to diagnostic.requestedIcon, "resolved-icon" to diagnostic.resolvedIcon.ifBlank { "-" }, "icon-valid" to diagnostic.iconValid)
            lang.send(sender, "toast-diagnose-text", "title" to diagnostic.title, "description" to diagnostic.description.ifBlank { "-" }, "display" to diagnostic.descriptionDisplay)
            return
        }
        if (!args.getOrNull(1).equals("test", true)) {
            lang.send(sender, "toast-test-usage")
            return
        }
        val regionId: String
        val target: Player
        when (args.size) {
            3 -> {
                target = sender as? Player ?: run { reply(sender, "only-player"); return }
                regionId = args[2]
            }
            4 -> {
                target = Bukkit.getPlayerExact(args[2]) ?: run {
                    lang.send(sender, "toast-test-player-not-found", "player" to args[2])
                    return
                }
                regionId = args[3]
            }
            else -> {
                lang.send(sender, "toast-test-usage")
                return
            }
        }
        val region = regions.effective(regionId) ?: run {
            reply(sender, "region-not-found", regionId)
            return
        }
        toasts.showPreview(target, region.id, region.displayName, region.role)
        lang.send(sender, "toast-test-sent", "player" to target.name, "region" to region.id)
    }

    private fun modules(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase()) {
            "list", null -> {
                val reports = moduleManager.all()
                lang.send(sender, "modules-list-header", "count" to reports.size)
                reports.forEach { report ->
                    lang.send(
                        sender,
                        "modules-list-item",
                        "id" to report.descriptor.id,
                        "name" to report.descriptor.name,
                        "version" to report.descriptor.version,
                        "state" to moduleStateKey(report.state),
                        "reason" to report.reason,
                    )
                }
            }
            "info" -> {
                val id = args.getOrNull(2) ?: run {
                    lang.send(sender, "modules-info-usage")
                    return
                }
                val report = moduleManager.find(id) ?: run {
                    lang.send(sender, "modules-not-found", "module" to id)
                    return
                }
                lang.send(sender, "modules-info-header", "id" to report.descriptor.id, "name" to report.descriptor.name)
                lang.send(sender, "modules-info-version", "version" to report.descriptor.version, "api" to report.descriptor.apiVersion, "state" to moduleStateKey(report.state))
                lang.send(sender, "modules-info-source", "source" to report.source, "reason" to report.reason)
                lang.send(sender, "modules-info-dependencies", "dependencies" to report.descriptor.dependencies.joinToString(", ").ifBlank { "-" })
            }
            "reload" -> {
                moduleManager.reload()
                lang.send(sender, "modules-reloaded", "count" to moduleManager.all().size)
            }
            else -> lang.send(sender, "modules-usage")
        }
    }

    private fun test(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: run { reply(sender, "only-player"); return }
        val regionId = args.getOrNull(1) ?: run { lang.send(player, "test-usage"); return }
        val region = regions.effective(regionId) ?: run { reply(player, "region-not-found", regionId); return }
        val validationIssues = regions.validate().filter { it.startsWith("${region.id}.", true) || it.startsWith("${region.id}:", true) }
        val shape = if (region.shape is com.worldscript.foundation.model.RegionShape.Polygon) "polygon" else "cuboid"
        val enter = region.events[RegionEventType.ENTER]
        val conditionState = when {
            !plugin.config.getBoolean("conditions.enabled", false) -> "global-disabled"
            enter?.conditionsEnabled != true || enter.conditions.isEmpty() -> "not-configured"
            else -> "configured"
        }
        lang.send(player, "test-header", "region" to region.id)
        lang.send(player, "test-shape", "shape" to shape, "world" to region.worldName)
        lang.send(player, "test-enter", "enabled" to (enter?.enabled ?: true), "conditions" to conditionState, "actions" to (enter?.actions?.size ?: 0))
        lang.send(player, if (validationIssues.isEmpty()) "test-validation-clean" else "test-validation-issues", "count" to validationIssues.size)
        validationIssues.forEach { lang.send(player, "validation-issue", "issue" to it) }
        toasts.showDiscoveryPreview(player, region.id, region.displayName, region.role, region.discovery?.toastTitle.orEmpty(), region.discovery?.toastDescription.orEmpty(), region.discovery?.toastIcon.orEmpty())
        lang.send(player, "test-toast-preview-sent")
    }

    private fun sendUsage(sender: CommandSender) {
        listOf(
            "usage-header",
            "usage-root",
            "usage-wand",
            "usage-selection",
            "usage-create",
            "usage-delete",
            "usage-list",
            "usage-settings",
            "usage-polygon",
            "usage-info",
            "usage-edit",
            "usage-reload",
            "usage-language",
            "usage-validate",
            "usage-progress",
            "usage-toast",
            "usage-modules",
            "usage-test",
            "usage-footer",
        ).forEachIndexed { index, key -> lang.send(sender, key, index == 0) }
    }

    private fun reply(sender: CommandSender, key: String, vararg values: Any): Boolean { lang.send(sender, key, "region" to values.firstOrNull()); return true }

    private fun reload(sender: CommandSender, args: Array<out String>) {
        polygons.clear()
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
        SettingsLayout.saveRoot(plugin)
        Lang.reloadAll()
        lang.send(sender, "language-changed", "language" to requested)
    }
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when {
        args.size == 1 -> listOf("wand", "selection", "create", "polygon", "delete", "list", "settings", "info", "edit", "reload", "language", "validate", "progress", "toast", "modules", "test", "help")
        args.size == 2 && args[0].equals("selection", true) -> listOf("preview", "cancel")
        args.size == 2 && args[0].equals("polygon", true) -> listOf("start", "cancel", "status", "preview", "finish", "undo", "redo", "remove", "move", "reset") + regions.all().map { it.id }
        args.size == 3 && args[0].equals("polygon", true) && args[1].equals("start", true) -> regions.all().map { it.id }
        args.size == 3 && args[0].equals("polygon", true) && args[1].equals("reset", true) -> regions.all().map { it.id }
        args.size == 2 && args[0].equals("validate", true) -> regions.all().map { it.id }
        args.size == 3 && args[0].equals("progress", true) -> regions.all().map { it.id }
        args.size == 4 && args[0].equals("progress", true) -> listOf("unlock", "complete", "reset")
        args.size == 2 && args[0].equals("toast", true) -> listOf("test", "diagnose")
        args.size == 3 && args[0].equals("toast", true) && args[1].equals("diagnose", true) -> regions.all().map { it.id }
        args.size == 3 && args[0].equals("toast", true) && args[1].equals("test", true) -> Bukkit.getOnlinePlayers().map { it.name }
        args.size == 2 && args[0].equals("modules", true) -> listOf("list", "info", "reload")
        args.size == 3 && args[0].equals("modules", true) && args[1].equals("info", true) -> moduleManager.all().map { it.descriptor.id }
        args.size == 2 && args[0].equals("language", true) -> listOf("reload", "en_US", "zh_CN", "zh_TW")
        args.size == 2 && args[0].equals("test", true) -> regions.all().map { it.id }
        args.size == 2 -> regions.all().map { it.id }
        else -> emptyList()
    }

    private fun moduleStateKey(state: ModuleState): String = when (state) {
        ModuleState.BUILTIN -> lang.text("modules-state-builtin", "built-in")
        ModuleState.ENABLED -> lang.text("modules-state-enabled", "enabled")
        ModuleState.DISABLED -> lang.text("modules-state-disabled", "disabled")
        ModuleState.FAILED -> lang.text("modules-state-failed", "failed")
    }
}
