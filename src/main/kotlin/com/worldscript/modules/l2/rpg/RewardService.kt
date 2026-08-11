package com.worldscript.modules.l2.rpg

import com.worldscript.foundation.model.RegionStatus
import com.worldscript.foundation.model.RewardDefinition
import com.worldscript.foundation.model.RewardType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class RewardService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
    private val state: PlayerVariableService,
) {
    fun grant(player: Player, regionId: String, rewards: List<RewardDefinition>) {
        rewards.forEach { reward ->
            runCatching { grantOne(player, regionId, reward) }
                .onFailure { plugin.logger.warning("Could not grant ${reward.type} in region $regionId: ${it.message}") }
        }
    }

    private fun grantOne(player: Player, regionId: String, reward: RewardDefinition) {
        when (reward.type) {
            RewardType.ITEM -> {
                val material = Material.matchMaterial(reward.value) ?: return
                player.inventory.addItem(ItemStack(material, reward.amount.toInt().coerceAtLeast(1)))
            }
            RewardType.EXPERIENCE -> player.giveExp(reward.value.toDoubleOrNull()?.toInt() ?: reward.amount.toInt())
            RewardType.MONEY -> {
                val amount = reward.value.toDoubleOrNull() ?: reward.amount
                dispatch(plugin.config.getString("economy-command", "eco give %player% %amount%") ?: "eco give %player% %amount%", player, amount)
            }
            RewardType.COMMAND -> dispatch(reward.value, player, reward.amount)
            RewardType.UNLOCK_REGION -> regions.setStatus(reward.value, RegionStatus.UNLOCKED, true)
            RewardType.SET_VARIABLE -> reward.value.split('=', limit = 2).takeIf { it.size == 2 }?.let { state.setVariable(player, it[0].trim(), it[1]) }
            RewardType.SET_REGION_STATUS -> reward.value.split(',', limit = 2).takeIf { it.size == 2 }?.let { parts ->
                val status = runCatching { RegionStatus.valueOf(parts[1].trim().uppercase()) }.getOrNull() ?: return
                regions.setStatus(parts[0].trim(), status, true)
            }
            RewardType.MESSAGE -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', placeholders(reward.value, player, regionId, reward.amount)))
        }
    }

    private fun dispatch(command: String, player: Player, amount: Double) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(command.removePrefix("/"), player, "", amount))
    }

    private fun placeholders(value: String, player: Player, regionId: String, amount: Double): String = value
        .replace("%player%", player.name)
        .replace("%uuid%", player.uniqueId.toString())
        .replace("%region%", regionId)
        .replace("%amount%", if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString())
}
