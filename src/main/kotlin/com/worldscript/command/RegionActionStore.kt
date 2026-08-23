package com.worldscript.command

import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl

/**
 * Owns action lookup and local-override rules for the chat editor.
 *
 * Discovery actions are stored beside DiscoveryDefinition while normal
 * actions are stored on ScriptDefinition. Keeping that distinction here
 * prevents the editor from accumulating storage-specific branches.
 */
internal class RegionActionStore(
    private val regions: RegionCoreServiceImpl,
) {
    fun get(region: RegionDefinition, key: String, index: Int): ActionDefinition? =
        if (isDiscovery(key)) {
            regions.effective(region.id)?.discovery?.configuredActions()?.getOrNull(index)
        } else {
            eventType(key)?.let { type ->
                regions.effective(region.id)?.events?.get(type)?.actions?.getOrNull(index)
            }
        }

    fun ensureLocal(region: RegionDefinition, key: String, index: Int): Boolean {
        return if (isDiscovery(key)) {
            if (region.discovery?.configuredActions()?.getOrNull(index) != null) return true
            val inherited = regions.effective(region.id)?.discovery?.configuredActions() ?: return false
            if (index !in inherited.indices) return false
            regions.updateDiscovery(region.id) { it.copy(actions = inherited) }
            true
        } else {
            val type = eventType(key) ?: return false
            if (region.events[type]?.actions?.getOrNull(index) != null) return true
            val inherited = regions.effective(region.id)?.events?.get(type)?.actions ?: return false
            if (index !in inherited.indices) return false
            regions.updateEvent(region.id, type) { it.copy(actions = inherited) }
            true
        }
    }

    fun add(regionId: String, key: String, action: ActionDefinition): Boolean =
        if (isDiscovery(key)) {
            regions.updateDiscovery(regionId) { it.copy(actions = it.configuredActions() + action) }
        } else {
            eventType(key)?.let { regions.addAction(regionId, it, action) } ?: false
        }

    fun update(regionId: String, key: String, index: Int, action: ActionDefinition): Boolean =
        if (isDiscovery(key)) {
            regions.updateDiscovery(regionId) {
                it.copy(actions = it.configuredActions().toMutableList().also { actions ->
                    if (index in actions.indices) actions[index] = action
                })
            }
        } else {
            eventType(key)?.let { regions.updateAction(regionId, it, index, action) } ?: false
        }

    fun remove(regionId: String, key: String, index: Int): Boolean =
        if (isDiscovery(key)) {
            regions.updateDiscovery(regionId) {
                it.copy(actions = it.configuredActions().toMutableList().also { actions ->
                    if (index in actions.indices) actions.removeAt(index)
                })
            }
        } else {
            eventType(key)?.let { regions.removeAction(regionId, it, index) } ?: false
        }

    fun isKnown(key: String): Boolean = isDiscovery(key) || eventType(key) != null

    private fun isDiscovery(key: String): Boolean = key.equals("discovery", true)

    private fun eventType(key: String): RegionEventType? =
        RegionEventMenu.entries.firstOrNull { it.key.equals(key, true) }?.type
}
