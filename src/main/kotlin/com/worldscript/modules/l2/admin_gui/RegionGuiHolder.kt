package com.worldscript.modules.l2.admin_gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.Bukkit

class RegionGuiHolder(
    val page: String,
    val pageIndex: Int = 0,
) : InventoryHolder {
    override fun getInventory(): Inventory = Bukkit.createInventory(null, 9)
}
