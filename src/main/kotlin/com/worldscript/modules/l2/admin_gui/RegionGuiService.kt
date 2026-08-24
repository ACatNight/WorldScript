@file:Suppress("DEPRECATION")

package com.worldscript.modules.l2.admin_gui

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionRole
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Bukkit
import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

class RegionGuiService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
) : Listener {
    private val lang = Lang(plugin)
    var editorOpener: ((Player, String) -> Unit)? = null

    fun openList(player: Player, requestedPage: Int = 0) {
        val entries = sortedRegions()
        val pageCount = ((entries.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val inventory = Bukkit.createInventory(
            RegionGuiHolder("list", page),
            54,
            color(lang.text("gui-list-title", "WorldScript Regions")),
        )
        fillBackground(inventory)
        inventory.setItem(4, item(material("MAP"), lang.text("gui-list-title", "WorldScript Regions"), listOf(
            lang.text("gui-list-left", "Left-click: Edit"),
            lang.text("gui-list-right", "Right-click: Teleport"),
            lang.text("gui-list-middle", "Middle-click: Global settings"),
            lang.text("gui-list-map", "Map: Open settings"),
            "&8${page + 1} / $pageCount",
        )))

        entries.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { index, region ->
            inventory.setItem(REGION_SLOTS[index], regionItem(region, player))
        }
        inventory.setItem(45, button("ARROW", "gui-page-previous", "Previous page"))
        inventory.setItem(49, button("BARRIER", "gui-close", "Close"))
        inventory.setItem(53, button("ARROW", "gui-page-next", "Next page"))
        player.openInventory(inventory)
    }

    private fun openSettings(player: Player) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("settings", 0), 27, color(lang.text("gui-settings-title", "WorldScript Settings")))
        fillBackground(inventory)
        inventory.setItem(10, toggleItem("discovery.enabled", "gui-setting-discovery", "Discovery"))
        inventory.setItem(12, toggleItem("discovery.title.enabled", "gui-setting-title", "Discovery Title"))
        inventory.setItem(14, toggleItem("discovery.sound.enabled", "gui-setting-sound", "Discovery Sound"))
        inventory.setItem(16, toggleItem("discovery.reward.enabled", "gui-setting-reward", "Discovery Reward"))
        inventory.setItem(22, toggleItem("conditions.enabled", "gui-setting-conditions", "Entry Conditions"))
        inventory.setItem(18, button("ARROW", "gui-settings-back", "Back"))
        inventory.setItem(26, button("BARRIER", "gui-close", "Close"))
        player.openInventory(inventory)
    }

    private fun toggleItem(path: String, key: String, fallback: String): ItemStack {
        val enabled = plugin.config.getBoolean(path, false)
        val state = if (enabled) {
            lang.text("gui-enabled", "&aEnabled, click to disable")
        } else {
            lang.text("gui-disabled", "&cDisabled, click to enable")
        }
        return item(material(if (enabled) "LIME_DYE" else "GRAY_DYE"), lang.text(key, fallback), listOf(state))
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.inventory.holder as? RegionGuiHolder ?: return
        event.isCancelled = true
        if (holder.page == "settings") {
            when (event.rawSlot) {
                10 -> toggleSetting("discovery.enabled", player)
                12 -> toggleSetting("discovery.title.enabled", player)
                14 -> toggleSetting("discovery.sound.enabled", player)
                16 -> toggleSetting("discovery.reward.enabled", player)
                22 -> toggleSetting("conditions.enabled", player)
                18 -> openList(player)
                26 -> player.closeInventory()
            }
            return
        }
        if (holder.page != "list") return
        val page = holder.pageIndex
        when (event.rawSlot) {
            // Middle-click is not emitted by many survival clients. The
            // header map is therefore also a reliable settings fallback.
            4 -> if (event.click == ClickType.LEFT || event.click == ClickType.SHIFT_LEFT) {
                openSettingsNextTick(player)
            }
            45 -> if (page > 0) openList(player, page - 1)
            49 -> player.closeInventory()
            53 -> openList(player, page + 1)
            in REGION_SLOTS -> {
                val slotIndex = REGION_SLOTS.indexOf(event.rawSlot)
                val region = sortedRegions().getOrNull(page * PAGE_SIZE + slotIndex) ?: return
                handleRegionClick(player, region, event.click)
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is RegionGuiHolder) event.isCancelled = true
    }

    private fun teleportToRegion(player: Player, region: RegionDefinition) {
        val world = Bukkit.getWorld(region.worldId) ?: Bukkit.getWorld(region.worldName)
        if (world == null) {
            lang.send(player, "region-world-not-found", "world" to region.worldName)
            return
        }
        val min = region.bounds.min
        val max = region.bounds.max
        player.teleport(org.bukkit.Location(world,
            (min.x + max.x) / 2.0 + 0.5,
            (min.y + max.y).toDouble() / 2.0 + 0.1,
            (min.z + max.z) / 2.0 + 0.5,
        ))
        lang.send(player, "gui-teleported", "region" to region.id)
    }

    private fun handleRegionClick(player: Player, region: RegionDefinition, click: ClickType) {
        when (click) {
            ClickType.LEFT, ClickType.SHIFT_LEFT -> {
                player.closeInventory()
                editorOpener?.invoke(player, region.id)
            }
            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> teleportToRegion(player, region)
            ClickType.MIDDLE, ClickType.CREATIVE -> {
                openSettingsNextTick(player)
            }
            else -> Unit
        }
    }

    private fun openSettingsNextTick(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) {
                player.closeInventory()
                openSettings(player)
            }
        })
    }

    private fun toggleSetting(path: String, player: Player) {
        val enabled = !plugin.config.getBoolean(path, false)
        plugin.config.set(path, enabled)
        // A Discovery child switch has no effect while its parent is off.
        // Keep the global settings GUI consistent with the region editor.
        if (enabled && path.startsWith("discovery.") && path != "discovery.enabled") {
            plugin.config.set("discovery.enabled", true)
        }
        plugin.saveConfig()
        openSettings(player)
    }

    private fun sortedRegions(): List<RegionDefinition> =
        regions.all().sortedWith(compareBy<RegionDefinition> { it.worldName }.thenBy { it.id })

    private fun regionItem(region: RegionDefinition, player: Player): ItemStack {
        val effective = regions.effective(region.id) ?: region
        val parent = region.parentId?.let(regions::find)
        val distance = if (player.world.name == region.worldName) {
            val min = region.bounds.min
            val max = region.bounds.max
            val x = (min.x + max.x) / 2.0 + 0.5
            val z = (min.z + max.z) / 2.0 + 0.5
            player.location.distance(org.bukkit.Location(player.world, x, player.location.y, z)).toInt()
        } else null
        val lore = mutableListOf(
            "&f${region.displayName}",
            "&7${region.worldName}${distance?.let { " &8(${it}m)" } ?: ""}",
            "&7${lang.text("gui-region-role", "Role")}: &f${region.role.name.lowercase()}",
            "&7${lang.text("gui-region-status", "Status")}: &f${statusText(effective.statuses)}",
        )
        parent?.let { lore += "&7${lang.text("gui-region-parent", "Parent")}: &f${it.displayName}" }
        if (region.contentId.isNotBlank()) lore += "&7${lang.text("gui-region-content", "Content")}: &f${region.contentId}"
        lore += ""
        lore += lang.text("gui-list-left", "Left-click: Edit")
        lore += lang.text("gui-list-right", "Right-click: Teleport")
        lore += lang.text("gui-list-middle", "Middle-click: Global settings")
        lore += lang.text("gui-list-map", "Map: Open settings")
        return item(roleMaterial(region.role), region.id, lore)
    }

    private fun statusText(statuses: Set<GlobalRegionStatus>): String =
        if (statuses.isEmpty()) lang.text("gui-status-open", "open") else statuses.joinToString(",") { it.name.lowercase() }

    private fun roleMaterial(role: RegionRole): Material = when (role) {
        RegionRole.HUB -> material("COMPASS")
        RegionRole.OPEN_ZONE -> material("GRASS_BLOCK", "GRASS")
        RegionRole.POINT_OF_INTEREST -> material("MAP")
        RegionRole.DANGER_ZONE -> material("REDSTONE")
        RegionRole.GATE -> material("IRON_BARS")
    }

    private fun material(primary: String, vararg legacy: String): Material = MaterialResolver.find(primary, *legacy) ?: Material.PAPER

    private fun button(material: String, key: String, fallback: String): ItemStack =
        item(this.material(material), lang.text(key, fallback), emptyList())

    private fun item(material: Material, name: String, lore: List<String>): ItemStack = ItemStack(material).also { stack ->
        stack.itemMeta = stack.itemMeta?.also { meta: ItemMeta ->
            meta.setDisplayName(color(name))
            meta.lore = lore.map(::color)
        }
    }

    private fun fillBackground(inventory: Inventory) {
        val pane = item(material("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"), " ", emptyList())
        BORDER_SLOTS.forEach { inventory.setItem(it, pane) }
    }

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)

    private companion object {
        const val PAGE_SIZE = 36
        val REGION_SLOTS = (9..44).toList()
        val BORDER_SLOTS = listOf(0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52, 53)
    }
}
