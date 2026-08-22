# WorldScript Wiki

WorldScript adds named regions to a Paper server. A region is a box with a name, optional parent, state, variables, particles, and event actions. It is not a quest or combat plugin.

## Install

1. Put `WorldScript-<version>.jar` in `plugins/`.
2. Start the server once.
3. Put region files in `plugins/WorldScript/regions/`.
4. Run `/ws validate` and fix any reported path.
5. Run `/ws reload` after editing a file.

TabooLib and Kether are included in the jar. PlaceholderAPI is optional and is only needed for HUD placeholders.

This page describes WorldScript `0.1.92`.

## Create A Region

In game, get the selection tool and mark two corners:

```text
/ws wand
```

Left-click one block, right-click the other, then run:

```text
/ws create forest_entrance Forest Entrance
```

The first argument is the internal ID and the optional remaining text is the player-facing display name. The selection tool uses `selection.tool` from `config.yml`. The plugin automatically assigns the smallest containing region as the parent when a new region is created.

The chat editor supports editing the player-facing name without changing the internal ID, adding and editing local variables, showing whether a variable is local or inherited, and confirming removal of local overrides.

## Region File

This is the format used by the examples:

```yaml
schema: 2
id: forest_entrance
identity:
  name: Forest Entrance
  role: point_of_interest
  content-id: forest_intro
  parent: whispering_forest
location:
  world: world
  world-id: world
  priority: 0
  min: {x: 0, y: 60, z: 0}
  max: {x: 40, y: 90, z: 40}
state:
  inherit: true
  statuses: [open]
variables:
  short_name: Forest Entrance
events: {}
```

The `id` is used by commands and other plugins. `identity.name` is shown to players. `content-id` is only an identifier for an external content plugin; WorldScript does not create content from it.

Roles are labels used by the editor and placeholders: `hub`, `open_zone`, `point_of_interest`, `danger_zone`, and `gate`.

`identity.parent` makes the region a child. Parent variables, states, particles, and events can be inherited. A value written in the child takes priority over the inherited value. Put shared ambience in the parent and specific interactions in the child.

## Events

Available event keys:

```text
enter       player enters the region
leave       player leaves the region
interact    main-hand right-click on a block
left-click  left-click on a block
right-click right-click on a block
```

For enter events, `mode` can be `always`, `first`, or `repeat`.

```yaml
events:
  enter:
    enabled: true
    mode: first
    actions:
      - type: message
        value: "&6You found the forest entrance."
      - type: sound
        sound: BLOCK_PORTAL_TRIGGER
        volume: 1.0
        pitch: 0.9
```

Actions can send a message, play a sound, show a title, run a player or console command, teleport, set a variable, change a region state, give an item/experience/money, unlock a region, or complete a region. Kether is available for sequences and logic that do not fit one action.

Quest steps belong in Chemdah or another quest plugin. WorldScript supplies the location event and progress storage.

## PlaceholderAPI

Install and enable PlaceholderAPI, then run `/ws reload` if it was added or enabled after WorldScript. WorldScript also registers automatically when PlaceholderAPI is already enabled at startup. The current region is the deepest accessible region containing the player.

If a placeholder remains unchanged, verify that the target scoreboard, tab, chat, or HUD plugin supports PlaceholderAPI and that `/papi parse me %worldscript_region_id%` returns a value. WorldScript logs whether its expansion was registered; a duplicate `worldscript` identifier from another expansion will be reported as a registration warning.

Fixed placeholders:

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_region_role%
%worldscript_region_content_id%
%worldscript_parent_id%
%worldscript_parent_name%
%worldscript_child_id%
%worldscript_child_name%
%worldscript_region_path%
%worldscript_region_depth%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
```

Variables from the current effective region (automatically discovered):

```text
%worldscript_var_short_name%
%worldscript_region_var_short_name%
%worldscript_short_name%
```

Variables from the effective parent region:

```text
%worldscript_parent_var_biome%
```

For a shorter, more readable form, use `%worldscript_parent_<key>%` and `%worldscript_child_<key>%`. The child form means the deepest current region; it is useful when the same layout is shared by parent and child regions.

Replace `short_name` and `biome` with the exact keys under `variables:`. Missing variables return an empty string.

The combined display name is configurable in `config.yml`:

```yaml
placeholders:
  region-name-format: '{parent} / {current}'
```

Available tokens are `{parent}`, `{current}`, `{child}`, `{id}`, and `{path}`. When there is no parent, the default parent separator is removed automatically.

### Testing placeholders

Stand inside the target region and run:

```text
/papi parse me %worldscript_region_name%
/papi parse me %worldscript_region_path%
/papi parse me %worldscript_short_name%
/papi parse me %worldscript_parent_biome%
```

With a parent named `Whispering Forest` and a child named `Forest Entrance`, the default combined result is:

```text
Whispering Forest / Forest Entrance
```

If the placeholder is returned unchanged, confirm that PlaceholderAPI is installed and that the HUD, tab, scoreboard, or chat plugin performing the parse supports PlaceholderAPI.

## Particles

Particles are optional and run on a timer. They are useful for portals, borders, and points of interest, but a large count with a short interval can be expensive.

```yaml
particle:
  enabled: true
  preset: PORTAL
  type: END_ROD
  count: 2
  interval-ticks: 20
  spread: {x: 1.5, y: 0.8, z: 1.5}
  speed: 0.0
```

Only the deepest active region displays particles. A child particle setting overrides its parent.

## Commands

```text
/ws help
/ws wand
/ws create <region-id> [display name]
/ws delete <region-id>
/ws list
/ws info <region-id>
/ws gui
/ws edit <region-id>
/ws reload
/ws validate [region-id]
/ws progress <player> <region-id> <unlock|complete>
```

`/ws progress` is intended for external plugins. It does not define quests or objectives.

## Troubleshooting

- If a region does not load, run `/ws validate` and use the file path in the error.
- If an event does not run, check its `enabled`, `mode`, cooldown, and parent override.
- If a child does not inherit anything, check `identity.parent`, `state.inherit`, and that both regions use the same world.
- If a sound or particle is skipped, check the Bukkit name for the server version.
- If Kether fails, test a simple message first, then add one operation at a time.

## Language And Build

The bundled language files are in `plugins/WorldScript/lang/`: `en_US.yml`, `zh_CN.yml`, and `zh_TW.yml`. Set `language` in `config.yml`, then run `/ws reload`.

Build from source with:

```text
gradlew.bat clean build
```

The target is Paper 1.12.2 through 1.21.8. Test the exact server version before using the plugin in production.
