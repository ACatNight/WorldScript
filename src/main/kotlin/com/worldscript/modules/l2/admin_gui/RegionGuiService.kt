package com.worldscript.modules.l2.admin_gui

import com.worldscript.foundation.Lang
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.RegionRole
import com.worldscript.foundation.model.ScriptDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

class RegionGuiService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
) : Listener {
    private val lang = Lang(plugin)
    private val pendingInputs = mutableMapOf<java.util.UUID, RegionGuiHolder>()

    fun openList(player: Player) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("list"), 54, color(lang.text("gui-atlas-title", "WorldScript Atlas")))
        fillBackground(inventory)
        inventory.setItem(4, item(Material.MAP, lang.text("gui-atlas-title", "WorldScript Atlas"), lang.text("gui-atlas-hint", "Select a region to edit")))
        regions.all().take(REGION_SLOTS.size).forEachIndexed { index, region ->
            inventory.setItem(REGION_SLOTS[index], regionItem(region))
        }
        inventory.setItem(49, item(Material.BARRIER, lang.text("gui-close", "Close"), ""))
        player.openInventory(inventory)
    }

    private fun openRegion(player: Player, regionId: String) {
        val region = regions.find(regionId) ?: return openList(player)
        val effective = regions.effective(region.id) ?: region
        val parent = region.parentId?.let(regions::find)
        val inventory = Bukkit.createInventory(RegionGuiHolder("region", region.id), 54, color(region.displayName))
        fillBackground(inventory)

        inventory.setItem(4, item(Material.MAP, region.displayName, listOf(
            "${lang.text("gui-region-id", "ID")}: ${region.id}",
            "${lang.text("gui-region-role", "Role")}: ${region.role.name.lowercase()}",
            "${lang.text("gui-region-content", "Content")}: ${region.contentId.ifBlank { "-" }}",
        ).joinToString("|")))
        inventory.setItem(10, item(Material.NAME_TAG, lang.text("gui-card-identity", "Identity"), listOf(
            "${lang.text("gui-region-name", "Name")}: ${region.displayName}",
            "${lang.text("gui-region-role", "Role")}: ${region.role.name.lowercase()}",
        ).joinToString("|")))
        inventory.setItem(12, item(Material.COMPASS, lang.text("gui-card-location", "Location"), listOf(
            "${lang.text("gui-region-world", "World")}: ${region.worldName}",
            region.bounds.toString(),
        ).joinToString("|")))
        inventory.setItem(14, item(Material.REDSTONE, lang.text("gui-card-state", "World state"), listOf(
            "${lang.text("gui-region-status", "Status")}: ${statusText(effective.statuses)}",
            "${lang.text("gui-region-priority", "Priority")}: ${region.priority}",
        ).joinToString("|")))
        inventory.setItem(16, item(Material.CHAIN, lang.text("gui-card-inheritance", "Inheritance"), listOf(
            "${lang.text("gui-region-parent", "Parent")}: ${parent?.displayName ?: lang.text("gui-none", "None")}",
            "${lang.text("gui-region-inherit", "Enabled")}: ${region.inheritParent}",
        ).joinToString("|")))

        inventory.setItem(26, item(Material.WOODEN_SWORD, lang.text("gui-event-left-click", "Left-click block"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(28, item(Material.LIME_DYE, lang.text("gui-event-enter", "Enter event"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(30, item(Material.RED_DYE, lang.text("gui-event-leave", "Leave event"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(32, item(Material.YELLOW_DYE, lang.text("gui-event-interact", "Legacy interact"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(34, item(Material.STONE_BUTTON, lang.text("gui-event-right-click", "Right-click block"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(38, item(Material.PAPER, lang.text("gui-card-variables", "Variables"), "${region.variables.size}"))
        inventory.setItem(40, item(Material.BOOK, lang.text("gui-card-content", "External content"), region.contentId.ifBlank { "-" }))
        inventory.setItem(49, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openEvent(player: Player, regionId: String, type: RegionEventType) {
        val region = regions.find(regionId) ?: return openList(player)
        val rawScript = region.events[type]
        val script = regions.effective(regionId)?.events?.get(type) ?: rawScript ?: ScriptDefinition()
        val inherited = rawScript == null && region.parentId != null
        val inventory = Bukkit.createInventory(RegionGuiHolder("event", regionId, type), 54, color(lang.text("gui-event-${type.name.lowercase()}", type.name)))
        inventory.setItem(4, item(if (script.enabled) Material.LIME_DYE else Material.GRAY_DYE, lang.text(if (script.enabled) "gui-enabled" else "gui-disabled", "Enabled"), lang.text("gui-toggle", "Click to toggle")))
        inventory.setItem(5, item(Material.CLOCK, lang.text("gui-cooldown", "Cooldown"), "${script.cooldownSeconds}s"))
        inventory.setItem(6, item(Material.COMPASS, lang.text("gui-trigger", "Trigger"), eventModeText(script)))
        inventory.setItem(8, item(Material.CHAIN, lang.text("gui-inherit-event", "Inheritance"), if (inherited) lang.text("gui-inherited-readonly", "Inherited from parent|Edit this event to override") else "${lang.text("gui-inherit-from-parent", "Inherit parent event")}: ${!script.overrideParent}"))
        inventory.setItem(10, item(Material.WRITABLE_BOOK, lang.text("gui-add-action", "Add action"), lang.text("gui-add-action-lore", "Choose an action type")))
        inventory.setItem(12, item(Material.PAPER, lang.text("gui-condition-count", "Conditions"), script.conditions.size.toString()))
        inventory.setItem(14, item(Material.CHEST, lang.text("gui-reward-count", "Rewards"), script.rewards.size.toString()))
        script.actions.take(27).forEachIndexed { index, action ->
            val inheritedAction = rawScript?.actions?.getOrNull(index) == null && inherited
            inventory.setItem(18 + index, item(Material.PAPER, "${index + 1}. ${actionLabel(action.type)}", if (inheritedAction) "${action.value}|${lang.text("gui-inherited-readonly", "Inherited from parent|Edit this event to override")}" else action.value))
        }
        inventory.setItem(49, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openActionTypes(player: Player, regionId: String, eventType: RegionEventType, actionIndex: Int = -1) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("types", regionId, eventType, actionIndex), 54, color(lang.text("gui-action-types", "Choose action type")))
        inventory.setItem(4, item(Material.WRITABLE_BOOK, lang.text("gui-action-types", "Choose action type"), lang.text("gui-action-types-hint", "Choose what this event should do")))
        ActionType.entries.forEachIndexed { index, type ->
            inventory.setItem(ACTION_TYPE_SLOTS[index], item(actionMaterial(type), actionLabel(type), actionDescription(type)))
        }
        inventory.setItem(49, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openActionEditor(player: Player, regionId: String, eventType: RegionEventType, index: Int) {
        val action = regions.find(regionId)?.events?.get(eventType)?.actions?.getOrNull(index) ?: return openEvent(player, regionId, eventType)
        val inventory = Bukkit.createInventory(RegionGuiHolder("action", regionId, eventType, index, action.type), 27, color("${index + 1}. ${action.type.name}"))
        inventory.setItem(4, item(actionMaterial(action.type), actionLabel(action.type), action.value))
        inventory.setItem(10, item(Material.NAME_TAG, lang.text("gui-change-type", "Change type"), ""))
        inventory.setItem(12, item(Material.WRITABLE_BOOK, lang.text("gui-edit-value", "Edit value"), action.value))
        inventory.setItem(14, item(Material.LAVA_BUCKET, lang.text("gui-delete-action", "Delete action"), ""))
        inventory.setItem(22, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openTextInput(player: Player, holder: RegionGuiHolder) {
        pendingInputs[player.uniqueId] = holder
        player.closeInventory()
        if (holder.inputKind == "cooldown") lang.send(player, "gui-cooldown-prompt")
        else lang.send(player, "gui-action-prompt", "type" to (holder.actionType?.name ?: "MESSAGE"))
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.inventory.holder as? RegionGuiHolder ?: return
        event.isCancelled = true
        val regionId = holder.regionId
        when (holder.page) {
            "list" -> when {
                event.rawSlot == 49 -> player.closeInventory()
                event.rawSlot in REGION_SLOTS -> event.currentItem?.itemMeta?.displayName?.let { ChatColor.stripColor(it) }?.let { if (regions.find(it) != null) openRegion(player, it) }
            }
            "region" -> when (event.rawSlot) {
                26 -> openEvent(player, regionId ?: return, RegionEventType.LEFT_CLICK)
                28 -> openEvent(player, regionId ?: return, RegionEventType.ENTER)
                30 -> openEvent(player, regionId ?: return, RegionEventType.LEAVE)
                32 -> openEvent(player, regionId ?: return, RegionEventType.INTERACT)
                34 -> openEvent(player, regionId ?: return, RegionEventType.RIGHT_CLICK)
                49 -> openList(player)
            }
            "event" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when {
                    event.rawSlot == 4 -> { regions.toggleEvent(rid, type); openEvent(player, rid, type) }
                    event.rawSlot == 5 -> openTextInput(player, RegionGuiHolder("chat", rid, type, inputKind = "cooldown"))
                    event.rawSlot == 10 -> openActionTypes(player, rid, type)
                    event.rawSlot in 18..44 && event.currentItem != null -> {
                        val index = event.rawSlot - 18
                        if (regions.find(rid)?.events?.get(type)?.actions?.getOrNull(index) == null && regions.effective(rid)?.events?.get(type)?.actions?.getOrNull(index) != null) {
                            lang.send(player, "gui-inherited-readonly-message")
                        } else {
                            openActionEditor(player, rid, type, index)
                        }
                    }
                    event.rawSlot == 49 -> openRegion(player, rid)
                }
            }
            "types" -> {
                val type = holder.eventType ?: return
                val index = ACTION_TYPE_SLOTS.indexOf(event.rawSlot)
                val actionType = ActionType.entries.getOrNull(index)
                if (actionType != null) openTextInput(player, RegionGuiHolder("chat", regionId, type, holder.actionIndex, actionType, "action"))
                if (event.rawSlot == 49) openEvent(player, regionId ?: return, type)
            }
            "action" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when (event.rawSlot) {
                    10 -> openActionTypes(player, rid, type, holder.actionIndex)
                    12 -> openTextInput(player, holder.copyForInput("action"))
                    14 -> {
                        lang.send(player, "gui-delete-confirm")
                        pendingInputs[player.uniqueId] = RegionGuiHolder("confirm-delete", rid, type, holder.actionIndex)
                        player.closeInventory()
                    }
                    22 -> openEvent(player, rid, type)
                }
            }
        }
    }

    @EventHandler
    fun onChat(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
        val holder = pendingInputs.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        plugin.server.scheduler.runTask(plugin, Runnable { finishInput(event.player, event.message.trim(), holder) })
    }

    private fun finishInput(player: Player, value: String, holder: RegionGuiHolder) {
        if (holder.page == "confirm-delete") {
            if (value.equals("confirm", true) || value.equals("yes", true) || value.equals("y", true) || value == "确认") {
                val regionId = holder.regionId ?: return
                val type = holder.eventType ?: return
                regions.removeAction(regionId, type, holder.actionIndex)
                openEvent(player, regionId, type)
            } else {
                lang.send(player, "gui-delete-cancelled")
                holder.regionId?.let { regionId -> holder.eventType?.let { openEvent(player, regionId, it) } }
            }
            return
        }
        if (value.isBlank()) { lang.send(player, "gui-input-empty"); return }
        val regionId = holder.regionId ?: return
        val type = holder.eventType ?: return
        when (holder.inputKind) {
            "cooldown" -> {
                val seconds = value.toLongOrNull()?.takeIf { it >= 0 }
                if (seconds == null) {
                    lang.send(player, "gui-cooldown-invalid")
                    openEvent(player, regionId, type)
                    return
                }
                regions.updateEvent(regionId, type) { it.copy(cooldownSeconds = seconds) }
            }
            "action" -> {
                val action = ActionDefinition(holder.actionType ?: ActionType.MESSAGE, value)
                if (holder.actionIndex >= 0) regions.updateAction(regionId, type, holder.actionIndex, action) else regions.addAction(regionId, type, action)
            }
        }
        openEvent(player, regionId, type)
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) { pendingInputs.remove(event.player.uniqueId) }

    private fun regionItem(region: RegionDefinition): ItemStack = item(roleMaterial(region.role), region.id, listOf(
        region.displayName,
        "${lang.text("gui-region-role", "Role")}: ${region.role.name.lowercase()}",
        "${lang.text("gui-region-status", "Status")}: ${statusText(regions.effective(region.id)?.statuses ?: region.statuses)}",
    ).joinToString("|"))

    private fun statusText(statuses: Set<GlobalRegionStatus>): String =
        if (statuses.isEmpty()) lang.text("gui-status-open", "open") else statuses.joinToString(",") { it.name.lowercase() }

    private fun eventModeText(script: ScriptDefinition): String = when {
        script.firstEntryOnly -> lang.text("gui-mode-first", "First entry")
        script.repeatEntryOnly -> lang.text("gui-mode-repeat", "Repeat entry")
        else -> lang.text("gui-mode-always", "Always")
    }

    private fun actionLabel(type: ActionType): String = lang.text("gui-type-${type.name.lowercase().replace('_', '-')}", type.name.lowercase().replace('_', ' '))

    private fun actionDescription(type: ActionType): String = lang.text("gui-type-${type.name.lowercase().replace('_', '-')}-desc", type.name)

    private fun actionMaterial(type: ActionType): Material = when (type) {
        ActionType.MESSAGE -> Material.PAPER
        ActionType.PLAYER_COMMAND -> Material.COMMAND_BLOCK
        ActionType.CONSOLE_COMMAND -> Material.CHAIN_COMMAND_BLOCK
        ActionType.TELEPORT -> Material.ENDER_PEARL
        ActionType.SET_VARIABLE -> Material.WRITABLE_BOOK
        ActionType.SET_REGION_STATUS -> Material.REDSTONE
        ActionType.GIVE_ITEM -> Material.CHEST
        ActionType.GIVE_EXPERIENCE -> Material.EXPERIENCE_BOTTLE
        ActionType.GIVE_MONEY -> Material.GOLD_INGOT
        ActionType.UNLOCK_REGION -> Material.TRIPWIRE_HOOK
        ActionType.COMPLETE_REGION -> Material.NETHER_STAR
    }

    private fun roleMaterial(role: RegionRole): Material = when (role) {
        RegionRole.HUB -> Material.COMPASS
        RegionRole.OPEN_ZONE -> Material.GRASS_BLOCK
        RegionRole.POINT_OF_INTEREST -> Material.MAP
        RegionRole.DANGER_ZONE -> Material.REDSTONE
        RegionRole.GATE -> Material.IRON_BARS
    }

    private fun item(material: Material, name: String, lore: String): ItemStack = ItemStack(material).also { stack ->
        stack.itemMeta = stack.itemMeta?.also { meta: ItemMeta ->
            meta.setDisplayName(color(name))
            meta.lore = lore.split('|').map(::color)
        }
    }

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)

    private fun fillBackground(inventory: org.bukkit.inventory.Inventory) {
        val pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", "")
        BORDER_SLOTS.forEach { slot -> inventory.setItem(slot, pane) }
    }

    private fun RegionGuiHolder.copyForInput(kind: String) = RegionGuiHolder("anvil", regionId, eventType, actionIndex, actionType, kind)

    private companion object {
        val REGION_SLOTS = (10..43).toList()
        val ACTION_TYPE_SLOTS = listOf(10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34)
        val BORDER_SLOTS = listOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53)
    }
}
