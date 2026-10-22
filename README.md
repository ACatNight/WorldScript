<p align="center">
  <img src="docs/assets/readme/logo.png" width="112" height="112" alt="WorldScript logo">
</p>

<h1 align="center">WorldScript</h1>

<p align="center">Region events, exploration progress and encounters for Minecraft servers.</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-36cbb5" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/platform-Paper-36cbb5" alt="Platform: Paper">
  <img src="https://img.shields.io/badge/Minecraft-1.12.2–1.21.8-192431" alt="Minecraft compatibility target: 1.12.2–1.21.8">
  <img src="https://img.shields.io/badge/language-Kotlin-7f52ff" alt="Language: Kotlin">
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/worldscript-rpg-regions.138114/">SpigotMC</a> ·
  <a href="https://modrinth.com/plugin/worldscript">Modrinth</a> ·
  <a href="https://ankeryth.gitbook.io/worldscript/english/en_us">Documentation</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="README-zh_CN.md">简体中文</a>
</p>

## Overview

WorldScript is a region gameplay plugin for Paper servers. Define a town, forest or dungeon entrance, then configure what happens when players enter, leave or interact with it.

Connect regions to your existing quest, NPC, economy and combat plugins through commands, placeholders and Kether scripts. WorldScript tracks regions and player region progress; quest definitions remain in your quest plugin.

## Features

| | Capability | What you can configure |
| --- | --- | --- |
| <img src="docs/assets/readme/regions.svg" width="28" alt=""> | **Region shapes & hierarchy** | Cuboid and polygon boundaries, priorities, parent/child regions and supported setting inheritance. |
| <img src="docs/assets/readme/events.svg" width="28" alt=""> | **Events & scripting** | Entry, exit and block interactions; conditions, cooldowns, actions and Kether scripts. |
| <img src="docs/assets/readme/progress.svg" width="28" alt=""> | **Discovery & progression** | Player unlocks, first visits, completion, one-time rewards and advancement-style Toasts. |
| <img src="docs/assets/readme/spawn.svg" width="28" alt=""> | **Region encounters** | Vanilla or MythicMobs spawning, nearby-player checks, safe positions and alive limits. |
| <img src="docs/assets/readme/protect.svg" width="28" alt=""> | **Protection & atmosphere** | Region-based PVP policy, particle effects and PlaceholderAPI data for HUDs. |
| <img src="docs/assets/readme/editor.svg" width="28" alt=""> | **In-game configuration** | Selection tools, region list GUI, clickable chat editor, YAML files and validation. |

English, Simplified Chinese and Traditional Chinese are included. Built-in modules provide status diagnostics; external modules can extend the plugin through its module API.

## Requirements

**Compatibility target:** Paper 1.12.2–1.21.8. Use the Java version required by your particular server build.

| Minecraft | Typical Java requirement |
| --- | --- |
| 1.12.2–1.16.x | Java 8 |
| 1.17.x | Java 16 |
| 1.18–1.20.4 | Java 17 |
| 1.20.5–1.21.8 | Java 21 |

- **PlaceholderAPI** is optional, for placeholders in compatible HUDs, menus and scoreboards.
- **MythicMobs** is optional, for spawning mobs from a MythicMobs library.
- **TabooLib and Kether** are bundled; no separate installation is required.

Automated checks cover core logic and legacy/modern API adapters. They do not replace live-server testing of every supported version or integration.

## Installation

1. Download the plugin JAR from [SpigotMC](https://www.spigotmc.org/resources/worldscript-rpg-regions.138114/) or [Modrinth](https://modrinth.com/plugin/worldscript).
2. Place it in your server's `plugins/` directory.
3. Restart the server.

For upgrades, back up your WorldScript configuration and data, stop the server, replace the old JAR and restart. See the [changelog](CHANGELOG.md) for release-specific notes.

## Quick start

Get the selection tool:

```text
/ws wand
```

Left-click one corner and right-click the other, then create and edit a region:

```text
/ws create forest_entrance Forest Entrance
/ws edit forest_entrance
```

Add an enter action, then leave and re-enter the region to test it. Open the region list GUI with `/ws list`. Administration commands require `worldscript.admin`, granted to operators by default.

The region is stored in `plugins/WorldScript/regions/forest_entrance.yml`. After editing YAML, run `/ws validate`, then `/ws reload`.

The default language is `en_US`. Set `language: zh_CN` or `language: zh_TW` in `plugins/WorldScript/config.yml` and run `/ws reload`.

## Documentation

| Topic | Guide |
| --- | --- |
| Installation and configuration | [Installation](docs/gitbook/en_US/install.md) · [Configuration](docs/gitbook/en_US/config.md) |
| Regions and editing | [Regions](docs/gitbook/en_US/regions.md) · [Editor](docs/gitbook/en_US/editor.md) · [Commands](docs/gitbook/en_US/commands.md) |
| Gameplay | [Discovery Toasts](docs/gitbook/en_US/discovery-toast.md) · [Spawning](docs/gitbook/en_US/spawn.md) · [PVP protection](docs/gitbook/en_US/protect.md) |
| Integrations and extensions | [Placeholders](docs/gitbook/en_US/placeholders.md) · [Modules](docs/gitbook/en_US/modules.md) · [Module example](examples/modules/hello-worldscript-module/README.md) |
| Examples and troubleshooting | [Region examples](examples/regions) · [Troubleshooting](docs/gitbook/en_US/troubleshooting.md) |

See the [documentation index](docs/README.md) for reference manuals and integration guides.

## Development

Install JDK 8 and JDK 21. The plugin targets Java 8 bytecode; the modern API compatibility checks run on Java 21.

```sh
# Linux / macOS
./gradlew clean build
```

```powershell
# Windows
.\gradlew.bat clean build
```

The build runs JUnit regression tests, the WorldScript logic checks and Paper 1.21.8 API compatibility checks. The output is `build/libs/WorldScript-<version>.jar`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution and verification guidelines.

```text
src/main/           Plugin implementation and default resources
src/test/           Regression and API compatibility tests
examples/           Region configurations and an external module example
docs/gitbook/       English and Chinese user documentation
docs/assets/readme/ README logo and icons
gradle/             Gradle wrapper
```

## Telemetry

WorldScript uses bStats for usage statistics and does not add custom telemetry charts. Server owners can opt out by setting `enabled: false` in `plugins/bStats/config.yml` and restarting the server.

## Support & license

Report reproducible bugs through [GitHub Issues](https://github.com/ACatNight/WorldScript/issues), including the plugin version, server build, Java version, reproduction steps and relevant logs.

WorldScript is free and open source under the [MIT License](LICENSE).
