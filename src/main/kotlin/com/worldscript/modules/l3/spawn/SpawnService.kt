package com.worldscript.modules.l3.spawn

import com.worldscript.foundation.Lang
import com.worldscript.foundation.model.BlockPosition
import com.worldscript.foundation.model.RegionDefinition
import com.worldscript.modules.l1.region_core.RegionCoreServiceImpl
import com.worldscript.modules.l1.region_core.RegionGeometry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class SpawnService(
    private val plugin: JavaPlugin,
    private val regions: RegionCoreServiceImpl,
) : Listener {
    val repository = SpawnRuleRepository(plugin)
    val mythicMobs = MythicMobLibrary(plugin)
    private val lang = Lang(plugin)
    private val runtime = linkedMapOf<String, SpawnRuntime>()
    private var task: BukkitTask? = null

    fun start() {
        repository.load()
        schedule()
    }

    fun reload() {
        repository.reload()
        runtime.clear()
        schedule()
    }

    private fun schedule() {
        task?.cancel()
        val interval = repository.tickInterval()
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, interval, interval)
    }

    fun close() {
        task?.cancel()
        task = null
        runtime.clear()
    }

    fun rules(): List<SpawnRule> = repository.all()

    fun rules(regionId: String): List<SpawnRule> = repository.byRegion(regionId)

    fun createRule(regionId: String, mobId: String, provider: SpawnProvider): SpawnRule =
        repository.createForRegion(regionId, mobId, provider).also { runtime.remove(it.id) }

    fun createRule(regionId: String, ruleId: String, mobId: String, provider: SpawnProvider): SpawnRule =
        repository.createForRegion(regionId, mobId, provider, ruleId).also { runtime.remove(it.id) }

    fun toggle(ruleId: String): Boolean =
        repository.update(ruleId) { it.copy(enabled = !it.enabled) }.also { runtime.remove(ruleId) }

    fun delete(ruleId: String): Boolean =
        repository.delete(ruleId).also { runtime.remove(ruleId) }

    fun updateAmount(ruleId: String, range: IntRangeValue): Boolean {
        val hardMax = repository.defaults().hardAmountMax
        return repository.update(ruleId) { it.copy(amount = range.normalized(1, hardMax)) }
    }

    fun updateInterval(ruleId: String, range: IntRangeValue): Boolean =
        repository.update(ruleId) { it.copy(intervalSeconds = range.normalized(1, 3600)) }

    fun updateMob(ruleId: String, mobId: String, provider: SpawnProvider): Boolean =
        repository.update(ruleId) { it.copy(mobId = mobId, provider = provider) }

    fun updateMaxAlive(ruleId: String, value: Int): Boolean =
        repository.update(ruleId) { it.copy(limits = it.limits.copy(maxAlive = value.coerceIn(1, 1000))) }

    fun updateNearbyRadius(ruleId: String, value: Double): Boolean =
        repository.update(ruleId) { it.copy(players = it.players.copy(nearbyRadius = value.coerceIn(1.0, 512.0))) }

    fun updateMinDistance(ruleId: String, value: Double): Boolean =
        repository.update(ruleId) { it.copy(players = it.players.copy(minDistance = value.coerceIn(0.0, 128.0))) }

    fun updateCleanupDelay(ruleId: String, value: Long): Boolean =
        repository.update(ruleId) { it.copy(cleanup = it.cleanup.copy(delaySeconds = value.coerceIn(0, 3600))) }

    fun toggleInside(ruleId: String): Boolean =
        repository.update(ruleId) { it.copy(players = it.players.copy(requireInsideRegion = !it.players.requireInsideRegion)) }

    fun toggleNearby(ruleId: String): Boolean =
        repository.update(ruleId) { it.copy(players = it.players.copy(requireNearby = !it.players.requireNearby)) }

    fun toggleSafety(ruleId: String): Boolean =
        repository.update(ruleId) { it.copy(safety = it.safety.copy(enabled = !it.safety.enabled)) }

    fun toggleCleanup(ruleId: String): Boolean =
        repository.update(ruleId) { it.copy(cleanup = it.cleanup.copy(despawnWhenEmpty = !it.cleanup.despawnWhenEmpty)) }

    fun test(sender: CommandSender, ruleId: String) {
        val player = sender as? Player
        val result = spawnNow(ruleId, forcedPlayer = player, ignoreSchedule = true)
        lang.send(sender, "spawn-test-${result.name.lowercase(Locale.ROOT)}", "rule" to ruleId)
    }

    private fun tick() {
        if (!repository.enabled()) return
        repository.all().filter { it.enabled }.forEach { rule ->
            val state = runtime.getOrPut(rule.id) { SpawnRuntime(nextSpawnAtMillis = nextTime(rule)) }
            cleanup(rule, state)
            if (System.currentTimeMillis() < state.nextSpawnAtMillis) return@forEach
            val result = spawn(rule, state, forcedPlayer = null)
            state.nextSpawnAtMillis = nextTime(rule)
            if (result != SpawnResult.SUCCESS) rememberFailure(rule, state, result.name.lowercase(Locale.ROOT))
        }
    }

    private fun spawnNow(ruleId: String, forcedPlayer: Player?, ignoreSchedule: Boolean): SpawnResult {
        val rule = repository.find(ruleId) ?: return SpawnResult.RULE_NOT_FOUND
        if (!rule.enabled && !ignoreSchedule) return SpawnResult.RULE_DISABLED
        val state = runtime.getOrPut(rule.id) { SpawnRuntime() }
        cleanup(rule, state)
        return spawn(rule, state, forcedPlayer)
    }

    private fun spawn(rule: SpawnRule, state: SpawnRuntime, forcedPlayer: Player?): SpawnResult {
        state.alive.removeIf { uuid -> Bukkit.getEntity(uuid) == null || Bukkit.getEntity(uuid)?.isDead == true }
        if (state.alive.size >= rule.limits.maxAlive) return SpawnResult.LIMIT_REACHED
        val region = regions.effective(rule.regionId) ?: return SpawnResult.REGION_NOT_FOUND
        val world = Bukkit.getWorld(region.worldId) ?: Bukkit.getWorld(region.worldName) ?: return SpawnResult.WORLD_NOT_FOUND
        val players = world.players.filter { player -> shouldConsiderPlayer(player, region, rule) }
        if (forcedPlayer == null && players.isEmpty()) return SpawnResult.NO_PLAYER
        val amount = random(rule.amount.min, rule.amount.max).coerceAtMost(rule.limits.maxAlive - state.alive.size)
        if (amount <= 0) return SpawnResult.LIMIT_REACHED
        val spawned = mutableListOf<UUID>()
        repeat(amount) {
            val location = findSpawnLocation(world, region, rule, forcedPlayer ?: players.randomOrNull())
            if (location != null) {
                when (val result = spawnEntity(rule, location)) {
                    is SpawnEntityResult.Spawned -> spawned += result.entity.uniqueId
                    SpawnEntityResult.InvalidMob -> return SpawnResult.INVALID_MOB
                    SpawnEntityResult.ProviderUnavailable -> return SpawnResult.PROVIDER_UNAVAILABLE
                }
            }
        }
        if (spawned.isEmpty()) return if (providerAvailable(rule)) SpawnResult.NO_SAFE_LOCATION else SpawnResult.PROVIDER_UNAVAILABLE
        state.alive += spawned
        state.emptySinceMillis = 0
        return SpawnResult.SUCCESS
    }

    private fun spawnEntity(rule: SpawnRule, location: Location): SpawnEntityResult {
        val provider = resolveProvider(rule)
        if (provider == SpawnProvider.MYTHICMOBS) {
            if (!mythicMobs.available()) return SpawnEntityResult.ProviderUnavailable
            if (!mythicMobs.contains(rule.mobId)) return SpawnEntityResult.InvalidMob
            spawnMythicByApi(rule, location)?.let { return SpawnEntityResult.Spawned(it) }
            val before = nearbyLivingIds(location, 3.0)
            val command = repository.defaults().mythicCommand
                .replace("%mob%", rule.mobId)
                .replace("%amount%", "1")
                .replace("%world%", location.world?.name.orEmpty())
                .replace("%x%", "%.2f".format(Locale.US, location.x))
                .replace("%y%", "%.2f".format(Locale.US, location.y))
                .replace("%z%", "%.2f".format(Locale.US, location.z))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            return nearestNewLiving(location, 3.0, before)?.let { SpawnEntityResult.Spawned(it) }
                ?: SpawnEntityResult.ProviderUnavailable
        }
        val type = runCatching { EntityType.valueOf(rule.mobId.uppercase(Locale.ROOT)) }.getOrNull()
            ?: return SpawnEntityResult.InvalidMob
        if (!type.isAlive || !type.isSpawnable) return SpawnEntityResult.InvalidMob
        return location.world?.spawnEntity(location, type)?.let { SpawnEntityResult.Spawned(it) }
            ?: SpawnEntityResult.ProviderUnavailable
    }

    private fun spawnMythicByApi(rule: SpawnRule, location: Location): Entity? = runCatching {
        val mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
        val instance = mythicBukkit.getMethod("inst").invoke(null)
        val mobManager = instance.javaClass.methods.firstOrNull { it.name == "getMobManager" && it.parameterTypes.isEmpty() }
            ?.invoke(instance) ?: return null
        val optional = mobManager.javaClass.methods.firstOrNull { it.name == "getMythicMob" && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
            ?.invoke(mobManager, rule.mobId) ?: return null
        val mythicMob = optional.javaClass.methods.firstOrNull { it.name == "orElse" && it.parameterTypes.size == 1 }
            ?.invoke(optional, *arrayOfNulls<Any>(1)) ?: return null
        val adapter = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter")
        val abstractLocation = adapter.methods.firstOrNull { it.name == "adapt" && it.parameterTypes.contentEquals(arrayOf(Location::class.java)) }
            ?.invoke(null, location) ?: return null
        val activeMob = mythicMob.javaClass.methods.firstOrNull { it.name == "spawn" && it.parameterTypes.size == 2 && it.parameterTypes[1] == java.lang.Double.TYPE }
            ?.invoke(mythicMob, abstractLocation, 1.0) ?: return null
        val uuid = activeMob.javaClass.methods.firstOrNull { it.name == "getUniqueId" && it.parameterTypes.isEmpty() }
            ?.invoke(activeMob) as? UUID ?: return null
        Bukkit.getEntity(uuid)
    }.onFailure {
        plugin.logger.fine("Could not spawn MythicMobs mob ${rule.mobId} through API: ${it.message}")
    }.getOrNull()

    private fun nearbyLivingIds(location: Location, radius: Double): Set<UUID> =
        location.world?.entities
            ?.asSequence()
            ?.filterIsInstance<LivingEntity>()
            ?.filter { it !is Player }
            ?.filter { !it.isDead && it.location.world == location.world && it.location.distanceSquared(location) <= radius * radius }
            ?.map { it.uniqueId }
            ?.toSet()
            .orEmpty()

    private fun nearestNewLiving(location: Location, radius: Double, before: Set<UUID>): LivingEntity? =
        location.world?.entities
            ?.asSequence()
            ?.filterIsInstance<LivingEntity>()
            ?.filter { it !is Player && it.uniqueId !in before }
            ?.filter { !it.isDead && it.location.world == location.world && it.location.distanceSquared(location) <= radius * radius }
            ?.minByOrNull { it.location.distanceSquared(location) }

    private fun findSpawnLocation(world: World, region: RegionDefinition, rule: SpawnRule, anchor: Player?): Location? {
        val min = region.bounds.min
        val max = region.bounds.max
        repeat(rule.spawn.attempts) {
            val x = random(min.x, max.x)
            val z = random(min.z, max.z)
            val y = world.getHighestBlockYAt(x, z) + 1
            val candidate = Location(world, x + 0.5, y.toDouble(), z + 0.5)
            if (y < min.y || y > max.y + 1) return@repeat
            if (!RegionGeometry.contains(region, BlockPosition(x, y.coerceIn(min.y, max.y), z))) return@repeat
            if (anchor != null && !passesDistance(candidate, anchor, rule)) return@repeat
            if (!safe(candidate, rule)) return@repeat
            return candidate
        }
        return null
    }

    private fun shouldConsiderPlayer(player: Player, region: RegionDefinition, rule: SpawnRule): Boolean {
        if (rule.players.requireInsideRegion && !RegionGeometry.contains(region, player.location.toBlockPosition())) return false
        return true
    }

    private fun passesDistance(candidate: Location, player: Player, rule: SpawnRule): Boolean {
        if (candidate.world != player.world) return false
        val distance = candidate.distance(player.location)
        if (rule.players.requireNearby && distance > rule.players.nearbyRadius) return false
        if (distance < rule.players.minDistance) return false
        return true
    }

    private fun safe(location: Location, rule: SpawnRule): Boolean {
        if (!rule.safety.enabled) return true
        val feet = location.block
        val head = feet.getRelative(0, 1, 0)
        val ground = feet.getRelative(0, -1, 0)
        if (rule.safety.avoidSolidBody && (feet.type.isSolid || head.type.isSolid)) return false
        if (rule.safety.groundRequired && !ground.type.isSolid) return false
        if (rule.safety.avoidLiquid && (feet.isLiquid || head.isLiquid || ground.isLiquid)) return false
        if (ground.type == Material.AIR) return false
        return true
    }

    private fun cleanup(rule: SpawnRule, state: SpawnRuntime) {
        state.alive.removeIf { uuid -> Bukkit.getEntity(uuid) == null || Bukkit.getEntity(uuid)?.isDead == true }
        if (!rule.cleanup.despawnWhenEmpty) return
        val region = regions.effective(rule.regionId) ?: return
        val world = Bukkit.getWorld(region.worldId) ?: Bukkit.getWorld(region.worldName) ?: return
        val hasPlayer = world.players.any { RegionGeometry.contains(region, it.location.toBlockPosition()) }
        if (hasPlayer) {
            state.emptySinceMillis = 0
            return
        }
        if (state.emptySinceMillis == 0L) {
            state.emptySinceMillis = System.currentTimeMillis()
            return
        }
        if (System.currentTimeMillis() - state.emptySinceMillis < rule.cleanup.delaySeconds * 1000) return
        state.alive.toList().forEach { uuid -> Bukkit.getEntity(uuid)?.remove() }
        state.alive.clear()
    }

    private fun resolveProvider(rule: SpawnRule): SpawnProvider {
        if (rule.provider != SpawnProvider.AUTO) return rule.provider
        if (mythicMobs.available() && mythicMobs.contains(rule.mobId)) return SpawnProvider.MYTHICMOBS
        return SpawnProvider.VANILLA
    }

    private fun providerAvailable(rule: SpawnRule): Boolean {
        val provider = resolveProvider(rule)
        return provider == SpawnProvider.VANILLA || mythicMobs.available()
    }

    private fun nextTime(rule: SpawnRule): Long = System.currentTimeMillis() + random(rule.intervalSeconds.min, rule.intervalSeconds.max) * 1000L

    private fun rememberFailure(rule: SpawnRule, state: SpawnRuntime, reason: String) {
        state.lastFailureReason = reason
        val now = System.currentTimeMillis()
        if (now - state.lastFailureAtMillis < 60_000) return
        state.lastFailureAtMillis = now
        plugin.logger.fine("WorldScript spawn rule ${rule.id} skipped: $reason")
    }

    private fun random(min: Int, max: Int): Int = ThreadLocalRandom.current().nextInt(min, max + 1)

    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        runtime.values.forEach { it.alive.remove(event.entity.uniqueId) }
    }

    private fun Location.toBlockPosition() = BlockPosition(blockX, blockY, blockZ)

    private sealed class SpawnEntityResult {
        data class Spawned(val entity: Entity) : SpawnEntityResult()
        object ProviderUnavailable : SpawnEntityResult()
        object InvalidMob : SpawnEntityResult()
    }
}
