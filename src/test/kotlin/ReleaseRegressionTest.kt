package com.worldscript

import com.worldscript.foundation.BukkitCompatibility
import com.worldscript.foundation.api.RegionCoreService
import com.worldscript.foundation.model.*
import com.worldscript.foundation.module.ModuleState
import com.worldscript.foundation.module.WorldScriptModuleManager
import com.worldscript.modules.l1.region_core.EditorToolService
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_events.*
import com.worldscript.modules.l2.admin_gui.RegionGuiHolder
import com.worldscript.modules.l2.admin_gui.RegionGuiService
import com.worldscript.modules.l2.rpg.ConditionEvaluator
import com.worldscript.modules.l2.rpg.PlayerVariableService
import com.worldscript.modules.l2.rpg.RewardService
import com.worldscript.modules.l2.script_actions.ScriptActionServiceImpl
import com.worldscript.modules.l2.script_actions.ToastService
import com.worldscript.modules.l3.spawn.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import com.worldscript.modules.l3.protect.ProtectService
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.io.File
import java.nio.file.Path
import java.util.UUID
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.logging.Logger
import kotlin.test.*

/** Regression tests exercise the production services with a controlled Bukkit boundary. */
class ReleaseRegressionTest {
    @TempDir lateinit var directory: Path

    private class Fixture(directory: Path) {
        val config = YamlConfiguration()
        val server = Mockito.mock(Server::class.java)
        val scheduler = Mockito.mock(BukkitScheduler::class.java)
        val manager = Mockito.mock(PluginManager::class.java)
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val scheduled = mutableListOf<Runnable>()

        init {
            Mockito.`when`(plugin.dataFolder).thenReturn(directory.toFile())
            Mockito.`when`(plugin.config).thenReturn(config)
            Mockito.`when`(plugin.server).thenReturn(server)
            Mockito.`when`(plugin.description).thenReturn(PluginDescriptionFile("WorldScript", "1.0.0", "com.worldscript.WorldScriptPlugin"))
            Mockito.`when`(plugin.logger).thenReturn(Logger.getLogger("WorldScript-tests"))
            Mockito.`when`(server.scheduler).thenReturn(scheduler)
            Mockito.`when`(server.pluginManager).thenReturn(manager)
            Mockito.`when`(scheduler.runTask(Mockito.eq(plugin), Mockito.any(Runnable::class.java))).thenAnswer {
                scheduled += it.getArgument<Runnable>(1)
                Mockito.mock(BukkitTask::class.java)
            }
            Mockito.`when`(scheduler.runTaskTimer(Mockito.eq(plugin), Mockito.any(Runnable::class.java), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(Mockito.mock(BukkitTask::class.java))
        }
    }

    private fun region(id: String = "valley", script: ScriptDefinition = ScriptDefinition()) = RegionDefinition(
        id, "Valley", "world", "world", RegionBounds(BlockPosition(0, 0, 0), BlockPosition(10, 100, 10)),
        events = mapOf(RegionEventType.ENTER to script),
    )

    private fun player(world: World): Player = Mockito.mock(Player::class.java).also {
        Mockito.`when`(world.name).thenReturn("world")
        Mockito.`when`(it.uniqueId).thenReturn(UUID.randomUUID())
        Mockito.`when`(it.name).thenReturn("Tester")
        Mockito.`when`(it.world).thenReturn(world)
        Mockito.`when`(it.location).thenReturn(Location(world, 1.0, 64.0, 1.0))
        Mockito.`when`(it.isOnline).thenReturn(true)
    }

    @Test fun repeatModeRecordsFirstVisitAndRunsOnSecondVisit() {
        val fixture = Fixture(directory)
        val core = Mockito.mock(RegionCoreServiceImpl::class.java)
        val state = PlayerVariableService(fixture.plugin)
        val world = Mockito.mock(World::class.java)
        val player = player(world)
        val script = ScriptDefinition(repeatEntryOnly = true, actions = listOf(ActionDefinition(ActionType.MESSAGE, "again")))
        Mockito.`when`(core.effective("valley")).thenReturn(region(script = script))
        val service = ScriptActionServiceImpl(fixture.plugin, core, state,
            Mockito.mock(ConditionEvaluator::class.java), Mockito.mock(RewardService::class.java), Mockito.mock(ToastService::class.java))
        try {
            service.onEnter(RegionEnterEvent(player, "valley"))
            assertTrue(state.hasEnteredRegion(player, "valley"))
            Mockito.verify(player, Mockito.never()).sendMessage("again")
            service.onEnter(RegionEnterEvent(player, "valley"))
            Mockito.verify(player, Mockito.times(1)).sendMessage("again")
        } finally { state.saveAll() }
    }

    @Test fun inventoryOverflowDropsRemainderAndOnceRewardDoesNotDuplicate() {
        val fixture = Fixture(directory)
        val core = Mockito.mock(RegionCoreServiceImpl::class.java)
        val state = PlayerVariableService(fixture.plugin)
        val world = Mockito.mock(World::class.java)
        val player = player(world)
        val inventory = Mockito.mock(PlayerInventory::class.java)
        val remaining = ItemStack(Material.DIAMOND, 3)
        Mockito.`when`(player.inventory).thenReturn(inventory)
        Mockito.`when`(inventory.addItem(Mockito.any(ItemStack::class.java))).thenReturn(hashMapOf(0 to remaining))
        val reward = RewardDefinition(RewardType.ITEM, "DIAMOND", 5.0, once = true)
        try {
            val service = RewardService(fixture.plugin, core, state)
            service.grant(player, "valley", listOf(reward))
            assertTrue(state.isRewardClaimed(player, "valley:0"))
            service.grant(player, "valley", listOf(reward))
            Mockito.verify(inventory, Mockito.times(1)).addItem(Mockito.any(ItemStack::class.java))
            Mockito.verify(world, Mockito.times(1)).dropItemNaturally(player.location, remaining)
        } finally { state.saveAll() }
    }

    @Test fun economyUsesSplitSettingAndFailedCommandIsNotClaimed() {
        val fixture = Fixture(directory)
        fixture.config.set("economy.economy-command", "money add %player% %amount%")
        val core = Mockito.mock(RegionCoreServiceImpl::class.java)
        val state = PlayerVariableService(fixture.plugin)
        val player = player(Mockito.mock(World::class.java))
        val console = Mockito.mock(org.bukkit.command.ConsoleCommandSender::class.java)
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<org.bukkit.command.ConsoleCommandSender> { Bukkit.getConsoleSender() }.thenReturn(console)
            bukkit.`when`<Boolean> { Bukkit.dispatchCommand(console, "money add Tester 12") }.thenReturn(false)
            try {
                RewardService(fixture.plugin, core, state).grant(player, "valley", listOf(RewardDefinition(RewardType.MONEY, "12", once = true)))
                bukkit.verify { Bukkit.dispatchCommand(console, "money add Tester 12") }
                assertFalse(state.isRewardClaimed(player, "valley:0"))
            } finally { state.saveAll() }
        }
    }

