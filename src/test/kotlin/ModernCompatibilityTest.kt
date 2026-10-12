package com.worldscript

import com.worldscript.foundation.BukkitCompatibility
import org.bukkit.block.Block
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.inventory.Inventory
import java.lang.reflect.Proxy
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Uses actual modern API classes without linking tests to a particular InventoryView representation. */
class ModernCompatibilityTest {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            ModernCompatibilityTest().adaptersWorkWhenInventoryViewIsAnInterfaceAndBlocksHavePassability()
            println("[TEST] PASS compatibility.modern: InventoryView interface and Block passability on Paper 1.21.8")
        }
    }

    fun adaptersWorkWhenInventoryViewIsAnInterfaceAndBlocksHavePassability() {
        val viewType = Class.forName("org.bukkit.inventory.InventoryView")
        assertTrue(viewType.isInterface, "This suite must run on the modern API classpath")
        val inventory = Proxy.newProxyInstance(Inventory::class.java.classLoader, arrayOf(Inventory::class.java)) { _, _, _ -> null } as Inventory
        val view = Proxy.newProxyInstance(viewType.classLoader, arrayOf(viewType)) { _, method, _ ->
            if (method.name == "getTopInventory") inventory else null
        }
        val event = InventoryEvent::class.java.getConstructor(viewType).newInstance(view)
        assertSame(inventory, BukkitCompatibility.topInventory(event))
        var passable = true
        val block = Proxy.newProxyInstance(Block::class.java.classLoader, arrayOf(Block::class.java)) { _, method, _ ->
            when (method.name) {
                "isPassable" -> passable
                "isEmpty" -> false
                else -> null
            }
        } as Block
        assertTrue(BukkitCompatibility.isPassable(block), "Modern collision API should take precedence over the legacy fallback")
        passable = false
        assertFalse(BukkitCompatibility.isPassable(block))
    }
}
