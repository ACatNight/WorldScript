package com.worldscript

import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l3.spawn.SpawnRuntime
import com.worldscript.modules.l3.spawn.SpawnService
import com.worldscript.modules.l2.script_actions.ToastService
import org.bukkit.Material
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.event.Cancellable
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.mockito.Mockito
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Uses actual modern API classes without linking tests to a particular InventoryView representation. */
class ModernCompatibilityTest {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            ModernCompatibilityTest().adaptersWorkWhenInventoryViewIsAnInterfaceAndBlocksHavePassability()
            println("[TEST] PASS compatibility.modern: InventoryView interface and Block passability on Paper 1.21.8")
            ModernCompatibilityTest().toastIconsDoNotInitializeLegacyConversion()
            println("[TEST] PASS compatibility.modern.toast: canonical icon keys without legacy conversion")
            ModernCompatibilityTest().chunkUnloadTracksMobsWithoutCancellableOnModernApi()
            println("[TEST] PASS compatibility.modern.chunk-unload: non-cancellable Paper 1.21.8 event preserves mob tracking")
        }
    }

    fun toastIconsDoNotInitializeLegacyConversion() {
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            val service = ToastService(Mockito.mock(JavaPlugin::class.java))
            bukkit.clearInvocations()
            val resolve = ToastService::class.java.getDeclaredMethod("modernMaterialId", Material::class.java)
                .apply { isAccessible = true }
            for (name in listOf("PAPER", "STONE", "OAK_LOG", "INK_SAC", "RED_DYE")) {
                val material = java.lang.Enum.valueOf(Material::class.java, name)
                assertEquals("minecraft:${name.lowercase(java.util.Locale.ROOT)}", resolve.invoke(service, material))
            }
            val legacy = java.lang.Enum.valueOf(Material::class.java, "LEGACY_LOG")
            assertEquals(null, resolve.invoke(service, legacy), "Legacy icons should use the fallback")
            // The previous implementation calls Bukkit.getUnsafe() for every icon.
            bukkit.verifyNoInteractions()
        }
    }

    fun chunkUnloadTracksMobsWithoutCancellableOnModernApi() {
        assertFalse(Cancellable::class.java.isAssignableFrom(ChunkUnloadEvent::class.java),
            "This regression must run against the API where cancellation was removed")
        val plugin = Mockito.mock(JavaPlugin::class.java)
        Mockito.`when`(plugin.dataFolder).thenReturn(File("build/modern-compatibility"))
        val service = SpawnService(plugin, Mockito.mock(RegionCoreServiceImpl::class.java))
        val world = Mockito.mock(World::class.java)
        val chunk = Mockito.mock(Chunk::class.java)
        val entity = Mockito.mock(Entity::class.java)
        val worldId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        Mockito.`when`(world.uid).thenReturn(worldId)
        Mockito.`when`(chunk.world).thenReturn(world)
        Mockito.`when`(chunk.x).thenReturn(7)
        Mockito.`when`(chunk.z).thenReturn(-3)
        Mockito.`when`(chunk.entities).thenReturn(arrayOf(entity))
        Mockito.`when`(entity.uniqueId).thenReturn(entityId)
        @Suppress("UNCHECKED_CAST")
        val runtime = SpawnService::class.java.getDeclaredField("runtime").apply { isAccessible = true }
            .get(service) as MutableMap<String, SpawnRuntime>
        runtime["test"] = SpawnRuntime(alive = linkedSetOf(entityId))

        // Invoke the production listener using the real modern event class.
        service.onChunkUnload(ChunkUnloadEvent(chunk))
        val unloaded = SpawnService::class.java.getDeclaredField("unloaded").apply { isAccessible = true }
            .get(service) as Map<*, *>
        assertEquals(Triple(worldId, 7, -3), unloaded[entityId])
        assertEquals(setOf(entityId), runtime.getValue("test").alive.toSet())
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
