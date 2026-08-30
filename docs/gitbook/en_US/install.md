# Installation & Compatibility

## Installation

1. Download `WorldScript-1.0.0.jar` from [SpigotMC](https://www.spigotmc.org/resources/worldscript-1-12-2-1-21-8-%EF%B8%8Frpg-region-framework-%E2%9A%A1dynamic-events-player-progression%E2%9A%A1.138114/).
2. Put it in the server `plugins/` directory.
3. Start the server once to generate `plugins/WorldScript/`.
4. Run `/ws validate`.
5. After editing files, run `/ws reload`.

TabooLib and Kether are bundled inside WorldScript. You do not need to install TabooLib separately.

## Optional Dependencies

- PlaceholderAPI: required for placeholders in HUDs, scoreboards, tabs, or chat.
- MythicMobs: required for selecting and spawning MythicMobs mobs in the Spawn module.

## Compatibility Target

WorldScript targets Paper 1.12.2 through 1.21.8.

Recommended Java versions:

- 1.12.2 - 1.16.x: Java 8
- 1.17 - 1.20.4: Java 17
- 1.20.5 - 1.21.8: Java 21

Run a server smoke test on your exact target version before production use.
