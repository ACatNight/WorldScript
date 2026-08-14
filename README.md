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
- `/ws validate` configuration validation
- Project-local design agent: `AgentSkills/open-world-rpg-region-designer`

Quest state and quest definitions are intentionally not implemented here. Quest behavior should be provided by the server's dedicated quest plugin, such as Chemdah, and invoked through that plugin's supported command or API.

## Region Progress

Event scripts support `first-entry-only` and `repeat-entry-only`. Rewards can use `once: true` to protect a reward from being granted more than once per player. `unlock-region` actions and `unlock_region` rewards unlock a region for the triggering player only. Use `player_region_status` conditions with `unlocked`, `entered`, or `completed` to build a focused region progression chain without adding a quest system.

## Build

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

The plugin jar is written to `build/libs/WorldScript-<version>.jar`.

Copy `examples/region-progression-template.yml` into the server's `plugins/WorldScript/regions/` directory as a starting point. Replace the region bounds and external plugin commands, then run `/ws validate` before reloading.

An external quest plugin can write back the result with `/ws progress <player> <region> <unlock|complete>`. WorldScript only records the player-region state; it does not create or manage quests.

If PlaceholderAPI is installed, HUD plugins can use `%worldscript_region_name%`, `%worldscript_parent_name%`, `%worldscript_child_name%`, `%worldscript_region_depth%`, `%worldscript_region_unlocked%`, `%worldscript_region_entered%`, `%worldscript_region_completed%`, and `%worldscript_region_world%`. These variables do not include level or reputation.

The design agent audits region identity, open-world branching, unlock pacing, parent-child inheritance, player/global state scope, HUD semantics, and external quest boundaries before implementation decisions are made.
