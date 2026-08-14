package com.worldscript.modules.l2.admin_gui

import com.worldscript.foundation.Lang
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.ScriptDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
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
        val inventory = Bukkit.createInventory(RegionGuiHolder("list"), 54, color(lang.text("gui-list-title", "WorldScript Regions")))
        regions.all().take(54).forEachIndexed { index, region ->
            inventory.setItem(index, item(Material.CHEST, region.id, lang.text("gui-region-entry", "Click to edit")))
        }
        player.openInventory(inventory)
    }

    private fun openRegion(player: Player, regionId: String) {
        val region = regions.find(regionId) ?: return openList(player)
        val inventory = Bukkit.createInventory(RegionGuiHolder("region", region.id), 27, color(region.displayName))
        inventory.setItem(4, item(Material.BOOK, region.displayName, "${region.worldName} ${region.bounds}"))
        inventory.setItem(10, item(Material.LIME_DYE, lang.text("gui-event-enter", "Enter"), lang.text("gui-open-event", "Click to edit")))
        inventory.setItem(12, item(Material.RED_DYE, lang.text("gui-event-leave", "Leave"), lang.text("gui-open-event", "Click to edit")))
        inventory.setItem(14, item(Material.YELLOW_DYE, lang.text("gui-event-interact", "Interact"), lang.text("gui-open-event", "Click to edit")))
        inventory.setItem(22, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openEvent(player: Player, regionId: String, type: RegionEventType) {
        val script = regions.find(regionId)?.events?.get(type) ?: ScriptDefinition()
        val inventory = Bukkit.createInventory(RegionGuiHolder("event", regionId, type), 54, color(lang.text("gui-event-${type.name.lowercase()}", type.name)))
        inventory.setItem(4, item(if (script.enabled) Material.LIME_DYE else Material.GRAY_DYE, lang.text(if (script.enabled) "gui-enabled" else "gui-disabled", "Enabled"), lang.text("gui-toggle", "Click to switch")))
        inventory.setItem(5, item(Material.CLOCK, lang.text("gui-cooldown", "Cooldown"), "${script.cooldownSeconds}s"))
        inventory.setItem(6, item(Material.COMPASS, lang.text("gui-entry-mode", "Gameplay summary"), listOf(
            "${lang.text("gui-first-entry", "First entry only")}: ${script.firstEntryOnly}",
            "${lang.text("gui-repeat-entry", "Repeat entry only")}: ${script.repeatEntryOnly}",
            "${lang.text("gui-condition-count", "Conditions")}: ${script.conditions.size}",
            "${lang.text("gui-reward-count", "Rewards")}: ${script.rewards.size}",
        ).joinToString("|")))
        inventory.setItem(10, item(Material.WRITABLE_BOOK, lang.text("gui-add-action", "Add action"), lang.text("gui-add-action-lore", "Click to choose action type")))
        script.actions.take(27).forEachIndexed { index, action ->
            inventory.setItem(18 + index, item(Material.PAPER, "${index + 1}. ${action.type.name}", action.value))
        }
        inventory.setItem(49, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openActionTypes(player: Player, regionId: String, eventType: RegionEventType, actionIndex: Int = -1) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("types", regionId, eventType, actionIndex), 27, color(lang.text("gui-action-types", "Choose action type")))
        inventory.setItem(10, item(Material.PAPER, "MESSAGE", lang.text("gui-type-message", "Text message")))
        inventory.setItem(12, item(Material.COMMAND_BLOCK, "PLAYER_COMMAND", lang.text("gui-type-player-command", "Player command")))
        inventory.setItem(14, item(Material.CHAIN_COMMAND_BLOCK, "CONSOLE_COMMAND", lang.text("gui-type-console-command", "Console command")))
        inventory.setItem(16, item(Material.ENDER_PEARL, "TELEPORT", lang.text("gui-type-teleport", "world,x,y,z")))
        inventory.setItem(22, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openActionEditor(player: Player, regionId: String, eventType: RegionEventType, index: Int) {
        val action = regions.find(regionId)?.events?.get(eventType)?.actions?.getOrNull(index) ?: return openEvent(player, regionId, eventType)
        val inventory = Bukkit.createInventory(RegionGuiHolder("action", regionId, eventType, index, action.type), 27, color("${index + 1}. ${action.type.name}"))
        inventory.setItem(10, item(Material.NAME_TAG, lang.text("gui-change-type", "Change type"), ""))
        inventory.setItem(12, item(Material.WRITABLE_BOOK, lang.text("gui-edit-value", "Edit value"), action.value))
        inventory.setItem(14, item(Material.LAVA_BUCKET, lang.text("gui-delete-action", "Delete action"), ""))
        inventory.setItem(22, item(Material.BARRIER, lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openTextInput(player: Player, holder: RegionGuiHolder) {
        pendingInputs[player.uniqueId] = holder
        player.closeInventory()
        if (holder.inputKind == "cooldown") {
            lang.send(player, "gui-cooldown-prompt")
        } else {
            lang.send(player, "gui-action-prompt", "type" to (holder.actionType?.name ?: "MESSAGE"))
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.inventory.holder as? RegionGuiHolder ?: return
        event.isCancelled = true
        val regionId = holder.regionId
        when (holder.page) {
            "list" -> event.currentItem?.itemMeta?.displayName?.let { ChatColor.stripColor(it) }?.let { openRegion(player, it) }
            "region" -> when (event.rawSlot) {
                10 -> openEvent(player, regionId ?: return, RegionEventType.ENTER)
                12 -> openEvent(player, regionId ?: return, RegionEventType.LEAVE)
                14 -> openEvent(player, regionId ?: return, RegionEventType.INTERACT)
                22 -> openList(player)
            }
            "event" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when {
                    event.rawSlot == 4 -> { regions.toggleEvent(rid, type); openEvent(player, rid, type) }
                    event.rawSlot == 5 -> openTextInput(player, RegionGuiHolder("chat", rid, type, inputKind = "cooldown"))
                    event.rawSlot == 10 -> openActionTypes(player, rid, type)
                    event.rawSlot in 18..44 -> openActionEditor(player, rid, type, event.rawSlot - 18)
                    event.rawSlot == 49 -> openRegion(player, rid)
                }
            }
            "types" -> {
                val type = holder.eventType ?: return
                val actionType = when (event.rawSlot) { 10 -> ActionType.MESSAGE; 12 -> ActionType.PLAYER_COMMAND; 14 -> ActionType.CONSOLE_COMMAND; 16 -> ActionType.TELEPORT; else -> null }
                if (actionType != null) openTextInput(player, RegionGuiHolder("chat", regionId, type, holder.actionIndex, actionType, "action"))
                if (event.rawSlot == 22) openEvent(player, regionId ?: return, type)
            }
            "action" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when (event.rawSlot) {
                    10 -> openActionTypes(player, rid, type, holder.actionIndex)
                    12 -> openTextInput(player, holder.copyForInput("action"))
                    14 -> { regions.removeAction(rid, type, holder.actionIndex); openEvent(player, rid, type) }
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
        if (value.isBlank()) { lang.send(player, "gui-input-empty"); return }
        val regionId = holder.regionId ?: return
        val type = holder.eventType ?: return
        when (holder.inputKind) {
            "cooldown" -> value.toLongOrNull()?.coerceAtLeast(0)?.let { seconds -> regions.updateEvent(regionId, type) { it.copy(cooldownSeconds = seconds) } }
            "action" -> {
                val action = ActionDefinition(holder.actionType ?: ActionType.MESSAGE, value)
                if (holder.actionIndex >= 0) regions.updateAction(regionId, type, holder.actionIndex, action) else regions.addAction(regionId, type, action)
            }
        }
        openEvent(player, regionId, type)
    }

    @EventHandler
    fun onQuit(event: org.bukkit.event.player.PlayerQuitEvent) { pendingInputs.remove(event.player.uniqueId) }

    private fun RegionGuiHolder.copyForInput(kind: String) = RegionGuiHolder("anvil", regionId, eventType, actionIndex, actionType, kind)
    private fun item(material: Material, name: String, lore: String): ItemStack = ItemStack(material).also { stack -> stack.itemMeta = stack.itemMeta?.also { meta: ItemMeta -> meta.setDisplayName(color(name)); meta.lore = lore.split('|').map(::color) } }
    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)
}
