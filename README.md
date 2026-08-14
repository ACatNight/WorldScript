# WorldScript

WorldScript is a Paper 1.21.8 region scripting plugin built with Kotlin and Gradle.

## Current Scope

- Parent and child regions with inherited variables, statuses, and event scripts
- Enter, leave, and interaction events
- Player variable persistence
- Per-player region unlock, first-entry, completion, and one-time reward state
- Conditions for levels, permissions, items, variables, and region statuses
- Actions and rewards for commands, messages, teleportation, items, experience, money, variables, and region unlocks
- Admin selection tool, commands, and GUI

Quest state and quest definitions are intentionally not implemented here. Quest behavior should be provided by the server's dedicated quest plugin, such as Chemdah, and invoked through that plugin's supported command or API.

## Region Progress

Event scripts support `first-entry-only` and `repeat-entry-only`. Rewards can use `once: true` to protect a reward from being granted more than once per player. `unlock-region` actions and `unlock_region` rewards unlock a region for the triggering player only. Use `player_region_status` conditions with `unlocked`, `entered`, or `completed` to build a focused region progression chain without adding a quest system.

## Build

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

The plugin jar is written to `build/libs/WorldScript-<version>.jar`.
