package com.worldscript.modules.l3.spawn

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Locale

class SpawnRuleRepository(private val plugin: JavaPlugin) {
    private val directory = File(plugin.dataFolder, "modules/spawn")
    private val file = File(directory, "config.yml")
    private var config: YamlConfiguration = YamlConfiguration()

    fun load() {
        if (!directory.exists()) directory.mkdirs()
        if (!file.isFile) {
            config = YamlConfiguration()
            writeDefaults(config)
            config.save(file)
        } else {
            config = YamlConfiguration.loadConfiguration(file)
            if (writeDefaults(config)) config.save(file)
        }
    }

    fun reload() = load()

    fun save() {
        if (!directory.exists()) directory.mkdirs()
        config.save(file)
    }

    fun enabled(): Boolean = config.getBoolean("enabled", true)

    fun tickInterval(): Long = config.getLong("tick-interval", 20).coerceIn(1, 20 * 60)

    fun defaults(): SpawnDefaults {
        return SpawnDefaults(
            amount = range("defaults.amount", 1, 3, 1, hardAmountMax()),
            intervalSeconds = range("defaults.interval-seconds", 30, 60, 1, 3600),
            hardAmountMax = hardAmountMax(),
            players = SpawnPlayerSettings(
                requireInsideRegion = config.getBoolean("defaults.players.require-inside-region", true),
                requireNearby = config.getBoolean("defaults.players.require-nearby", true),
                nearbyRadius = config.getDouble("defaults.players.nearby-radius", 48.0).coerceIn(1.0, 512.0),
                minDistance = config.getDouble("defaults.players.min-distance", 8.0).coerceIn(0.0, 128.0),
            ),
            spawn = SpawnLocationSettings(
                attempts = config.getInt("defaults.spawn.attempts", 12).coerceIn(1, 128),
            ),
            safety = SpawnSafetySettings(
                enabled = config.getBoolean("defaults.safety.enabled", true),
                groundRequired = config.getBoolean("defaults.safety.ground-required", true),
                avoidLiquid = config.getBoolean("defaults.safety.avoid-liquid", true),
                avoidSolidBody = config.getBoolean("defaults.safety.avoid-solid-body", true),
            ),
            cleanup = SpawnCleanupSettings(
                despawnWhenEmpty = config.getBoolean("defaults.cleanup.despawn-when-empty", true),
                delaySeconds = config.getLong("defaults.cleanup.delay-seconds", 60).coerceIn(0, 3600),
            ),
            limits = SpawnLimitSettings(
                maxAlive = config.getInt("defaults.limits.max-alive", 30).coerceIn(1, 1000),
            ),
            mythicCommand = config.getString("providers.mythicmobs.command", "mm mobs spawn %mob% %amount% %world%,%x%,%y%,%z%")
                ?: "mm mobs spawn %mob% %amount% %world%,%x%,%y%,%z%",
        )
    }

    fun all(): List<SpawnRule> {
        val rules = config.getConfigurationSection("rules") ?: return emptyList()
        return rules.getKeys(false).mapNotNull { id -> readRule(id, rules.getConfigurationSection(id) ?: return@mapNotNull null) }
            .sortedBy { it.id.lowercase(Locale.ROOT) }
    }

    fun byRegion(regionId: String): List<SpawnRule> = all().filter { it.regionId.equals(regionId, true) }

    fun find(id: String): SpawnRule? = all().firstOrNull { it.id.equals(id, true) }

    fun createForRegion(regionId: String, mobId: String, provider: SpawnProvider = SpawnProvider.AUTO, requestedId: String? = null): SpawnRule {
        val defaults = defaults()
        val ruleId = uniqueRuleId(requestedId?.takeIf { it.isNotBlank() } ?: mobId)
        writeRule(
            SpawnRule(
                id = ruleId,
                enabled = true,
                regionId = regionId,
                provider = provider,
                mobId = mobId,
                amount = defaults.amount,
                intervalSeconds = defaults.intervalSeconds,
                players = defaults.players,
                spawn = defaults.spawn,
                safety = defaults.safety,
                cleanup = defaults.cleanup,
                limits = defaults.limits,
            ),
        )
        save()
        return find(ruleId) ?: error("created spawn rule disappeared: $ruleId")
    }

    fun update(id: String, update: (SpawnRule) -> SpawnRule): Boolean {
        val current = find(id) ?: return false
        writeRule(update(current))
        save()
        return true
    }

    fun delete(id: String): Boolean {
        val key = find(id)?.id ?: return false
        config.set("rules.$key", null)
        save()
        return true
    }

