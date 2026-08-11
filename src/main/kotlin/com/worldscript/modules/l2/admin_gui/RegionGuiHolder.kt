package com.worldscript.modules.l2.admin_gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.Bukkit
import com.worldscript.foundation.model.ActionType
import com.worldscript.foundation.model.RegionEventType

class RegionGuiHolder(
    val page: String,
    val regionId: String? = null,
    val eventType: RegionEventType? = null,
    val actionIndex: Int = -1,
    val actionType: ActionType? = null,
    val inputKind: String? = null,
) : InventoryHolder {
    override fun getInventory(): Inventory = Bukkit.createInventory(null, 9)
}
