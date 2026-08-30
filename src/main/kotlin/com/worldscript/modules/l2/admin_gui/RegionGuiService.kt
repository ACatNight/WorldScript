@file:Suppress("DEPRECATION")

package com.worldscript.modules.l2.admin_gui

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.SettingsLayout
import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionRole
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_core.RegionGeometry
import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.RegionShape
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
        val regionSlots = listRegionSlots()
        val pageCount = ((entries.size + regionSlots.size - 1) / regionSlots.size).coerceAtLeast(1)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val inventory = Bukkit.createInventory(
            RegionGuiHolder("list", page),
            inventorySize("gui.layout.list.size", 54),
            color(lang.text("gui-list-title", "WorldScript Regions")),
        )
        fillBackground(inventory, "gui.layout.list.border-slots")
        inventory.setItem(listSlot("header-slot", 4), item(guiMaterial("header", "MAP"), lang.text("gui-list-title", "WorldScript Regions"), listOf(
            lang.text("gui-list-left", "Left-click: Edit"),
            lang.text("gui-list-right", "Right-click: Teleport"),
            lang.text("gui-list-middle", "Middle-click: Global settings"),
            lang.text("gui-list-map", "Map: Open settings"),
            "&8${page + 1} / $pageCount",
        )))

        entries.drop(page * regionSlots.size).take(regionSlots.size).forEachIndexed { index, region ->
            inventory.setItem(regionSlots[index], regionItem(region, player))
        }
        inventory.setItem(listSlot("previous-slot", 45), button("previous", "ARROW", "gui-page-previous", "Previous page"))
        inventory.setItem(listSlot("close-slot", 49), button("close", "BARRIER", "gui-close", "Close"))
        inventory.setItem(listSlot("next-slot", 53), button("next", "ARROW", "gui-page-next", "Next page"))
        player.openInventory(inventory)
        playSound(player, "open")
    }

    fun openSettings(player: Player) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("settings", 0), inventorySize("gui.layout.settings.size", 27), color(lang.text("gui-settings-title", "WorldScript Settings")))
        fillBackground(inventory, "gui.layout.settings.border-slots")
        inventory.setItem(settingsSlot("discovery-slot", 10), toggleItem("discovery.enabled", "gui-setting-discovery", "Discovery"))
        inventory.setItem(settingsSlot("title-slot", 12), toggleItem("discovery.title.enabled", "gui-setting-title", "Discovery Title"))
        inventory.setItem(settingsSlot("sound-slot", 14), toggleItem("discovery.sound.enabled", "gui-setting-sound", "Discovery Sound"))
        inventory.setItem(settingsSlot("reward-slot", 16), toggleItem("discovery.reward.enabled", "gui-setting-reward", "Discovery Reward"))
        inventory.setItem(settingsSlot("toast-slot", 20), toggleItem("discovery.display.toast.enabled", "gui-setting-toast", "Discovery Toast"))
        inventory.setItem(settingsSlot("conditions-slot", 22), toggleItem("conditions.enabled", "gui-setting-conditions", "Entry Conditions"))
        inventory.setItem(settingsSlot("back-slot", 18), button("previous", "ARROW", "gui-settings-back", "Back"))
        inventory.setItem(settingsSlot("close-slot", 26), button("close", "BARRIER", "gui-close", "Close"))
        player.openInventory(inventory)
        playSound(player, "open")
    }

    private fun openToastIcons(player: Player) {
        val inventory = Bukkit.createInventory(
            RegionGuiHolder("toast-icons", 0),
            inventorySize("gui.layout.toast-icons.size", 27),
            color(lang.text("gui-toast-icons-title", "Toast icons")),
        )
        fillBackground(inventory, "gui.layout.toast-icons.border-slots")
        RegionRole.entries.forEach { role -> inventory.setItem(toastIconSlot(role), toastIconItem(role)) }
        inventory.setItem(toastIconSlot("back-slot", 18), button("previous", "ARROW", "gui-settings-back", "Back"))
        inventory.setItem(toastIconSlot("close-slot", 26), button("close", "BARRIER", "gui-close", "Close"))
        player.openInventory(inventory)
        playSound(player, "open")
    }

    private fun toggleItem(path: String, key: String, fallback: String): ItemStack {
        val enabled = plugin.config.getBoolean(path, false)
        val state = if (enabled) {
            lang.text("gui-enabled", "&aEnabled, click to disable")
        } else {
            lang.text("gui-disabled", "&cDisabled, click to enable")
        }
        return item(guiMaterial(if (enabled) "enabled" else "disabled", if (enabled) "LIME_DYE" else "GRAY_DYE"), lang.text(key, fallback), listOf(state))
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? RegionGuiHolder ?: return
        if (event.rawSlot !in 0 until event.view.topInventory.size) return
        event.isCancelled = true
        playSound(player, "click")
        if (holder.page == "toast-icons") {
            handleToastIconClick(player, event)
            return
        }
        if (holder.page == "settings") {
            when (event.rawSlot) {
                settingsSlot("discovery-slot", 10) -> toggleSetting("discovery.enabled", player)
                settingsSlot("title-slot", 12) -> toggleSetting("discovery.title.enabled", player)
                settingsSlot("sound-slot", 14) -> toggleSetting("discovery.sound.enabled", player)
                settingsSlot("reward-slot", 16) -> toggleSetting("discovery.reward.enabled", player)
                settingsSlot("toast-slot", 20) -> if (event.click.isRightClick) openToastIcons(player)
                    else toggleSetting("discovery.display.toast.enabled", player)
                settingsSlot("conditions-slot", 22) -> toggleSetting("conditions.enabled", player)
                settingsSlot("back-slot", 18) -> openList(player)
                settingsSlot("close-slot", 26) -> { player.closeInventory(); playSound(player, "close") }
            }
            return
        }
        if (holder.page != "list") return
        val page = holder.pageIndex
        when (event.rawSlot) {
            // Some clients do not emit an inventory middle-click in survival.
            // The header map is a click-type-independent settings entry point.
            listSlot("header-slot", 4) -> openSettingsNextTick(player)
            listSlot("previous-slot", 45) -> if (page > 0) openList(player, page - 1)
            listSlot("close-slot", 49) -> { player.closeInventory(); playSound(player, "close") }
            listSlot("next-slot", 53) -> openList(player, page + 1)
            in listRegionSlots() -> {
                val slotIndex = listRegionSlots().indexOf(event.rawSlot)
                val region = sortedRegions().getOrNull(page * listRegionSlots().size + slotIndex) ?: return
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
        val target = findSafeTeleport(world, region)
        if (target == null) {
            lang.send(player, "gui-teleport-no-safe-point", "region" to region.id)
            return
        }
        player.teleport(target)
        lang.send(player, "gui-teleported", "region" to region.id)
    }

    private fun findSafeTeleport(world: org.bukkit.World, region: RegionDefinition): org.bukkit.Location? {
        val min = region.bounds.min
        val max = region.bounds.max
        val cx = (min.x + max.x) / 2
        val cz = (min.z + max.z) / 2
        val candidates = sequence {
            yield(cx to cz)
            for (radius in 1..16) for (dx in -radius..radius) for (dz in -radius..radius)
                if (kotlin.math.abs(dx) == radius || kotlin.math.abs(dz) == radius) yield((cx + dx) to (cz + dz))
        }
        for ((x, z) in candidates) {
            if (!RegionGeometry.contains(region, BlockPosition(x, min.y, z))) continue
            val y = world.getHighestBlockYAt(x, z) + 1
            if (y < min.y || y > max.y + 1) continue
            val feet = world.getBlockAt(x, y, z)
            val head = world.getBlockAt(x, y + 1, z)
            val ground = world.getBlockAt(x, y - 1, z)
            if (ground.type.isSolid && feet.isPassable && head.isPassable) {
                return org.bukkit.Location(world, x + 0.5, y.toDouble(), z + 0.5)
            }
        }
        return null
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
        SettingsLayout.saveForPath(plugin, path)
        playSound(player, "success")
        openSettings(player)
    }

    private fun handleToastIconClick(player: Player, event: InventoryClickEvent) {
        val role = RegionRole.entries.firstOrNull { toastIconSlot(it) == event.rawSlot }
        if (role != null) {
            if (event.click.isRightClick && isEmpty(event.cursor)) {
                plugin.config.set("discovery.display.toast.role-items.${roleKey(role)}", null)
                SettingsLayout.save(plugin, "discovery")
                lang.send(player, "gui-toast-icon-reset", "role" to roleText(role))
            } else if (!isEmpty(event.cursor)) {
                plugin.config.set("discovery.display.toast.role-items.${roleKey(role)}", event.cursor.clone().apply { amount = 1 })
                SettingsLayout.save(plugin, "discovery")
                lang.send(player, "gui-toast-icon-saved", "role" to roleText(role))
            } else {
                lang.send(player, "gui-toast-icon-place")
            }
            playSound(player, "success")
            openToastIcons(player)
            return
        }
        when (event.rawSlot) {
            toastIconSlot("back-slot", 18) -> openSettings(player)
            toastIconSlot("close-slot", 26) -> { player.closeInventory(); playSound(player, "close") }
        }
    }

    private fun playSound(player: Player, action: String) {
        val name = plugin.config.getString("gui.sounds.$action") ?: return
        val sound = BukkitCompatibility.resolveSound(name) ?: run {
            plugin.logger.warning("Invalid GUI sound gui.sounds.$action='$name'")
            if (action != "error") playSound(player, "error")
            return
        }
        player.playSound(player.location, sound, 1.0f, 1.0f)
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
        return item(roleMaterial(region.role), region.id, lore).also { applyModelData(it, region.role) }
    }

    private fun applyModelData(stack: ItemStack, role: RegionRole) {
        if (!plugin.config.getBoolean("gui.custom-model-data.enabled", false)) return
        val key = role.name.lowercase().replace('_', '-')
        val model = plugin.config.getInt("gui.custom-model-data.$key", plugin.config.getInt("gui.custom-model-data.default", 0))
        if (model <= 0) return
        runCatching {
            val meta = stack.itemMeta ?: return
            val method = meta.javaClass.methods.firstOrNull { it.name == "setCustomModelData" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }
            method?.invoke(meta, model)
            stack.itemMeta = meta
        }.onFailure { plugin.logger.warning("Could not apply GUI custom model data: ${it.message}") }
    }

    private fun statusText(statuses: Set<GlobalRegionStatus>): String =
        if (statuses.isEmpty()) lang.text("gui-status-open", "open") else statuses.joinToString(",") { it.name.lowercase() }

    private fun roleMaterial(role: RegionRole): Material = guiMaterial(role.name.lowercase().replace('_', '-'), when (role) {
        RegionRole.HUB -> "COMPASS"
        RegionRole.OPEN_ZONE -> "GRASS_BLOCK"
        RegionRole.POINT_OF_INTEREST -> "MAP"
        RegionRole.DANGER_ZONE -> "REDSTONE"
        RegionRole.GATE -> "IRON_BARS"
    }, *(if (role == RegionRole.OPEN_ZONE) arrayOf("GRASS") else emptyArray()))

    private fun toastIconItem(role: RegionRole): ItemStack {
        val configured = plugin.config.getItemStack("discovery.display.toast.role-items.${roleKey(role)}")
        if (!isEmpty(configured)) return configured!!.clone().apply { amount = 1 }
        val material = MaterialResolver.find(plugin.config.getString("discovery.display.toast.role-icons.${roleKey(role)}").orEmpty())
            ?: roleMaterial(role)
        return item(material, roleText(role), listOf(lang.text("gui-toast-icon-place"), lang.text("gui-toast-icon-reset-hint")))
    }

    private fun roleText(role: RegionRole): String = lang.text("gui-toast-icon-${roleKey(role)}", role.name.lowercase())

    private fun roleKey(role: RegionRole): String = role.name.lowercase().replace('_', '-')

    private fun isEmpty(item: ItemStack?): Boolean = item == null || item.type == Material.AIR

    private fun material(primary: String, vararg legacy: String): Material = MaterialResolver.find(primary, *legacy) ?: Material.PAPER

    private fun button(materialKey: String, fallbackMaterial: String, key: String, fallback: String): ItemStack =
        item(guiMaterial(materialKey, fallbackMaterial), lang.text(key, fallback), emptyList())

    private fun item(material: Material, name: String, lore: List<String>): ItemStack = ItemStack(material).also { stack ->
        stack.itemMeta = stack.itemMeta?.also { meta: ItemMeta ->
            meta.setDisplayName(color(name))
            meta.lore = lore.map(::color)
        }
    }

    private fun fillBackground(inventory: Inventory, slotsPath: String) {
        val pane = item(guiMaterial("background", "GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"), " ", emptyList())
        plugin.config.getIntegerList(slotsPath).filter { it in 0 until inventory.size }.forEach { inventory.setItem(it, pane) }
    }

    private fun listSlot(key: String, fallback: Int) =
        plugin.config.getInt("gui.layout.list.$key", fallback).coerceIn(0, inventorySize("gui.layout.list.size", 54) - 1)
    private fun settingsSlot(key: String, fallback: Int) =
        plugin.config.getInt("gui.layout.settings.$key", fallback).coerceIn(0, inventorySize("gui.layout.settings.size", 27) - 1)
    private fun toastIconSlot(role: RegionRole): Int = toastIconSlot("${roleKey(role)}-slot", 10)
    private fun toastIconSlot(key: String, fallback: Int) =
        plugin.config.getInt("gui.layout.toast-icons.$key", fallback).coerceIn(0, inventorySize("gui.layout.toast-icons.size", 27) - 1)
    private fun listRegionSlots(): List<Int> {
        val first = listSlot("region-first-slot", 9)
        val last = listSlot("region-last-slot", 44)
        return (minOf(first, last)..maxOf(first, last)).filter { it in 0 until inventorySize("gui.layout.list.size", 54) }.ifEmpty { (9..44).toList() }
    }
    private fun inventorySize(path: String, fallback: Int): Int = plugin.config.getInt(path, fallback).takeIf { it in 9..54 && it % 9 == 0 } ?: fallback
    private fun guiMaterial(key: String, fallback: String, vararg legacy: String): Material =
        material(plugin.config.getString("gui.materials.$key", fallback) ?: fallback, *legacy)

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)

    private companion object {
    }
}