    private fun readRule(id: String, section: ConfigurationSection): SpawnRule {
        val defaults = defaults()
        val hardMax = defaults.hardAmountMax
        return SpawnRule(
            id = id,
            enabled = section.getBoolean("enabled", true),
            regionId = section.getString("region", "") ?: "",
            provider = parseProvider(section.getString("provider", "auto")),
            mobId = section.getString("mob", "") ?: "",
            amount = range(section, "amount", defaults.amount).normalized(1, hardMax),
            intervalSeconds = range(section, "interval-seconds", defaults.intervalSeconds).normalized(1, 3600),
            players = SpawnPlayerSettings(
                requireInsideRegion = section.getBoolean("players.require-inside-region", defaults.players.requireInsideRegion),
                requireNearby = section.getBoolean("players.require-nearby", defaults.players.requireNearby),
                nearbyRadius = section.getDouble("players.nearby-radius", defaults.players.nearbyRadius).coerceIn(1.0, 512.0),
                minDistance = section.getDouble("players.min-distance", defaults.players.minDistance).coerceIn(0.0, 128.0),
            ),
            spawn = SpawnLocationSettings(
                attempts = section.getInt("spawn.attempts", defaults.spawn.attempts).coerceIn(1, 128),
            ),
            safety = SpawnSafetySettings(
                enabled = section.getBoolean("safety.enabled", defaults.safety.enabled),
                groundRequired = section.getBoolean("safety.ground-required", defaults.safety.groundRequired),
                avoidLiquid = section.getBoolean("safety.avoid-liquid", defaults.safety.avoidLiquid),
                avoidSolidBody = section.getBoolean("safety.avoid-solid-body", defaults.safety.avoidSolidBody),
            ),
            cleanup = SpawnCleanupSettings(
                despawnWhenEmpty = section.getBoolean("cleanup.despawn-when-empty", defaults.cleanup.despawnWhenEmpty),
                delaySeconds = section.getLong("cleanup.delay-seconds", defaults.cleanup.delaySeconds).coerceIn(0, 3600),
            ),
            limits = SpawnLimitSettings(
                maxAlive = section.getInt("limits.max-alive", defaults.limits.maxAlive).coerceIn(1, 1000),
            ),
        )
    }

    private fun writeRule(rule: SpawnRule) {
        val path = "rules.${rule.id}"
        config.set("$path.enabled", rule.enabled)
        config.set("$path.region", rule.regionId)
        config.set("$path.provider", rule.provider.name.lowercase(Locale.ROOT))
        config.set("$path.mob", rule.mobId)
        config.set("$path.amount.min", rule.amount.min)
        config.set("$path.amount.max", rule.amount.max)
        config.set("$path.interval-seconds.min", rule.intervalSeconds.min)
        config.set("$path.interval-seconds.max", rule.intervalSeconds.max)
        config.set("$path.players.require-inside-region", rule.players.requireInsideRegion)
        config.set("$path.players.require-nearby", rule.players.requireNearby)
        config.set("$path.players.nearby-radius", rule.players.nearbyRadius)
        config.set("$path.players.min-distance", rule.players.minDistance)
        config.set("$path.spawn.mode", "random-in-region")
        config.set("$path.spawn.attempts", rule.spawn.attempts)
        config.set("$path.safety.enabled", rule.safety.enabled)
        config.set("$path.safety.ground-required", rule.safety.groundRequired)
        config.set("$path.safety.avoid-liquid", rule.safety.avoidLiquid)
        config.set("$path.safety.avoid-solid-body", rule.safety.avoidSolidBody)
        config.set("$path.cleanup.despawn-when-empty", rule.cleanup.despawnWhenEmpty)
        config.set("$path.cleanup.delay-seconds", rule.cleanup.delaySeconds)
        config.set("$path.limits.max-alive", rule.limits.maxAlive)
    }

    private fun uniqueRuleId(seed: String): String {
        val base = seed.replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .lowercase(Locale.ROOT)
            .ifBlank { "spawn_rule" }
        val existing = all().map { it.id.lowercase(Locale.ROOT) }.toSet()
        if (base !in existing) return base
        for (index in 2..999) {
            val candidate = "${base}_$index"
            if (candidate !in existing) return candidate
        }
        return "${base}_${System.currentTimeMillis()}"
    }

    private fun range(root: String, fallbackMin: Int, fallbackMax: Int, floor: Int, ceiling: Int): IntRangeValue =
        IntRangeValue(config.getInt("$root.min", fallbackMin), config.getInt("$root.max", fallbackMax)).normalized(floor, ceiling)

    private fun range(section: ConfigurationSection, path: String, fallback: IntRangeValue): IntRangeValue =
        IntRangeValue(section.getInt("$path.min", fallback.min), section.getInt("$path.max", fallback.max))

    private fun hardAmountMax(): Int = config.getInt("defaults.amount.hard-max", 10).coerceIn(1, 128)

    private fun parseProvider(value: String?): SpawnProvider =
        when (value?.trim()?.lowercase(Locale.ROOT)) {
            "vanilla" -> SpawnProvider.VANILLA
            "mythic", "mythicmobs", "mm" -> SpawnProvider.MYTHICMOBS
            else -> SpawnProvider.AUTO
        }

    private fun writeDefaults(target: YamlConfiguration): Boolean {
        var changed = false
        fun set(path: String, value: Any) {
            if (!target.contains(path)) {
                target.set(path, value)
                changed = true
            }
        }
        set("enabled", true)
        set("tick-interval", 20)
        set("defaults.amount.min", 1)
        set("defaults.amount.max", 3)
        set("defaults.amount.hard-max", 10)
        set("defaults.interval-seconds.min", 30)
        set("defaults.interval-seconds.max", 60)
        set("defaults.players.require-inside-region", true)
        set("defaults.players.require-nearby", true)
        set("defaults.players.nearby-radius", 48)
        set("defaults.players.min-distance", 8)
        set("defaults.spawn.mode", "random-in-region")
        set("defaults.spawn.attempts", 12)
        set("defaults.safety.enabled", true)
        set("defaults.safety.ground-required", true)
        set("defaults.safety.avoid-liquid", true)
        set("defaults.safety.avoid-solid-body", true)
        set("defaults.cleanup.despawn-when-empty", true)
        set("defaults.cleanup.delay-seconds", 60)
        set("defaults.limits.max-alive", 30)
        set("defaults.limits.global-max-alive", 300)
        set("providers.mythicmobs.command", "mm mobs spawn %mob% %amount% %world%,%x%,%y%,%z%")
        if (!target.isConfigurationSection("rules")) {
            target.createSection("rules")
            changed = true
        }
        return changed
    }
}
