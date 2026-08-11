package com.worldscript.foundation.api

import com.worldscript.foundation.model.ActionDefinition
import org.bukkit.entity.Player

interface ScriptActionService {
    fun execute(player: Player, regionId: String, actions: List<ActionDefinition>)
}
