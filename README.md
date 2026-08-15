# WorldScript

WorldScript is a Paper 1.21.8 region scripting plugin built with Kotlin and Gradle.

中文文档：[README-zh_CN.md](README-zh_CN.md)

## License

WorldScript is currently fully free and available under the [MIT License](LICENSE).

## Current Scope

- Parent and child regions with inherited variables, global statuses, and event scripts
- Explicit region roles (`hub`, `open_zone`, `point_of_interest`, `danger_zone`, `gate`) and external `content-id`
- Enter, leave, and interaction events
- Player variable persistence
- Per-player region unlock, first-entry, completion, and one-time reward state, stored separately from world state
- Conditions for permissions, items, variables, and region statuses
- Actions and rewards for commands, messages, teleportation, items, experience, money, variables, region unlocks, and completion
- Admin selection tool, commands, and GUI
- `/ws validate` configuration validation
- Project-local design agent: `AgentSkills/open-world-rpg-region-designer`

Quest state and quest definitions are intentionally not implemented here. Quest behavior should be provided by the server's dedicated quest plugin, such as Chemdah, and invoked through that plugin's supported command or API. WorldScript's global `statuses` only describe shared world conditions (`locked`, `open`, `dangerous`, `peaceful`); use `unlock_region` and `complete_region` for a single player's progress.

## Region Progress

Event scripts support `first-entry-only` and `repeat-entry-only`. Rewards can use `once: true` to protect a reward from being granted more than once per player. `unlock-region` / `complete-region` actions and `unlock_region` / `complete_region` rewards update the triggering player only. Use `player_region_status` conditions with `unlocked`, `entered`, or `completed` to build a focused region progression chain without adding a quest system.

## Build

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

The plugin jar is written to `build/libs/WorldScript-<version>.jar`.

Copy `examples/region-progression-template.yml` into the server's `plugins/WorldScript/regions/` directory as an open-world starter layout. Replace the region bounds and external plugin commands, then run `/ws validate` before reloading.

An external quest plugin can write back the result with `/ws progress <player> <region> <unlock|complete>`. WorldScript only records the player-region state; it does not create or manage quests.

`/ws progress` supports players who have played on the server before, including offline players. Integrations can also use `WorldScriptPlugin.playerProgress` with a player UUID from the server thread.

`INTERACT` scripts run only for an uncancelled main-hand right click on a block. This avoids accidental left-click and off-hand duplicate execution.

If PlaceholderAPI is installed, HUD plugins can use `%worldscript_region_name%`, `%worldscript_parent_name%`, `%worldscript_child_name%`, `%worldscript_region_role%`, `%worldscript_region_content_id%`, `%worldscript_region_depth%`, `%worldscript_region_unlocked%`, `%worldscript_region_entered%`, `%worldscript_region_completed%`, and `%worldscript_region_world%`. These variables do not include level or reputation.

The design agent audits region identity, open-world branching, unlock pacing, parent-child inheritance, player/global state scope, HUD semantics, and external quest boundaries before implementation decisions are made.

Chinese operator documentation is available in [docs/config-reference-zh_CN.md](docs/config-reference-zh_CN.md) and [docs/integration-zh_CN.md](docs/integration-zh_CN.md).

The default language is `en_US`. Set `language: zh_CN` in `plugins/WorldScript/config.yml` and run `/ws reload` to use the bundled Chinese messages.
