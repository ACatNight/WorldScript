package com.worldscript.modules.l3.spawn

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class SpawnMobSelectorGui(
    private val plugin: JavaPlugin,
    private val spawn: SpawnService,
) : Listener {
    private val lang = Lang(plugin)
    var editorOpener: ((Player, String, String) -> Unit)? = null

    fun openCreate(player: Player, regionId: String, page: Int = 0) {
        open(player, SpawnMobSelectorHolder("create", regionId, null, page))
    }

    fun openReplace(player: Player, regionId: String, ruleId: String, page: Int = 0) {
        open(player, SpawnMobSelectorHolder("replace", regionId, ruleId, page))
    }

    private fun open(player: Player, holder: SpawnMobSelectorHolder) {
        val mobs = mobChoices()
        val pageSize = 45
        val pageCount = ((mobs.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = holder.page.coerceIn(0, pageCount - 1)
        val inventory = Bukkit.createInventory(
            holder.copy(page = page),
            54,
            color(lang.text("spawn-gui-title", "WorldScript Spawn Mobs")),
        )
        fillBackground(inventory)
        mobs.drop(page * pageSize).take(pageSize).forEachIndexed { index, choice ->
            inventory.setItem(index, mobItem(choice))
        }
        inventory.setItem(45, button("ARROW", lang.text("gui-page-previous", "Previous page")))
        inventory.setItem(49, button("BARRIER", lang.text("gui-close", "Close")))
        inventory.setItem(53, button("ARROW", lang.text("gui-page-next", "Next page")))
        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? SpawnMobSelectorHolder ?: return
        if (event.rawSlot !in 0 until event.view.topInventory.size) return
        event.isCancelled = true
        val choices = mobChoices()
        when (event.rawSlot) {
            45 -> open(player, holder.copy(page = (holder.page - 1).coerceAtLeast(0)))
            49 -> {
                player.closeInventory()
                editorOpener?.invoke(player, holder.regionId, "spawn")
            }
            53 -> open(player, holder.copy(page = holder.page + 1))
            in 0 until 45 -> {
                val choice = choices.getOrNull(holder.page * 45 + event.rawSlot) ?: return
                if (holder.mode == "replace" && holder.ruleId != null) {
                    spawn.updateMob(holder.ruleId, choice.id, choice.provider)
                    lang.send(player, "spawn-rule-mob-saved", "rule" to holder.ruleId, "mob" to choice.id)
                    player.closeInventory()
                    editorOpener?.invoke(player, holder.regionId, "spawn-rule:${holder.ruleId}")
                } else {
                    val rule = spawn.createRule(holder.regionId, choice.id, choice.provider)
                    lang.send(player, "spawn-rule-created", "rule" to rule.id, "region" to holder.regionId, "mob" to choice.id, "amount" to rule.amount.display())
                    player.closeInventory()
                    editorOpener?.invoke(player, holder.regionId, "spawn-rule:${rule.id}")
                }
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is SpawnMobSelectorHolder) event.isCancelled = true
    }

    private fun mobChoices(): List<SpawnMobChoice> {
        val mythic = spawn.mythicMobs.mobIds().map { SpawnMobChoice(it, SpawnProvider.MYTHICMOBS) }
        if (mythic.isNotEmpty()) return mythic
        return EntityType.values()
            .filter { it.isAlive && it.isSpawnable }
            .map { SpawnMobChoice(it.name, SpawnProvider.VANILLA) }
            .sortedBy { it.id.lowercase(Locale.ROOT) }
    }

    private fun mobItem(choice: SpawnMobChoice): ItemStack {
        val material = when (choice.provider) {
            SpawnProvider.MYTHICMOBS -> MaterialResolver.find("NETHER_STAR") ?: Material.PAPER
            else -> MaterialResolver.find("ZOMBIE_SPAWN_EGG", "MONSTER_EGG") ?: Material.PAPER
        }
        val providerText = if (choice.provider == SpawnProvider.MYTHICMOBS) "MythicMobs" else "Vanilla"
        return item(material, "&e${choice.id}", listOf(
            "&7${lang.text("spawn-gui-provider", "Provider")}: &f$providerText",
            "",
            lang.text("spawn-gui-click-select", "&aClick to select this mob"),
        ))
    }

    private fun button(material: String, name: String): ItemStack =
        item(MaterialResolver.find(material) ?: Material.PAPER, name, emptyList())

    private fun item(material: Material, name: String, lore: List<String>): ItemStack = ItemStack(material).also { stack ->
        stack.itemMeta = stack.itemMeta?.also { meta: ItemMeta ->
            meta.setDisplayName(color(name))
            meta.lore = lore.map(::color)
        }
    }

    private fun fillBackground(inventory: Inventory) {
        val pane = item(MaterialResolver.find("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE") ?: Material.PAPER, " ", emptyList())
        (45 until inventory.size).forEach { inventory.setItem(it, pane) }
    }

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)
}

data class SpawnMobSelectorHolder(
    val mode: String,
    val regionId: String,
    val ruleId: String?,
    val page: Int,
) : InventoryHolder {
    override fun getInventory(): Inventory = Bukkit.createInventory(null, 9)
}

private data class SpawnMobChoice(
    val id: String,
    val provider: SpawnProvider,
)
