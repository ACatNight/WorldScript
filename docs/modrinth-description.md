# WorldScript

WorldScript is a Paper plugin for building RPG locations with regions.

Define a region, attach enter/leave/click events, play feedback, and let quest or other plugins update the player's region progress.

WorldScript handles locations and region state. It does not replace your quest, combat, economy, or HUD plugin.

## Features

- Parent and child regions
- Region states: `open`, `locked`, `dangerous`, and `peaceful`
- Inherited region variables, events, states, and particles
- Player-specific unlock, first-entry, and completion state
- Enter, leave, left-click, right-click, and interaction events
- Chat editor and region list
- YAML region files with `/ws validate`
- PlaceholderAPI support
- Bundled TabooLib and Kether support

## Actions

Region events can run:

- Chat messages and titles
- Sounds
- Player or console commands
- Teleportation
- Items, experience, and money
- Player variables
- Region state changes
- Region unlock and completion
- Kether scripts

Particles are configured as optional region atmosphere. Child regions can override their parent effect.

## External Quest Plugins

WorldScript does not create quest definitions or quest steps.

Use your quest plugin for the quest itself, then update WorldScript when the player reaches a location milestone:

```text
/ws progress <player> <region-id> unlock
/ws progress <player> <region-id> complete
```

This works well with Chemdah and other plugins that can run commands after a quest step is completed.

## HUD Placeholders

With PlaceholderAPI installed:

```text
%worldscript_region_name%
%worldscript_parent_name%
%worldscript_region_role%
%worldscript_region_content_id%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_var_<key>%
%worldscript_parent_var_<key>%
```

For example, a region with `variables.short_name: Forest Entrance` can be shown with:

```text
%worldscript_var_short_name%
```

## Compatibility

- Paper 1.12.2 to 1.21.8
- PlaceholderAPI is optional
- TabooLib and Kether are bundled in the plugin jar
- Java 8 for 1.12.2-1.16.x
- Java 17 for 1.17-1.20.4
- Java 21 for 1.20.5-1.21.8

## Links

- Wiki: https://github.com/ACatNight/WorldScript/blob/main/docs/wiki.md
- Discord: https://discord.gg/5NkEuBR6hV