    @Test fun cancelledOrRedirectedTeleportNeverUsesOldDestination() {
        val fixture = Fixture(directory)
        val world = Mockito.mock(World::class.java)
        val player = player(world)
        val origin = player.location
        val destination = Location(world, 200.0, 64.0, 200.0)
        val core = Mockito.mock(RegionCoreService::class.java)
        Mockito.`when`(core.regionsAt(origin)).thenReturn(emptyList())
        val state = Mockito.mock(PlayerVariableService::class.java)
        val service = RegionEventServiceImpl(fixture.plugin, core, state, Mockito.mock(ConditionEvaluator::class.java),
            { _, _, _, _ -> }, EditorToolService(fixture.plugin))
        val event = PlayerTeleportEvent(player, origin, destination)
        // A later plugin can still cancel after this callback; the task must use actual location.
        service.onTeleportCommitted(event)
        event.isCancelled = true
        fixture.scheduled.single().run()
        Mockito.verify(core, Mockito.never()).regionsAt(destination)
        Mockito.verify(fixture.manager, Mockito.never()).callEvent(Mockito.any(RegionEnterEvent::class.java))
        Mockito.verify(fixture.manager, Mockito.never()).callEvent(Mockito.any(RegionDiscoverEvent::class.java))
        fixture.scheduled.clear()
        service.onTeleportCommitted(event)
        assertTrue(fixture.scheduled.isEmpty())
    }

