# Installation & Compatibility

## Install

1. Download the latest WorldScript JAR from [SpigotMC](https://www.spigotmc.org/resources/worldscript-rpg-regions.138114/) or [Modrinth](https://modrinth.com/plugin/worldscript).
2. Drop it into the server `plugins/` directory.
3. Start the server once so it creates `plugins/WorldScript/`.
4. Run `/ws wand` and select two corners, then `/ws create forest_entrance`.
5. Open the region list with `/ws list` or edit the region with `/ws edit forest_entrance`.
6. After editing files, run `/ws validate`, then `/ws reload`.

TabooLib and Kether are bundled inside WorldScript. You do not need to install TabooLib separately.

## Optional Dependencies

- PlaceholderAPI: required for placeholders in HUDs, scoreboards, tabs, or chat.
- MythicMobs: required for selecting and spawning MythicMobs mobs in the Spawn module.

## Versions

WorldScript targets Paper 1.12.2 through 1.21.8.

Use the Java version required by your server build. Typical requirements are:

- 1.12.2 - 1.16.x: Java 8
- 1.17.x: Java 16
- 1.18 - 1.20.4: Java 17
- 1.20.5 - 1.21.8: Java 21

Before using it on a live server, test your exact server version. Toast, particles, and MythicMobs integration are the parts most worth checking.
