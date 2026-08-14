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
    fun grant(player: Player, regionId: String, rewards: List<RewardDefinition>, eventKey: String? = null) {
        rewards.forEachIndexed { index, reward ->
            val rewardKey = "${eventKey ?: regionId}:$index"
            if (reward.once && state.isRewardClaimed(player, rewardKey)) return@forEachIndexed
            runCatching { grantOne(player, regionId, reward) }
                .onSuccess { granted -> if (reward.once && granted) state.claimReward(player, rewardKey) }
                .onFailure { plugin.logger.warning("Could not grant ${reward.type} in region $regionId: ${it.message}") }
        }
    }

    private fun grantOne(player: Player, regionId: String, reward: RewardDefinition): Boolean {
        return when (reward.type) {
            RewardType.ITEM -> {
                val material = Material.matchMaterial(reward.value) ?: return false
                player.inventory.addItem(ItemStack(material, reward.amount.toInt().coerceAtLeast(1)))
                true
            }
            RewardType.EXPERIENCE -> {
                player.giveExp(reward.value.toDoubleOrNull()?.toInt() ?: reward.amount.toInt())
                true
            }
            RewardType.MONEY -> {
                val amount = reward.value.toDoubleOrNull() ?: reward.amount
                dispatch(plugin.config.getString("economy-command", "eco give %player% %amount%") ?: "eco give %player% %amount%", player, amount)
                true
            }
            RewardType.COMMAND -> {
                dispatch(reward.value, player, reward.amount)
                true
            }
            RewardType.UNLOCK_REGION -> {
                state.unlockRegion(player, reward.value)
                true
            }
            RewardType.SET_VARIABLE -> {
                val parts = reward.value.split('=', limit = 2)
                if (parts.size != 2) return false
                state.setVariable(player, parts[0].trim(), parts[1])
                true
            }
            RewardType.SET_REGION_STATUS -> {
                val parts = reward.value.split(',', limit = 2)
                if (parts.size != 2) return false
                val status = runCatching { RegionStatus.valueOf(parts[1].trim().uppercase()) }.getOrNull() ?: return false
                if (status == RegionStatus.COMPLETED) state.markRegionCompleted(player, parts[0].trim())
                regions.setStatus(parts[0].trim(), status, true)
                true
            }
            RewardType.MESSAGE -> {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', placeholders(reward.value, player, regionId, reward.amount)))
                true
            }
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
