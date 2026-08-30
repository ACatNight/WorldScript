package com.worldscript.foundation.module

import com.worldscript.foundation.SettingsLayout
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class WorldScriptModuleManager(private val plugin: JavaPlugin) {
    private val modulesDirectory = File(plugin.dataFolder, "modules")
    private val disabledDirectory = File(modulesDirectory, "disabled")
    private val reports = mutableListOf<ModuleReport>()
    private val services = ServiceRegistry()
    private val loadedModules = mutableListOf<LoadedModule>()

    fun initialize() {
        if (!modulesDirectory.exists()) modulesDirectory.mkdirs()
        if (!disabledDirectory.exists()) disabledDirectory.mkdirs()
        if (plugin.config.getBoolean("modules.auto-install-official", true)) {
            installOfficialModules()
        }
        reload()
    }

    fun reload() {
        unloadExternalModules()
        reports.clear()
        val descriptors = scanDescriptors()
        val ordered = order(descriptors)
        ordered.forEach { candidate -> report(candidate) }
    }

    fun close() {
        unloadExternalModules()
        reports.clear()
    }

    fun all(): List<ModuleReport> = reports.toList()

    fun find(id: String): ModuleReport? = reports.firstOrNull { it.descriptor.id.equals(id, true) }

    fun disable(id: String): ModuleToggleResult {
        val normalized = id.trim().lowercase()
        val descriptor = find(normalized)?.descriptor ?: officialById[normalized] ?: return ModuleToggleResult.NOT_FOUND
        if (descriptor.required) return ModuleToggleResult.REQUIRED
        if (descriptor.builtin) return ModuleToggleResult.BUILTIN
        val disabled = disabledIds().toMutableSet()
        if (!disabled.add(normalized)) return ModuleToggleResult.UNCHANGED
        plugin.config.set("modules.disabled", disabled.sorted())
        SettingsLayout.save(plugin, "modules")
        reload()
        return ModuleToggleResult.CHANGED
    }

    fun enable(id: String): ModuleToggleResult {
        val normalized = id.trim().lowercase()
        val disabled = disabledIds().toMutableSet()
        if (normalized !in disabled && find(normalized) == null && officialById[normalized] == null) return ModuleToggleResult.NOT_FOUND
        if (!disabled.remove(normalized)) return ModuleToggleResult.UNCHANGED
        plugin.config.set("modules.disabled", disabled.sorted())
        SettingsLayout.save(plugin, "modules")
        reload()
        return ModuleToggleResult.CHANGED
    }

    private fun installOfficialModules() {
        officialModules.forEach { descriptor ->
            val file = File(modulesDirectory, "worldscript-${descriptor.id}.jar")
            if (!shouldWriteOfficialModule(file, descriptor)) return@forEach
            runCatching {
                JarOutputStream(file.outputStream()).use { output ->
                    output.putNextEntry(JarEntry("module.yml"))
                    output.write(descriptor.toYaml().toByteArray(Charsets.UTF_8))
                    output.closeEntry()
                }
            }.onFailure { error ->
                plugin.logger.warning("Could not install official module ${descriptor.id}: ${error.message}")
            }
        }
    }

    private fun shouldWriteOfficialModule(file: File, official: ModuleDescriptor): Boolean {
        if (!file.isFile) return true
        val existing = readDescriptor(file) ?: return true
        if (existing.id != official.id) return false
        return (existing.official || existing.builtin || existing.required) && !matchesOfficialCatalog(existing, official)
    }

    private fun scanDescriptors(): List<ModuleCandidate> {
        val result = mutableListOf<ModuleCandidate>()
        scanDirectory(modulesDirectory, false, result)
        scanDirectory(disabledDirectory, true, result)
        officialModules.filter { it.required && result.none { candidate -> candidate.descriptor.id == it.id } }
            .forEach { result += ModuleCandidate(it, "builtin:${it.id}", false, null) }
        return result
    }

    private fun scanDirectory(directory: File, disabledByDirectory: Boolean, result: MutableList<ModuleCandidate>) {
        directory.listFiles { file -> file.isFile && file.extension.equals("jar", true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                val descriptor = readDescriptor(file)
                if (descriptor == null) {
                    reports += failedPlaceholder(file.name, "modules-reason-invalid-descriptor")
                } else {
                    val official = officialById[descriptor.id]
                    if (official == null) {
                        if (descriptor.official || descriptor.builtin || descriptor.required) {
                            reports += ModuleReport(descriptor, file.name, ModuleState.FAILED, "modules-reason-protected-flag")
                        } else {
                            result += ModuleCandidate(descriptor, file.name, disabledByDirectory, file)
                        }
                    } else if (!matchesOfficialCatalog(descriptor, official)) {
                        reports += ModuleReport(descriptor, file.name, ModuleState.FAILED, "modules-reason-catalog-mismatch")
                    } else {
                        result += ModuleCandidate(official, file.name, disabledByDirectory, file)
                    }
                }
            }
    }

    private fun readDescriptor(file: File): ModuleDescriptor? {
        return runCatching {
            JarFile(file).use { jar ->
                val entry = jar.getJarEntry("module.yml") ?: return null
                jar.getInputStream(entry).use { input ->
                    ModuleDescriptor.parse(YamlConfiguration.loadConfiguration(input.reader(Charsets.UTF_8)), file.name).getOrNull()
                }
            }
        }.getOrNull()
    }

    private fun order(candidates: List<ModuleCandidate>): List<ModuleCandidate> {
        val unique = linkedMapOf<String, ModuleCandidate>()
        candidates.forEach { candidate ->
            val previous = unique.putIfAbsent(candidate.descriptor.id, candidate)
            if (previous != null) {
                reports += ModuleReport(candidate.descriptor, candidate.source, ModuleState.FAILED, "modules-reason-duplicate")
            }
        }
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val ordered = mutableListOf<ModuleCandidate>()
        fun visit(candidate: ModuleCandidate) {
            val id = candidate.descriptor.id
            if (id in visited) return
            if (!visiting.add(id)) {
                reports += ModuleReport(candidate.descriptor, candidate.source, ModuleState.FAILED, "modules-reason-circular-dependency")
                return
            }
            candidate.descriptor.dependencies.forEach { dependency ->
                unique[dependency]?.let(::visit) ?: reports.add(ModuleReport(candidate.descriptor, candidate.source, ModuleState.FAILED, "modules-reason-missing-dependency", dependency))
            }
            visiting.remove(id)
            visited += id
            ordered += candidate
        }
        unique.values.forEach(::visit)
        return ordered
    }

    private fun report(candidate: ModuleCandidate) {
        val descriptor = candidate.descriptor
        val disabled = candidate.disabledByDirectory || descriptor.id in disabledIds()
        if (disabled && !descriptor.required) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.DISABLED, "modules-reason-disabled")
            return
        }
        if (!supportsApi(descriptor.apiVersion)) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-api-unsupported", descriptor.apiVersion.toString())
            return
        }
        if (!supportsWorldScriptVersion(descriptor.worldScriptVersion)) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-version-unsupported", descriptor.worldScriptVersion)
            return
        }
        if (reports.any { it.descriptor.id == descriptor.id && it.state == ModuleState.FAILED }) return
        if (descriptor.builtin) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.BUILTIN, "modules-reason-builtin")
            return
        }
        if (!plugin.config.getBoolean("modules.load-external", false)) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.DISABLED, "modules-reason-external-disabled")
            return
        }
        loadExternal(candidate)
    }

    private fun loadExternal(candidate: ModuleCandidate) {
        val descriptor = candidate.descriptor
        val file = candidate.file ?: run {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-invalid-descriptor")
            return
        }
        if (descriptor.main.isBlank()) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-main-missing")
            return
        }
        val classLoader = URLClassLoader(arrayOf(file.toURI().toURL()), plugin.javaClass.classLoader)
        val context = ModuleContext(plugin, descriptor.id, services)
        val module = runCatching {
            val type = Class.forName(descriptor.main, true, classLoader).asSubclass(WorldScriptModule::class.java)
            val constructor = type.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrElse { error ->
            classLoader.close()
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-load-failed", error.message.orEmpty().ifBlank { error.javaClass.simpleName })
            return
        }
        if (!module.id.equals(descriptor.id, ignoreCase = true)) {
            runCatching { module.onDisable() }
            context.unregisterListeners()
            classLoader.close()
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-id-mismatch", module.id)
            return
        }
        runCatching {
            module.onLoad(context)
            module.onEnable()
        }.onSuccess {
            loadedModules += LoadedModule(descriptor, module, context, classLoader)
            reports += ModuleReport(descriptor, candidate.source, ModuleState.ENABLED, "modules-reason-loaded")
        }.onFailure { error ->
            runCatching { module.onDisable() }
            context.unregisterListeners()
            classLoader.close()
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "modules-reason-load-failed", error.message.orEmpty().ifBlank { error.javaClass.simpleName })
        }
    }

    private fun unloadExternalModules() {
        loadedModules.asReversed().forEach { loaded ->
            runCatching { loaded.module.onDisable() }.onFailure { error ->
                plugin.logger.warning("Could not disable module ${loaded.descriptor.id}: ${error.message}")
            }
            loaded.context.unregisterListeners()
            runCatching { loaded.classLoader.close() }.onFailure { error ->
                plugin.logger.warning("Could not close module ${loaded.descriptor.id}: ${error.message}")
            }
        }
        loadedModules.clear()
    }

    private fun disabledIds(): Set<String> =
        plugin.config.getStringList("modules.disabled").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

    private fun supportsApi(apiVersion: Int): Boolean = apiVersion == MODULE_API_VERSION

    private fun supportsWorldScriptVersion(requirement: String): Boolean {
        val current = plugin.description.version
        val trimmed = requirement.trim()
        if (trimmed.isBlank()) return true
        return when {
            trimmed.startsWith(">=") -> compareVersions(current, trimmed.removePrefix(">=").trim()) >= 0
            trimmed.startsWith("=") -> compareVersions(current, trimmed.removePrefix("=").trim()) == 0
            else -> compareVersions(current, trimmed) == 0
        }
    }

    private fun compareVersions(current: String, required: String): Int {
        val left = versionParts(current)
        val right = versionParts(required)
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = (left.getOrNull(index) ?: 0) - (right.getOrNull(index) ?: 0)
            if (difference != 0) return difference
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    private fun failedPlaceholder(source: String, reason: String): ModuleReport {
        return ModuleReport(
            ModuleDescriptor(source.substringBeforeLast('.').lowercase(), source, "0.0.0", MODULE_API_VERSION, ">=0.1.0", "", emptyList(), emptyList(), false, false, false),
            source,
            ModuleState.FAILED,
            reason,
        )
    }

    private data class ModuleCandidate(
        val descriptor: ModuleDescriptor,
        val source: String,
        val disabledByDirectory: Boolean,
        val file: File?,
    )

    private data class LoadedModule(
        val descriptor: ModuleDescriptor,
        val module: WorldScriptModule,
        val context: ModuleContext,
        val classLoader: URLClassLoader,
    )

    companion object {
        const val MODULE_API_VERSION = 1
        private const val OFFICIAL_MODULE_VERSION = "1.0.0"
        private const val OFFICIAL_WORLD_SCRIPT_REQUIREMENT = ">=1.0.0"

        val officialModules = listOf(
            ModuleDescriptor("core", "WorldScript Core", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", emptyList(), emptyList(), true, true, true),
            ModuleDescriptor("rpg", "WorldScript RPG", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core"), emptyList(), true, true, false),
            ModuleDescriptor("toast", "WorldScript Toast", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core"), emptyList(), true, true, false),
            ModuleDescriptor("atmosphere", "WorldScript Atmosphere", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core", "rpg"), emptyList(), true, true, false),
            ModuleDescriptor("spawn", "WorldScript Spawn", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core"), listOf("MythicMobs"), true, true, false),
            ModuleDescriptor("protect", "WorldScript Protect", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core"), emptyList(), true, true, false),
            ModuleDescriptor("editor", "WorldScript Editor", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core", "rpg", "toast", "atmosphere", "spawn", "protect"), emptyList(), true, true, false),
            ModuleDescriptor("placeholder", "WorldScript PlaceholderAPI", OFFICIAL_MODULE_VERSION, MODULE_API_VERSION, OFFICIAL_WORLD_SCRIPT_REQUIREMENT, "", listOf("core", "rpg"), listOf("PlaceholderAPI"), true, true, false),
        )
        private val officialById = officialModules.associateBy { it.id }

        private fun matchesOfficialCatalog(descriptor: ModuleDescriptor, official: ModuleDescriptor): Boolean {
            return descriptor.name == official.name &&
                descriptor.version == official.version &&
                descriptor.apiVersion == official.apiVersion &&
                descriptor.worldScriptVersion == official.worldScriptVersion &&
                descriptor.main == official.main &&
                descriptor.dependencies == official.dependencies &&
                descriptor.softDependencies == official.softDependencies &&
                descriptor.official == official.official &&
                descriptor.builtin == official.builtin &&
                descriptor.required == official.required
        }
    }
}

enum class ModuleToggleResult {
    CHANGED,
    UNCHANGED,
    NOT_FOUND,
    REQUIRED,
    BUILTIN,
}
