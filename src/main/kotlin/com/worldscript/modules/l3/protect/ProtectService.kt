package com.worldscript.modules.l3.protect

import com.worldscript.foundation.Lang
import com.worldscript.foundation.SettingsLayout
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class ProtectService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
) : Listener {
    private val lang = Lang(plugin)
    private var pvpSettings = readPvpSettings()
    private var messageEnabled = plugin.config.getBoolean("protect.pvp.message.enabled", true)
    private var messageCooldownMs = plugin.config.getLong("protect.pvp.message.cooldown-ms", 1500L).coerceAtLeast(0L)
    private val lastMessage = mutableMapOf<UUID, Long>()

    fun reload(refreshSettingsLayout: Boolean = true) {
        if (refreshSettingsLayout) SettingsLayout.reload(plugin)
        pvpSettings = readPvpSettings()
        messageEnabled = plugin.config.getBoolean("protect.pvp.message.enabled", true)
        messageCooldownMs = plugin.config.getLong("protect.pvp.message.cooldown-ms", 1500L).coerceAtLeast(0L)
        lastMessage.clear()
    }

    fun test(sender: CommandSender, player: Player) {
        val region = activeRegion(player.location)
        val decision = ProtectPolicy.decidePvp(region, pvpSettings)
        lang.send(
            sender,
            "protect-test-result",
            "player" to player.name,
            "region" to (region?.id ?: "-"),
            "status" to region?.statuses.orEmpty().joinToString(", ") { it.name.lowercase() }.ifBlank { "-" },
            "result" to if (decision.allowed) lang.text("protect-result-allowed", "allowed") else lang.text("protect-result-blocked", "blocked"),
        )
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (!plugin.config.getBoolean("protect.enabled", true)) return
        val victim = event.entity as? Player ?: return
        val attacker = attacker(event) ?: return
        if (attacker.uniqueId == victim.uniqueId) return
        val attackerRegion = activeRegion(attacker.location)
        val victimRegion = activeRegion(victim.location)
        val attackerDecision = ProtectPolicy.decidePvp(attackerRegion, pvpSettings)
        val victimDecision = ProtectPolicy.decidePvp(victimRegion, pvpSettings)
        if (attackerDecision.allowed && victimDecision.allowed) return
        event.isCancelled = true
        sendBlocked(attacker, victimRegion ?: attackerRegion, victimDecision.takeUnless { it.allowed } ?: attackerDecision)
    }

    private fun attacker(event: EntityDamageByEntityEvent): Player? {
        (event.damager as? Player)?.let { return it }
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
    }

    private fun activeRegion(location: Location): RegionDefinition? =
        regions.regionsAt(location, { true })
            .lastOrNull()
            ?.let { regions.effective(it.id) ?: it }

    private fun sendBlocked(player: Player, region: RegionDefinition?, decision: ProtectDecision) {
        if (!messageEnabled) return
        val now = System.currentTimeMillis()
        val last = lastMessage[player.uniqueId] ?: 0L
        if (now - last < messageCooldownMs) return
        lastMessage[player.uniqueId] = now
        lang.send(
            player,
            "protect-pvp-blocked",
            "region" to (region?.displayName ?: region?.id ?: "-"),
            "status" to decision.matchedStatus.ifBlank { "-" },
        )
    }

    private fun readPvpSettings(): ProtectPvpSettings =
        ProtectPvpSettings(
            enabled = plugin.config.getBoolean("protect.pvp.enabled", true),
            defaultAllow = plugin.config.getBoolean("protect.pvp.default-allow", true),
            blockedStatuses = plugin.config.getStringList("protect.pvp.blocked-statuses")
                .map(ProtectPolicy::normalizeStatus)
                .filter { it.isNotBlank() }
                .toSet()
                .ifEmpty { setOf("peaceful") },
            allowedStatuses = plugin.config.getStringList("protect.pvp.allowed-statuses")
                .map(ProtectPolicy::normalizeStatus)
                .filter { it.isNotBlank() }
                .toSet()
                .ifEmpty { setOf("dangerous") },
        )
}