    @Test fun regionEditsKeepOriginalFilenameAndDeleteAllDuplicateSources() {
        val fixture = Fixture(directory)
        val folder = directory.resolve("regions").toFile().apply { mkdirs() }
        val original = File(folder, "01-simple-region.yml")
        original.writeText("id: valley\nidentity:\n  name: Before\nlocation:\n  world: world\n  min: {x: 0, y: 0, z: 0}\n  max: {x: 10, y: 100, z: 10}\n")
        val core = RegionCoreServiceImpl(fixture.plugin)
        core.load()
        assertTrue(core.setDisplayName("valley", "After"))
        assertFalse(File(folder, "valley.yml").exists())
        core.load()
        assertEquals("After", core.find("valley")?.displayName)
        original.copyTo(File(folder, "duplicate.yml"))
        core.load()
        assertTrue(core.delete("valley"))
        core.load()
        assertNull(core.find("valley"))
        assertFalse(original.exists())
        assertFalse(File(folder, "duplicate.yml").exists())
    }

    @Test fun newRegionDoesNotOverwriteAnotherRegionsUserNamedSource() {
        val fixture = Fixture(directory)
        val folder = directory.resolve("regions").toFile().apply { mkdirs() }
        val source = File(folder, "valley.yml")
        source.writeText("id: other\nlocation:\n  world: world\n  min: {x: 0, y: 0, z: 0}\n  max: {x: 10, y: 100, z: 10}\n")
        val core = RegionCoreServiceImpl(fixture.plugin)
        core.load()
        core.save(region())
        core.load()
        assertNotNull(core.find("other"))
        assertNotNull(core.find("valley"))
        assertEquals("other", YamlConfiguration.loadConfiguration(source).getString("id"))
    }

    @Test fun shiftClickFromPlayerInventoryIsCancelledInBothMenus() {
        val fixture = Fixture(directory)
        val world = Mockito.mock(World::class.java)
        val player = player(world)
        val top = Mockito.mock(Inventory::class.java)
        Mockito.`when`(top.size).thenReturn(54)
        val bottom = Mockito.mock(PlayerInventory::class.java)
        Mockito.`when`(bottom.size).thenReturn(36)
        val view = Mockito.mock(InventoryView::class.java)
        Mockito.`when`(view.topInventory).thenReturn(top)
        Mockito.`when`(view.bottomInventory).thenReturn(bottom)
        Mockito.`when`(view.player).thenReturn(player)
        val core = Mockito.mock(RegionCoreServiceImpl::class.java)
        Mockito.`when`(top.holder).thenReturn(RegionGuiHolder("list", 0))
        val regionClick = InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
        RegionGuiService(fixture.plugin, core).onClick(regionClick)
        assertTrue(regionClick.isCancelled)
        Mockito.`when`(top.holder).thenReturn(SpawnMobSelectorHolder("create", "valley", null, 0))
        val spawnClick = InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
        SpawnMobSelectorGui(fixture.plugin, SpawnService(fixture.plugin, core)).onClick(spawnClick)
        assertTrue(spawnClick.isCancelled)
    }

    @Test fun compatibilityAdapterWorksWithLegacyInventoryViewAndBlocks() {
        val top = Mockito.mock(Inventory::class.java)
        val view = Mockito.mock(InventoryView::class.java)
        Mockito.`when`(view.topInventory).thenReturn(top)
        assertSame(top, BukkitCompatibility.topInventory(org.bukkit.event.inventory.InventoryEvent(view)))
        val block = Mockito.mock(Block::class.java)
        Mockito.`when`(block.isEmpty).thenReturn(true)
        assertTrue(BukkitCompatibility.isPassable(block))
        Mockito.`when`(block.isEmpty).thenReturn(false)
        assertFalse(BukkitCompatibility.isPassable(block))
        assertNotNull(BukkitCompatibility.resolveSound("BLOCK_NOTE_BLOCK_PLING"))
    }

