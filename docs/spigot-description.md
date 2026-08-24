# WorldScript — Minecraft RPG Region Framework

WorldScript is a Paper plugin for building interactive RPG worlds with configurable regions, events, player progression, rewards, conditions, and scripts.

It is designed for server owners who want locations to behave like gameplay systems: a player can enter a town, discover a mine, unlock a dungeon entrance, receive a title, hear a sound, run a command, or trigger a script. WorldScript manages the region layer and works alongside quest, economy, combat, NPC, skills, and HUD plugins.

## Features

### Region hierarchy

- Create regions with a selection wand or YAML files.
- Organize large maps with parent and child regions.
- Inherit variables, statuses, events, particles, and discovery settings.
- Override only the settings that need to be different in a child region.
- Assign roles such as hub, open zone, point of interest, danger zone, or gate.
- Configure priority, world, bounds, display name, and content ID.

### Region events

Each event can be enabled or disabled, given a cooldown, configured for first-entry or repeat-entry behaviour, and assigned multiple actions.

- Enter a region.
- Leave a region.
- Interact with a block inside a region.
- Left-click a block inside a region.
- Right-click a block inside a region.

### Actions and scripting

Actions execute in the configured order. Supported action types include chat messages, titles and subtitles, Minecraft sounds, particles, player commands, console commands, teleport, item/experience/money rewards, player and region variables, region status changes, unlock/complete region actions, and Kether scripts.

WorldScript includes the required TabooLib and Kether runtime in its plugin jar.

### Region discovery and progression

Discovery is optional and can be disabled globally or per region. When a player discovers a region, administrators can independently enable a title, subtitle, sound, first-discovery actions, commands, messages, rewards, or scripts.

Unlock, entered, and completed progress is stored per player. One player unlocking a location does not automatically unlock it for every other player.

### Entry conditions

Entry conditions are optional and disabled by default. Supported requirements include permission nodes, PlaceholderAPI comparisons, player or region variables, item requirements, region status, and player-specific statuses such as unlocked, entered, or completed.

Conditions support AND and OR groups. When a player does not meet a condition, WorldScript can show a configurable message, title, subtitle, and failure actions.

### In-game editor and GUI

- `/ws list` opens the region list GUI. Left-click edits, right-click teleports, and middle-click opens global settings.
- `/ws edit <region-id>` opens the chat editor for overview, advanced settings, events, discovery, conditions, actions, variables, and particle atmosphere.
- Clickable controls, previews, numeric steppers, confirmation prompts, and chat input reduce the need to remember YAML syntax.
- English, Simplified Chinese, and Traditional Chinese language files are included.
- YAML remains available for advanced configuration and version control.

### PlaceholderAPI

When PlaceholderAPI is installed, WorldScript exposes region and progression values for scoreboards, tab lists, HUDs, menus, and other compatible plugins:

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_region_path%
%worldscript_parent_name%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
%worldscript_var_<key>%
%worldscript_region_var_<key>%
%worldscript_parent_var_<key>%
```

PlaceholderAPI is optional; core region and event features do not require it.

## Quick start

### Requirements

- Paper 1.12.2–1.21.8.
- Java 8 for Minecraft 1.12.2–1.16.x.
- Java 17 for Minecraft 1.17–1.20.4.
- Java 21 for Minecraft 1.20.5–1.21.8.

### Installation

1. Place the WorldScript jar in the server `plugins/` folder.
2. Start the server once.
3. Run `/ws wand` and select two corners.
4. Create a region:

```text
/ws create forest_entrance
```

5. Open the GUI or chat editor:

```text
/ws list
/ws edit forest_entrance
```

6. Validate and reload configuration:

```text
/ws validate
/ws reload
```

## Commands

```text
/ws wand
/ws create <region-id> [display name]
/ws delete <region-id>
/ws list
/ws info <region-id>
/ws edit <region-id>
/ws reload
/ws validate [region-id]
/ws progress <player> <region-id> <unlock|complete>
```

Required permission: `worldscript.admin`.

## Configuration

Regions are stored as YAML files in `plugins/WorldScript/regions/`. `/ws validate` checks region IDs, worlds, bounds, parent relationships, event actions, conditions, discovery actions, sounds, and supported values.

Player-facing discovery, sounds, rewards, and entry conditions are disabled by default. Administrators can enable each feature independently.

WorldScript targets Paper 1.12.2 through 1.21.8. Test the exact Paper build and plugin combination used by your server before production deployment.

WorldScript is free and open source under the MIT License.

## Screenshots

Upload real in-game screenshots to the BuiltByBit media carousel and include the same images in this description. Recommended screenshots are: the `/ws list` GUI, region overview editor, events page, discovery settings, conditions page, and action profile page. Do not leave image placeholders in the published description; add uploaded image URLs through the BuiltByBit editor.
