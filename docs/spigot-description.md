# WorldScript | RPG Regions

WorldScript is a region scripting plugin for Paper servers.

It is designed for open-world RPG servers. Regions can respond to player movement, block interaction, and external plugin callbacks.

WorldScript handles locations, region states, events, actions, and player region progress. Quest definitions, combat, levels, reputation, and economy can remain managed by other plugins.

## Features

- Parent and child regions
- Region variables and inherited settings
- Open, locked, dangerous, and peaceful region states
- Enter and leave events
- Block interaction events
- Messages and titles
- Sounds and particles
- Player and console commands
- Teleport actions
- Item and experience rewards
- Player unlock, entry, and completion progress
- PlaceholderAPI support
- Kether script actions
- In-game chat editor
- Configuration validation
- English, Simplified Chinese, and Traditional Chinese language files

## Region Structure

Regions can be organised into parent and child locations:

```text
forest
|- forest-entrance
|- hunter-camp
|- wolf-den
`- hidden-cave
```

Parent regions are useful for shared variables, status, atmosphere, and common settings. Child regions can override inherited values when needed.

## Events

Supported event types:

- Enter a region
- Leave a region
- Interact with a block inside a region

Each event can contain multiple actions.

## Actions

Available actions include:

- Send a message
- Show a title
- Play a sound
- Spawn particles
- Execute a player command
- Execute a console command
- Teleport a player
- Give items
- Give experience
- Change a region state
- Set player variables
- Run Kether scripts

## Commands

```text
/ws wand
/ws create <region-id>
/ws delete <region-id>
/ws list
/ws info <region-id>
/ws edit <region-id>
/ws gui
/ws validate
/ws reload
```

Permission:

```text
worldscript.admin
```

## External Plugin Progress

WorldScript does not manage quest definitions or quest steps. External plugins can update a player's region progress with:

```text
/ws progress <player> <region-id> unlock
/ws progress <player> <region-id> complete
```

This can be used by quest, dungeon, NPC, and adventure plugins.

## PlaceholderAPI

With PlaceholderAPI installed, the following placeholders are available:

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_parent_id%
%worldscript_parent_name%
%worldscript_child_id%
%worldscript_child_name%
%worldscript_region_depth%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
%worldscript_var_<key>%
```

## Installation

1. Download the latest jar.
2. Put it into the server's `plugins` folder.
3. Start the server once.
4. Edit the files in `plugins/WorldScript/`.
5. Run `/ws validate`.
6. Run `/ws reload`.

## Compatibility

- Paper 1.12.2 to 1.21.8
- Java 8 for Minecraft 1.12.2 to 1.16.x
- Java 17 for Minecraft 1.17 to 1.20.4
- Java 21 for Minecraft 1.20.5 and newer
- PlaceholderAPI is optional
- TabooLib and Kether are included in the plugin jar

## Links

- Source code: https://github.com/ACatNight/WorldScript
- Documentation: https://github.com/ACatNight/WorldScript/tree/main/docs
- Discord: https://discord.gg/5NkEuBR6hV

## License

WorldScript is released under the MIT License.