    @Test fun disabledFailedAndCircularDependenciesBlockTheirConsumers() {
        val fixture = Fixture(directory)
        fixture.config.set("modules.load-external", true)
        fixture.config.set("modules.auto-install-official", false)
        fixture.config.set("modules.disabled", listOf("a"))
        val folder = directory.resolve("modules").toFile().apply { mkdirs() }
        fun module(id: String, dependency: String? = null, api: Int = 1) {
            JarOutputStream(File(folder, "$id.jar").outputStream()).use {
                it.putNextEntry(JarEntry("module.yml"))
                it.write("id: $id\napi-version: $api\nworldscript-version: '>=1.0.0'\nmain: no.such.Module\ndependencies: ${dependency?.let { dep -> "[$dep]" } ?: "[]"}\n".toByteArray())
                it.closeEntry()
            }
        }
        module("a")
        module("b", "a")
        module("bad", api = 2)
        module("consumer", "bad")
        module("loadfail")
        module("loadfailconsumer", "loadfail")
        module("cycle1", "cycle2")
        module("cycle2", "cycle1")
        val manager = WorldScriptModuleManager(fixture.plugin)
        manager.initialize()
        assertEquals(ModuleState.DISABLED, manager.find("a")?.state)
        assertEquals("modules-reason-missing-dependency", manager.find("b")?.reasonKey)
        assertEquals("modules-reason-missing-dependency", manager.find("consumer")?.reasonKey)
        assertEquals("modules-reason-load-failed", manager.find("loadfail")?.reasonKey)
        assertEquals("modules-reason-missing-dependency", manager.find("loadfailconsumer")?.reasonKey)
        assertEquals(ModuleState.FAILED, manager.find("cycle1")?.state)
        assertEquals(ModuleState.FAILED, manager.find("cycle2")?.state)
        manager.close()
    }

    @Test fun cancelledLegacyChunkUnloadDoesNotChangeSpawnTracking() {
        val fixture = Fixture(directory)
        val service = SpawnService(fixture.plugin, Mockito.mock(RegionCoreServiceImpl::class.java))
        val chunk = Mockito.mock(Chunk::class.java)
        val event = ChunkUnloadEvent(chunk)
        event.isCancelled = true
        Mockito.clearInvocations(chunk)
        service.onChunkUnload(event)
        // Cancellation must return before querying the world or entities.
        Mockito.verifyNoInteractions(chunk)
    }

    @Test fun spawnReloadKeepsAliveTrackingAndDisableRemovesOwnedEntities() {
        val fixture = Fixture(directory)
        val core = Mockito.mock(RegionCoreServiceImpl::class.java)
        val service = SpawnService(fixture.plugin, core)
        service.repository.load()
        val rule = service.createRule("valley", "ZOMBIE", SpawnProvider.VANILLA)
        val entity = Mockito.mock(Entity::class.java)
        val uuid = UUID.randomUUID()
        val field = SpawnService::class.java.getDeclaredField("runtime").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val runtime = field.get(service) as MutableMap<String, SpawnRuntime>
        val state = SpawnRuntime(alive = linkedSetOf(uuid))
        runtime[rule.id] = state
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(fixture.scheduler)
            bukkit.`when`<Entity> { Bukkit.getEntity(uuid) }.thenReturn(entity)
            service.reload()
            assertSame(state, runtime[rule.id])
            assertEquals(setOf(uuid), runtime[rule.id]?.alive?.toSet())
            assertTrue(service.toggle(rule.id.uppercase()))
            Mockito.verify(entity).remove()
            assertFalse(runtime.containsKey(rule.id))
            service.close()
        }
    }

