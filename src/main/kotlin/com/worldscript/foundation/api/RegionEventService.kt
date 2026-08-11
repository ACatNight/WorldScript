package com.worldscript.foundation.api

import com.worldscript.foundation.model.RegionEventType
import org.bukkit.Location
import org.bukkit.entity.Player

interface RegionEventService {
    fun regionAt(location: Location): Collection<String>
    fun dispatch(player: Player, regionId: String, eventType: RegionEventType)
}
