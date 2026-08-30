package com.worldscript.foundation.module

import org.bukkit.configuration.file.YamlConfiguration

data class ModuleDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val worldScriptVersion: String,
    val main: String,
    val dependencies: List<String>,
    val softDependencies: List<String>,
    val official: Boolean,
    val builtin: Boolean,
    val required: Boolean,
) {
    fun toYaml(): String {
        return buildString {
            append("id: ").append(id).append('\n')
            append("name: ").append(name).append('\n')
            append("version: ").append(version).append('\n')
            append("api-version: ").append(apiVersion).append('\n')
            append("worldscript-version: \"").append(worldScriptVersion).append("\"\n")
            append("main: \"").append(main).append("\"\n")
            append("official: ").append(official).append('\n')
            append("builtin: ").append(builtin).append('\n')
            append("required: ").append(required).append('\n')
            append("dependencies:\n")
            dependencies.forEach { append("  - ").append(it).append('\n') }
            append("soft-dependencies:\n")
            softDependencies.forEach { append("  - ").append(it).append('\n') }
        }
    }

    companion object {
        fun parse(config: YamlConfiguration, source: String): Result<ModuleDescriptor> {
            val id = config.getString("id").orEmpty().trim().lowercase()
            if (!id.matches(Regex("[a-z0-9_-]+"))) {
                return Result.failure(IllegalArgumentException("$source: invalid module id '$id'"))
            }
            val name = config.getString("name").orEmpty().trim().ifBlank { id }
            val version = config.getString("version").orEmpty().trim().ifBlank { "0.1.0" }
            val apiVersion = config.getInt("api-version", 1).coerceAtLeast(1)
            val worldScriptVersion = config.getString("worldscript-version").orEmpty().trim().ifBlank { ">=0.1.0" }
            val main = config.getString("main").orEmpty().trim()
            return Result.success(
                ModuleDescriptor(
                    id = id,
                    name = name,
                    version = version,
                    apiVersion = apiVersion,
                    worldScriptVersion = worldScriptVersion,
                    main = main,
                    dependencies = config.getStringList("dependencies").map { it.trim().lowercase() }.filter { it.isNotBlank() },
                    softDependencies = config.getStringList("soft-dependencies").map { it.trim().lowercase() }.filter { it.isNotBlank() },
                    official = config.getBoolean("official", false),
                    builtin = config.getBoolean("builtin", false),
                    required = config.getBoolean("required", false),
                ),
            )
        }
    }
}

enum class ModuleState {
    BUILTIN,
    ENABLED,
    DISABLED,
    FAILED,
}

data class ModuleReport(
    val descriptor: ModuleDescriptor,
    val source: String,
    val state: ModuleState,
    val reasonKey: String,
    val detail: String = "",
)
