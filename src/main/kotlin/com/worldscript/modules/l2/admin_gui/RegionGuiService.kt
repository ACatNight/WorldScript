package com.worldscript.modules.l2.admin_gui

import com.worldscript.foundation.Lang
import com.worldscript.foundation.MaterialResolver
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.ComparisonOperator
import com.worldscript.foundation.model.ConditionDefinition
import com.worldscript.foundation.model.ConditionType
import com.worldscript.foundation.model.GlobalRegionStatus
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.RegionParticleDefinition
import com.worldscript.foundation.model.RegionRole
import com.worldscript.foundation.model.RewardDefinition
import com.worldscript.foundation.model.RewardType
import com.worldscript.foundation.model.ScriptDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

class RegionGuiService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
) : Listener {
    private val lang = Lang(plugin)
    private val pendingInputs = mutableMapOf<java.util.UUID, RegionGuiHolder>()

    fun reset() = pendingInputs.clear()

    fun openList(player: Player) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("list"), 54, color(lang.text("gui-atlas-title", "WorldScript Atlas")))
        fillBackground(inventory)
        inventory.setItem(4, item(material("MAP"), lang.text("gui-atlas-title", "WorldScript Atlas"), lang.text("gui-atlas-hint", "Select a region to edit")))
        regions.all().take(REGION_SLOTS.size).forEachIndexed { index, region ->
            inventory.setItem(REGION_SLOTS[index], regionItem(region))
        }
        inventory.setItem(49, item(material("BARRIER"), lang.text("gui-close", "Close"), ""))
        player.openInventory(inventory)
    }

    private fun openRegion(player: Player, regionId: String) {
        val region = regions.find(regionId) ?: return openList(player)
        val effective = regions.effective(region.id) ?: region
        val parent = region.parentId?.let(regions::find)
        val inventory = Bukkit.createInventory(RegionGuiHolder("region", region.id), 54, color(region.displayName))
        fillBackground(inventory)

        inventory.setItem(4, item(material("MAP"), region.displayName, listOf(
            "${lang.text("gui-region-id", "ID")}: ${region.id}",
            "${lang.text("gui-region-role", "Role")}: ${region.role.name.lowercase()}",
            "${lang.text("gui-region-content", "Content")}: ${region.contentId.ifBlank { "-" }}",
        ).joinToString("|")))
        inventory.setItem(10, item(material("NAME_TAG"), lang.text("gui-card-identity", "Identity"), listOf(
            "${lang.text("gui-region-name", "Name")}: ${region.displayName}",
            "${lang.text("gui-region-role", "Role")}: ${region.role.name.lowercase()}",
        ).joinToString("|")))
        inventory.setItem(12, item(material("COMPASS"), lang.text("gui-card-location", "Location"), listOf(
            "${lang.text("gui-region-world", "World")}: ${region.worldName}",
            region.bounds.toString(),
        ).joinToString("|")))
        inventory.setItem(14, item(material("REDSTONE"), lang.text("gui-card-state", "World state"), listOf(
            "${lang.text("gui-region-status", "Status")}: ${statusText(effective.statuses)}",
            "${lang.text("gui-region-priority", "Priority")}: ${region.priority}",
        ).joinToString("|")))
        inventory.setItem(16, item(material("CHAIN", "LEASH"), lang.text("gui-card-inheritance", "Inheritance"), listOf(
            "${lang.text("gui-region-parent", "Parent")}: ${parent?.displayName ?: lang.text("gui-none", "None")}",
            "${lang.text("gui-region-inherit", "Enabled")}: ${region.inheritParent}",
        ).joinToString("|")))

        inventory.setItem(26, item(material("WOODEN_SWORD", "WOOD_SWORD"), lang.text("gui-event-left-click", "Left-click block"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(28, item(material("LIME_DYE", "INK_SACK"), lang.text("gui-event-enter", "Enter event"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(30, item(material("RED_DYE", "INK_SACK"), lang.text("gui-event-leave", "Leave event"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(32, item(material("YELLOW_DYE", "INK_SACK"), lang.text("gui-event-interact", "Legacy interact"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(34, item(material("STONE_BUTTON"), lang.text("gui-event-right-click", "Right-click block"), lang.text("gui-event-card-hint", "Open event settings")))
        inventory.setItem(38, item(material("PAPER"), lang.text("gui-card-variables", "Variables"), "${region.variables.size}"))
        inventory.setItem(40, item(material("BOOK"), lang.text("gui-card-content", "External content"), region.contentId.ifBlank { "-" }))
        val particle = region.particle ?: effective.particle
        inventory.setItem(42, item(material("END_ROD", "BLAZE_ROD"), lang.text("gui-card-particle", "Region particles"), listOf(
            lang.text(if (particle?.enabled == true) "gui-particle-enabled" else "gui-particle-disabled", "Disabled"),
            "${lang.text("gui-particle-type", "Type")}: ${particle?.type ?: "-"}",
            "${lang.text("gui-particle-count", "Count")}: ${particle?.count ?: "-"}",
        ).joinToString("|")))
        inventory.setItem(49, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openParticle(player: Player, regionId: String) {
        val region = regions.find(regionId) ?: return openList(player)
        val particle = region.particle ?: regions.effective(regionId)?.particle
        val local = region.particle
        val inventory = Bukkit.createInventory(RegionGuiHolder("particle", regionId), 27, color(lang.text("gui-particle-title", "Region particles")))
        fillBackground(inventory)
        inventory.setItem(4, item(material(if (particle?.enabled == true) "LIME_DYE" else "GRAY_DYE", "INK_SACK"), lang.text(if (particle?.enabled == true) "gui-particle-enabled" else "gui-particle-disabled", "Disabled"), lang.text("gui-toggle", "Click to toggle")))
        inventory.setItem(10, item(material("END_ROD", "BLAZE_ROD"), lang.text("gui-particle-type", "Particle type"), listOf(
            particle?.type ?: "END_ROD",
            lang.text("gui-particle-type-hint", "Left click: previous|Right click: next"),
        ).joinToString("|")))
        inventory.setItem(12, item(material("GLOWSTONE_DUST"), lang.text("gui-particle-count", "Particle count"), (particle?.count ?: 2).toString()))
        inventory.setItem(14, item(material("CLOCK"), lang.text("gui-particle-interval", "Interval"), "${particle?.intervalTicks ?: 20} ticks"))
        inventory.setItem(16, item(material("FEATHER"), lang.text("gui-particle-spread", "Spread"), particle?.let { "${it.spreadX},${it.spreadY},${it.spreadZ}" } ?: "1.5,0.8,1.5"))
        if (local == null) inventory.setItem(20, item(material("CHAIN", "LEASH"), lang.text("gui-inherit-event", "Inheritance"), lang.text("gui-particle-inherited", "Using parent settings; editing creates a local override.")))
        inventory.setItem(22, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openEvent(player: Player, regionId: String, type: RegionEventType) {
        val region = regions.find(regionId) ?: return openList(player)
        val rawScript = region.events[type]
        val script = regions.effective(regionId)?.events?.get(type) ?: rawScript ?: ScriptDefinition()
        val inherited = rawScript == null && region.parentId != null
        val inventory = Bukkit.createInventory(RegionGuiHolder("event", regionId, type), 54, color(lang.text("gui-event-${type.name.lowercase()}", type.name)))
        fillBackground(inventory)
        inventory.setItem(4, item(if (script.enabled) material("LIME_DYE", "INK_SACK") else material("GRAY_DYE", "INK_SACK"), lang.text(if (script.enabled) "gui-enabled" else "gui-disabled", "Enabled"), lang.text("gui-toggle", "Click to toggle")))
        inventory.setItem(5, item(material("CLOCK"), lang.text("gui-cooldown", "Cooldown"), "${script.cooldownSeconds}s"))
        inventory.setItem(6, item(material("COMPASS"), lang.text("gui-trigger", "Trigger"), eventModeText(script)))
        inventory.setItem(8, item(material("CHAIN", "LEASH"), lang.text("gui-inherit-event", "Inheritance"), if (inherited) lang.text("gui-inherited-readonly", "Inherited from parent|Edit this event to override") else "${lang.text("gui-inherit-from-parent", "Inherit parent event")}: ${!script.overrideParent}"))
        inventory.setItem(10, item(material("WRITABLE_BOOK"), lang.text("gui-add-action", "Add action"), lang.text("gui-add-action-lore", "Choose an action type")))
        inventory.setItem(12, item(material("PAPER"), lang.text("gui-condition-count", "Conditions"), "${script.conditions.size}|${lang.text("gui-open-list", "Click to view")}"))
        inventory.setItem(14, item(material("CHEST"), lang.text("gui-reward-count", "Rewards"), "${script.rewards.size}|${lang.text("gui-open-list", "Click to view")}"))
        script.actions.take(27).forEachIndexed { index, action ->
            val inheritedAction = rawScript?.actions?.getOrNull(index) == null && inherited
            inventory.setItem(18 + index, item(material("PAPER"), "${index + 1}. ${actionLabel(action.type)}", if (inheritedAction) "${action.value}|${lang.text("gui-inherited-readonly", "Inherited from parent|Edit this event to override")}" else action.value))
        }
        inventory.setItem(49, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openConditions(player: Player, regionId: String, eventType: RegionEventType) {
        val script = regions.effective(regionId)?.events?.get(eventType) ?: ScriptDefinition()
        val inventory = Bukkit.createInventory(RegionGuiHolder("conditions", regionId, eventType), 54, color(lang.text("gui-conditions", "Conditions")))
        fillBackground(inventory)
        inventory.setItem(4, item(material("PAPER"), lang.text("gui-conditions", "Conditions"), lang.text("gui-condition-format", "type|key|value|operator|amount")))
        script.conditions.forEachIndexed { index, condition ->
            inventory.setItem(19 + index, item(material("PAPER"), "${index + 1}. ${condition.type.name.lowercase()}", conditionSummary(condition)))
        }
        inventory.setItem(10, item(material("WRITABLE_BOOK"), lang.text("gui-add-condition", "Add condition"), lang.text("gui-condition-format", "type|key|value|operator|amount")))
        inventory.setItem(49, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openRewards(player: Player, regionId: String, eventType: RegionEventType) {
        val script = regions.effective(regionId)?.events?.get(eventType) ?: ScriptDefinition()
        val inventory = Bukkit.createInventory(RegionGuiHolder("rewards", regionId, eventType), 54, color(lang.text("gui-rewards", "Rewards")))
        fillBackground(inventory)
        inventory.setItem(4, item(material("CHEST"), lang.text("gui-rewards", "Rewards"), lang.text("gui-reward-format", "type|value|amount|once")))
        script.rewards.forEachIndexed { index, reward ->
            inventory.setItem(19 + index, item(material("CHEST"), "${index + 1}. ${reward.type.name.lowercase()}", rewardSummary(reward)))
        }
        inventory.setItem(10, item(material("WRITABLE_BOOK"), lang.text("gui-add-reward", "Add reward"), lang.text("gui-reward-format", "type|value|amount|once")))
        inventory.setItem(49, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openActionTypes(player: Player, regionId: String, eventType: RegionEventType, actionIndex: Int = -1) {
        val inventory = Bukkit.createInventory(RegionGuiHolder("types", regionId, eventType, actionIndex), 54, color(lang.text("gui-action-types", "Choose action type")))
        inventory.setItem(4, item(material("WRITABLE_BOOK"), lang.text("gui-action-types", "Choose action type"), lang.text("gui-action-types-hint", "Choose what this event should do")))
        ActionType.entries.forEachIndexed { index, type ->
            inventory.setItem(ACTION_TYPE_SLOTS[index], item(actionMaterial(type), actionLabel(type), actionDescription(type)))
        }
        inventory.setItem(49, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openActionEditor(player: Player, regionId: String, eventType: RegionEventType, index: Int) {
        val action = regions.find(regionId)?.events?.get(eventType)?.actions?.getOrNull(index) ?: return openEvent(player, regionId, eventType)
        val inventory = Bukkit.createInventory(RegionGuiHolder("action", regionId, eventType, index, action.type), 27, color("${index + 1}. ${action.type.name}"))
        inventory.setItem(4, item(actionMaterial(action.type), actionLabel(action.type), action.value))
        inventory.setItem(10, item(material("NAME_TAG"), lang.text("gui-change-type", "Change type"), ""))
        inventory.setItem(12, item(material("WRITABLE_BOOK"), lang.text("gui-edit-value", "Edit value"), action.value))
        inventory.setItem(14, item(material("LAVA_BUCKET"), lang.text("gui-delete-action", "Delete action"), ""))
        inventory.setItem(22, item(material("BARRIER"), lang.text("gui-back", "Back"), ""))
        player.openInventory(inventory)
    }

    private fun openTextInput(player: Player, holder: RegionGuiHolder) {
        pendingInputs[player.uniqueId] = holder
        player.closeInventory()
        if (holder.inputKind == "cooldown") lang.send(player, "gui-cooldown-prompt")
        else when (holder.inputKind) {
            "condition" -> lang.send(player, "gui-condition-prompt")
            "reward" -> lang.send(player, "gui-reward-prompt")
            "particle-type" -> lang.send(player, "gui-particle-prompt-type")
            "particle-count" -> lang.send(player, "gui-particle-prompt-count")
            "particle-interval" -> lang.send(player, "gui-particle-prompt-interval")
            "particle-spread" -> lang.send(player, "gui-particle-prompt-spread")
            else -> lang.send(player, "gui-action-prompt", "type" to (holder.actionType?.name ?: "MESSAGE"))
        }
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
                42 -> openParticle(player, regionId ?: return)
                49 -> openList(player)
            }
            "particle" -> {
                val rid = regionId ?: return
                when (event.rawSlot) {
                    4 -> {
                        val current = regions.find(rid)?.particle ?: regions.effective(rid)?.particle ?: RegionParticleDefinition()
                        regions.updateParticle(rid, current.copy(enabled = !current.enabled))
                        openParticle(player, rid)
                    }
                    10 -> {
                        val current = regions.find(rid)?.particle ?: regions.effective(rid)?.particle ?: RegionParticleDefinition()
                        val currentIndex = PARTICLE_TYPES.indexOf(current.type).takeIf { it >= 0 } ?: 0
                        val offset = if (event.click == ClickType.LEFT) -1 else 1
                        val next = PARTICLE_TYPES[(currentIndex + offset + PARTICLE_TYPES.size) % PARTICLE_TYPES.size]
                        regions.updateParticle(rid, current.copy(type = next, enabled = true))
                        openParticle(player, rid)
                    }
                    12 -> openTextInput(player, RegionGuiHolder("chat", rid, inputKind = "particle-count"))
                    14 -> openTextInput(player, RegionGuiHolder("chat", rid, inputKind = "particle-interval"))
                    16 -> openTextInput(player, RegionGuiHolder("chat", rid, inputKind = "particle-spread"))
                    22 -> openRegion(player, rid)
                }
            }
            "event" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when {
                    event.rawSlot == 4 -> { regions.toggleEvent(rid, type); openEvent(player, rid, type) }
                    event.rawSlot == 5 -> openTextInput(player, RegionGuiHolder("chat", rid, type, inputKind = "cooldown"))
                    event.rawSlot == 10 -> openActionTypes(player, rid, type)
                    event.rawSlot == 12 -> openConditions(player, rid, type)
                    event.rawSlot == 14 -> openRewards(player, rid, type)
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
            "conditions" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when (event.rawSlot) {
                    10 -> openTextInput(player, RegionGuiHolder("chat", rid, type, inputKind = "condition"))
                    49 -> openEvent(player, rid, type)
                }
            }
            "rewards" -> {
                val type = holder.eventType ?: return
                val rid = regionId ?: return
                when (event.rawSlot) {
                    10 -> openTextInput(player, RegionGuiHolder("chat", rid, type, inputKind = "reward"))
                    49 -> openEvent(player, rid, type)
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
        when (holder.inputKind) {
            "cooldown" -> {
                val type = holder.eventType ?: return
                val seconds = value.toLongOrNull()?.takeIf { it >= 0 }
                if (seconds == null) {
                    lang.send(player, "gui-cooldown-invalid")
                    openEvent(player, regionId, type)
                    return
                }
                regions.updateEvent(regionId, type) { it.copy(cooldownSeconds = seconds) }
            }
            "action" -> {
                val type = holder.eventType ?: return
                val action = ActionDefinition(holder.actionType ?: ActionType.MESSAGE, value)
                if (holder.actionIndex >= 0) regions.updateAction(regionId, type, holder.actionIndex, action) else regions.addAction(regionId, type, action)
            }
            "condition" -> {
                val type = holder.eventType ?: return
                val condition = parseCondition(value) ?: run { lang.send(player, "gui-condition-invalid"); return }
                regions.updateEvent(regionId, type) { it.copy(conditions = it.conditions + condition) }
            }
            "reward" -> {
                val type = holder.eventType ?: return
                val reward = parseReward(value) ?: run { lang.send(player, "gui-reward-invalid"); return }
                regions.updateEvent(regionId, type) { it.copy(rewards = it.rewards + reward) }
            }
            "particle-type" -> {
                val type = runCatching { Particle.valueOf(value.uppercase()) }.getOrNull()
                if (type == null) return particleInputInvalid(player, regionId)
                updateParticle(regionId) { it.copy(type = type.name) }
            }
            "particle-count" -> {
                val count = value.toIntOrNull()?.takeIf { it in 1..64 } ?: return particleInputInvalid(player, regionId)
                updateParticle(regionId) { it.copy(count = count) }
            }
            "particle-interval" -> {
                val interval = value.toLongOrNull()?.takeIf { it >= 1 } ?: return particleInputInvalid(player, regionId)
                updateParticle(regionId) { it.copy(intervalTicks = interval) }
            }
            "particle-spread" -> {
                val values = value.split(',').map { it.trim().toDoubleOrNull() }
                if (values.size != 3 || values.any { it == null || it < 0.0 || it > 16.0 }) return particleInputInvalid(player, regionId)
                updateParticle(regionId) { it.copy(spreadX = values[0]!!, spreadY = values[1]!!, spreadZ = values[2]!!) }
            }
        }
        if (holder.inputKind?.startsWith("particle-") == true) openParticle(player, regionId)
        else openEvent(player, regionId, holder.eventType ?: return)
    }

    private fun updateParticle(regionId: String, update: (RegionParticleDefinition) -> RegionParticleDefinition) {
        val current = regions.find(regionId)?.particle ?: regions.effective(regionId)?.particle ?: RegionParticleDefinition()
        regions.updateParticle(regionId, update(current))
    }

    private fun particleInputInvalid(player: Player, regionId: String) {
        lang.send(player, "gui-particle-invalid")
        openParticle(player, regionId)
    }

    private fun parseCondition(value: String): ConditionDefinition? {
        val parts = value.split('|')
        if (parts.size < 3) return null
        val type = runCatching { ConditionType.valueOf(parts[0].trim().uppercase()) }.getOrNull() ?: return null
        val operator = parts.getOrNull(3)?.trim()?.takeIf(String::isNotEmpty)
            ?.let { runCatching { ComparisonOperator.valueOf(it.uppercase()) }.getOrNull() }
            ?: ComparisonOperator.EQUALS
        val amount = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 1
        return ConditionDefinition(type, parts[1].trim(), parts[2].trim(), operator, amount)
    }

    private fun parseReward(value: String): RewardDefinition? {
        val parts = value.split('|')
        if (parts.size < 2) return null
        val type = runCatching { RewardType.valueOf(parts[0].trim().uppercase()) }.getOrNull() ?: return null
        return RewardDefinition(type, parts[1].trim(), parts.getOrNull(2)?.toDoubleOrNull() ?: 1.0, parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false)
    }

    private fun conditionSummary(condition: ConditionDefinition): String =
        listOf(condition.key, condition.operator.name.lowercase(), condition.value).filter(String::isNotBlank).joinToString(" ")

    private fun rewardSummary(reward: RewardDefinition): String =
        listOf(reward.value, reward.amount.toString(), if (reward.once) "once" else "repeat").joinToString(" ")

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
        ActionType.KETHER -> material("ENCHANTED_BOOK")
        ActionType.MESSAGE -> material("PAPER")
        ActionType.PLAYER_COMMAND -> material("COMMAND_BLOCK")
        ActionType.CONSOLE_COMMAND -> material("CHAIN_COMMAND_BLOCK", "COMMAND")
        ActionType.TELEPORT -> material("ENDER_PEARL")
        ActionType.SET_VARIABLE -> material("WRITABLE_BOOK")
        ActionType.SET_REGION_STATUS -> material("REDSTONE")
        ActionType.GIVE_ITEM -> material("CHEST")
        ActionType.GIVE_EXPERIENCE -> material("EXPERIENCE_BOTTLE")
        ActionType.GIVE_MONEY -> material("GOLD_INGOT")
        ActionType.UNLOCK_REGION -> material("TRIPWIRE_HOOK")
        ActionType.COMPLETE_REGION -> material("NETHER_STAR")
    }

    private fun roleMaterial(role: RegionRole): Material = when (role) {
        RegionRole.HUB -> material("COMPASS")
        RegionRole.OPEN_ZONE -> material("GRASS_BLOCK", "GRASS")
        RegionRole.POINT_OF_INTEREST -> material("MAP")
        RegionRole.DANGER_ZONE -> material("REDSTONE")
        RegionRole.GATE -> material("IRON_BARS")
    }

    private fun material(primary: String, vararg legacy: String): Material = MaterialResolver.find(primary, *legacy) ?: Material.PAPER

    private fun item(material: Material, name: String, lore: String): ItemStack = ItemStack(material).also { stack ->
        stack.itemMeta = stack.itemMeta?.also { meta: ItemMeta ->
            meta.setDisplayName(color(name))
            meta.lore = lore.split('|').map(::color)
        }
    }

    private fun color(value: String) = ChatColor.translateAlternateColorCodes('&', value)

    private fun fillBackground(inventory: org.bukkit.inventory.Inventory) {
        val pane = item(material("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"), " ", "")
        BORDER_SLOTS.filter { it in 0 until inventory.size }.forEach { slot -> inventory.setItem(slot, pane) }
    }

    private fun RegionGuiHolder.copyForInput(kind: String) = RegionGuiHolder("anvil", regionId, eventType, actionIndex, actionType, kind)

    private companion object {
        val REGION_SLOTS = (10..43).toList()
        val ACTION_TYPE_SLOTS = listOf(10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34)
        val PARTICLE_TYPES = listOf(
            "END_ROD", "FLAME", "HEART", "CLOUD", "CRIT", "ENCHANT",
            "FIREWORK", "PORTAL", "TOTEM", "WITCH", "HAPPY_VILLAGER", "LAVA",
        )
        val BORDER_SLOTS = listOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53)
    }
}
