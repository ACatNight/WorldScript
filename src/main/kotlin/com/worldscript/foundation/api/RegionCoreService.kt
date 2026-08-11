package com.worldscript.foundation.api

import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.foundation.model.ActionDefinition
import com.worldscript.foundation.model.RegionEventType
import com.worldscript.foundation.model.RegionStatus

interface RegionCoreService {
    fun find(id: String): RegionDefinition?
    fun effective(id: String): RegionDefinition?
    fun all(): Collection<RegionDefinition>
    fun regionsAt(location: org.bukkit.Location): List<RegionDefinition>
    fun save(region: RegionDefinition)
    fun delete(id: String): Boolean
    fun setParent(id: String, parentId: String?): Boolean
    fun setVariable(id: String, key: String, value: String): Boolean
    fun setStatus(id: String, status: RegionStatus, enabled: Boolean): Boolean
    fun updateEvent(id: String, type: RegionEventType, update: (com.worldscript.foundation.model.ScriptDefinition) -> com.worldscript.foundation.model.ScriptDefinition): Boolean
    fun addAction(id: String, type: RegionEventType, action: ActionDefinition): Boolean
    fun updateAction(id: String, type: RegionEventType, index: Int, action: ActionDefinition): Boolean
    fun removeAction(id: String, type: RegionEventType, index: Int): Boolean
}
