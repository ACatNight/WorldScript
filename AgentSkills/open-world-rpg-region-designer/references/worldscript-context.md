# WorldScript Context

This repository is a Paper 1.21.8 Kotlin plugin. It is not a Germ project.

Current region capabilities include rectangular regions, parent-child inheritance, enter/leave/interact events, player variables, per-player region unlock/entered/completed state, conditions, rewards, first-entry/repeat-entry controls, one-time rewards, configuration validation, and a narrow `/ws progress` bridge.

Quest definitions and quest persistence are intentionally outside this plugin. A dedicated quest plugin such as Chemdah owns task state and calls WorldScript when an external result should unlock or complete a player-region state.

For nested enter and leave events, parent scripts run for the parent area. A child should set `override-parent: true` when it owns its own enter or leave content; otherwise the inherited parent script is not dispatched again for the child.

For HUD design, use deterministic semantics:

- Current region: the deepest matching region, resolved by depth and then priority.
- Parent region: the direct parent of the current region.
- Child region: the active nested region when the player is inside one; empty outside a child.
- Outside all regions: empty values or a configured fallback such as `Wilderness`.

When PlaceholderAPI is installed, the plugin exposes `%worldscript_region_name%`, `%worldscript_parent_name%`, `%worldscript_child_name%`, `%worldscript_region_depth%`, `%worldscript_region_unlocked%`, and `%worldscript_region_completed%`, plus entered and world values. Confirm the target HUD's placeholder protocol before configuring it.
