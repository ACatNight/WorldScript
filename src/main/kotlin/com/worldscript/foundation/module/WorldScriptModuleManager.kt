package com.worldscript.foundation.module

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class WorldScriptModuleManager(private val plugin: JavaPlugin) {
    private val modulesDirectory = File(plugin.dataFolder, "modules")
    private val disabledDirectory = File(modulesDirectory, "disabled")
    private val reports = mutableListOf<ModuleReport>()

    fun initialize() {
        if (!modulesDirectory.exists()) modulesDirectory.mkdirs()
        if (!disabledDirectory.exists()) disabledDirectory.mkdirs()
        if (plugin.config.getBoolean("modules.auto-install-official", true)) {
            installOfficialModules()
        }
        reload()
    }

    fun reload() {
        reports.clear()
        val descriptors = scanDescriptors()
        val ordered = order(descriptors)
        ordered.forEach { candidate -> report(candidate) }
    }

    fun close() {
        reports.clear()
    }

    fun all(): List<ModuleReport> = reports.toList()

    fun find(id: String): ModuleReport? = reports.firstOrNull { it.descriptor.id.equals(id, true) }

    private fun installOfficialModules() {
        officialModules.forEach { descriptor ->
            val file = File(modulesDirectory, "worldscript-${descriptor.id}.jar")
            if (file.isFile) return@forEach
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

    private fun scanDescriptors(): List<ModuleCandidate> {
        val result = mutableListOf<ModuleCandidate>()
        scanDirectory(modulesDirectory, false, result)
        scanDirectory(disabledDirectory, true, result)
        officialModules.filter { it.required && result.none { candidate -> candidate.descriptor.id == it.id } }
            .forEach { result += ModuleCandidate(it, "builtin:${it.id}", false) }
        return result
    }

    private fun scanDirectory(directory: File, disabledByDirectory: Boolean, result: MutableList<ModuleCandidate>) {
        directory.listFiles { file -> file.isFile && file.extension.equals("jar", true) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                val descriptor = readDescriptor(file)
                if (descriptor == null) {
                    reports += failedPlaceholder(file.name, "Missing or invalid module.yml")
                } else {
                    val official = officialById[descriptor.id]
                    if (official == null) {
                        reports += ModuleReport(descriptor, file.name, ModuleState.FAILED, "Unknown module id")
                    } else if (!matchesOfficialCatalog(descriptor, official)) {
                        reports += ModuleReport(descriptor, file.name, ModuleState.FAILED, "Official module descriptor does not match the catalog")
                    } else {
                        result += ModuleCandidate(official, file.name, disabledByDirectory)
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
                reports += ModuleReport(candidate.descriptor, candidate.source, ModuleState.FAILED, "Duplicate module id")
            }
        }
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val ordered = mutableListOf<ModuleCandidate>()
        fun visit(candidate: ModuleCandidate) {
            val id = candidate.descriptor.id
            if (id in visited) return
            if (!visiting.add(id)) {
                reports += ModuleReport(candidate.descriptor, candidate.source, ModuleState.FAILED, "Circular dependency")
                return
            }
            candidate.descriptor.dependencies.forEach { dependency ->
                unique[dependency]?.let(::visit) ?: reports.add(ModuleReport(candidate.descriptor, candidate.source, ModuleState.FAILED, "Missing dependency: $dependency"))
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
        if (disabled && !descriptor.required && !descriptor.builtin) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.DISABLED, "Disabled by configuration")
            return
        }
        if (!supportsApi(descriptor.apiVersion)) {
            reports += ModuleReport(descriptor, candidate.source, ModuleState.FAILED, "Unsupported module API ${descriptor.apiVersion}")
            return
        }
        if (reports.any { it.descriptor.id == descriptor.id && it.state == ModuleState.FAILED }) return
        reports += ModuleReport(descriptor, candidate.source, ModuleState.BUILTIN, "Built-in module descriptor")
    }

    private fun disabledIds(): Set<String> =
        plugin.config.getStringList("modules.disabled").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

    private fun supportsApi(apiVersion: Int): Boolean = apiVersion == MODULE_API_VERSION

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
    )

    companion object {
        const val MODULE_API_VERSION = 1

        val officialModules = listOf(
            ModuleDescriptor("core", "WorldScript Core", "0.1.0", MODULE_API_VERSION, ">=0.1.0", "", emptyList(), emptyList(), true, true, true),
            ModuleDescriptor("rpg", "WorldScript RPG", "0.1.0", MODULE_API_VERSION, ">=0.1.0", "", listOf("core"), emptyList(), true, true, false),
            ModuleDescriptor("toast", "WorldScript Toast", "0.1.0", MODULE_API_VERSION, ">=0.1.0", "", listOf("core"), emptyList(), true, true, false),
            ModuleDescriptor("atmosphere", "WorldScript Atmosphere", "0.1.0", MODULE_API_VERSION, ">=0.1.0", "", listOf("core", "rpg"), emptyList(), true, true, false),
            ModuleDescriptor("editor", "WorldScript Editor", "0.1.0", MODULE_API_VERSION, ">=0.1.0", "", listOf("core", "rpg", "toast", "atmosphere"), emptyList(), true, true, false),
            ModuleDescriptor("placeholder", "WorldScript PlaceholderAPI", "0.1.0", MODULE_API_VERSION, ">=0.1.0", "", listOf("core", "rpg"), listOf("PlaceholderAPI"), true, true, false),
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