    @Test fun globalSpawnLimitStopsAnotherRuleBeforeSpawning() {
        val fixture = Fixture(directory)
        val service = SpawnService(fixture.plugin, Mockito.mock(RegionCoreServiceImpl::class.java))
        service.repository.load()
        val rule = service.createRule("valley", "ZOMBIE", SpawnProvider.VANILLA)
        val file = directory.resolve("modules/spawn/config.yml").toFile()
        YamlConfiguration.loadConfiguration(file).apply { set("defaults.limits.global-max-alive", 1); save(file) }
        service.repository.reload()
        val uuid = UUID.randomUUID()
        val field = SpawnService::class.java.getDeclaredField("runtime").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val runtime = field.get(service) as MutableMap<String, SpawnRuntime>
        runtime["other_rule"] = SpawnRuntime(alive = linkedSetOf(uuid))
        val player = player(Mockito.mock(World::class.java))
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Entity> { Bukkit.getEntity(uuid) }.thenReturn(Mockito.mock(Entity::class.java))
            service.test(player, rule.id)
            // If the global check is absent the next lookup returns REGION_NOT_FOUND.
            val messages = Mockito.mockingDetails(player).invocations.filter { it.method.name == "sendMessage" }.map { it.arguments[0].toString() }
            assertTrue(messages.any { "spawn-test-limit_reached" in it }, messages.toString())
            service.close()
        }
    }

    @Test fun movementEffectsWaitForActualLocationAndCoalesceWithinOneTick() {
        val fixture = Fixture(directory)
        val core = RegionCoreServiceImpl(fixture.plugin)
        core.save(region())
        core.load()
        val world = Mockito.mock(World::class.java)
        Mockito.`when`(world.uid).thenReturn(UUID.randomUUID())
        val player = player(world)
        val outside = Location(world, -20.0, 64.0, -20.0)
        val inside = Location(world, 1.0, 64.0, 1.0)
        Mockito.`when`(player.location).thenReturn(outside)
        val service = RegionEventServiceImpl(fixture.plugin, core, Mockito.mock(PlayerVariableService::class.java),
            Mockito.mock(ConditionEvaluator::class.java), { _, _, _, _ -> }, EditorToolService(fixture.plugin))
        val event = PlayerMoveEvent(player, outside, inside)
        event.isCancelled = true
        service.onMove(event)
        service.onMoveCommitted(event)
        assertTrue(fixture.scheduled.isEmpty())
        event.isCancelled = false
        service.onMove(event)
        Mockito.verify(fixture.manager, Mockito.never()).callEvent(Mockito.any(RegionEnterEvent::class.java))
        service.onMoveCommitted(event)
        service.onMoveCommitted(event)
        assertEquals(1, fixture.scheduled.size)
        // A plugin redirected/cancelled the move; the player actually stayed outside.
        fixture.scheduled.removeAt(0).run()
        Mockito.verify(fixture.manager, Mockito.never()).callEvent(Mockito.any(RegionEnterEvent::class.java))
        service.onMoveCommitted(event)
        Mockito.`when`(player.location).thenReturn(inside)
        fixture.scheduled.removeAt(0).run()
        Mockito.verify(fixture.manager, Mockito.times(1)).callEvent(Mockito.any(RegionEnterEvent::class.java))
        service.reset()
    }

    @Test fun disabledSpawnCleanupSurvivesUnloadedChunksAndPluginRestart() {
        val fixture = Fixture(directory)
        val core = Mockito.mock(RegionCoreServiceImpl::class.java)
        val service = SpawnService(fixture.plugin, core)
        service.repository.load()
        val rule = service.createRule("valley", "ZOMBIE", SpawnProvider.VANILLA)
        val uuid = UUID.randomUUID()
        val entity = Mockito.mock(Entity::class.java)
        Mockito.`when`(entity.uniqueId).thenReturn(uuid)
        val world = Mockito.mock(World::class.java)
        Mockito.`when`(world.uid).thenReturn(UUID.randomUUID())
        val chunk = Mockito.mock(Chunk::class.java)
        Mockito.`when`(chunk.world).thenReturn(world)
        Mockito.`when`(chunk.entities).thenReturn(arrayOf(entity))
        val field = SpawnService::class.java.getDeclaredField("runtime").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val runtime = field.get(service) as MutableMap<String, SpawnRuntime>
        runtime[rule.id] = SpawnRuntime(alive = linkedSetOf(uuid))
        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(fixture.scheduler)
            // Bukkit cannot resolve the entity while its chunk is unloaded.
            service.onChunkUnload(ChunkUnloadEvent(chunk))
            assertTrue(service.delete(rule.id))
            val cleanupFile = directory.resolve("modules/spawn/pending-removals.yml").toFile()
            assertEquals(listOf(uuid.toString()), YamlConfiguration.loadConfiguration(cleanupFile).getStringList("entities"))
            service.close()
            val restarted = SpawnService(fixture.plugin, core)
            restarted.start()
            restarted.onChunkLoad(ChunkLoadEvent(chunk, false))
            Mockito.verify(entity).remove()
            assertTrue(YamlConfiguration.loadConfiguration(cleanupFile).getStringList("entities").isEmpty())
            restarted.close()
        }
    }

    @Test fun pvpNoticeCacheIsReleasedOnQuit() {
        val fixture = Fixture(directory)
        val core = RegionCoreServiceImpl(fixture.plugin)
        core.save(region().copy(statuses = setOf(GlobalRegionStatus.PEACEFUL)))
        core.load()
        val world = Mockito.mock(World::class.java)
        val attacker = player(world)
        val victim = player(world)
        val service = ProtectService(fixture.plugin, core)
        val event = EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0)
        service.onEntityDamageByEntity(event)
        assertTrue(event.isCancelled)
        val cacheField = ProtectService::class.java.getDeclaredField("lastMessage").apply { isAccessible = true }
        val cache = cacheField.get(service) as Map<*, *>
        assertTrue(attacker.uniqueId in cache)
        service.onQuit(PlayerQuitEvent(attacker, ""))
        assertTrue(cache.isEmpty())
    }
}
