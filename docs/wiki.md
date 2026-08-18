# WorldScript Wiki

WorldScript is a free, MIT-licensed Paper plugin for authoring open-world RPG locations. It handles where players are, what a location means, which feedback it plays, and how region progress changes. It deliberately does not replace a quest, combat, economy, or HUD plugin.

## 1. Scope

Use WorldScript for region bounds, parent-child relationships, world states, player progress, location feedback, and connections to other plugins.

| Concern | Recommended owner |
| --- | --- |
| Region bounds, hierarchy, and state | WorldScript |
| Quest objectives, steps, and dialogue | An external quest plugin |
| Balances and payments | An economy plugin |
| HUD presentation | PlaceholderAPI and a HUD plugin |
| Mobs, combat, and drops | The relevant combat plugin |

This boundary keeps a region focused on a clear gameplay purpose instead of turning it into a second quest system.

## 2. Installation

1. Place `WorldScript-<version>.jar` in the server `plugins/` directory.
2. Start the server once to create `plugins/WorldScript/`.
3. Copy an example from `examples/` into `plugins/WorldScript/regions/`.
4. Update the world name, bounds, content ID, and external commands.
5. Run `/ws validate`, then `/ws reload`.

TabooLib and Kether are bundled into the WorldScript jar. PlaceholderAPI is optional.

## 3. Creating a Region

Use the selection tool to mark two corners:

```text
/ws wand
```

Left-click and right-click blocks with the tool, then create the region:

```text
/ws create starter_valley
```

Useful follow-up commands:

```text
/ws list
/ws info starter_valley
/ws gui
```

The region list is intentionally small: left-click an entry to teleport to its center, or right-click it to open the chat editor.

## 4. Region Format

Schema 2 is the recommended format:

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

Available roles are `hub`, `open_zone`, `point_of_interest`, `danger_zone`, and `gate`.

Set a parent with `identity.parent`. Parent regions are best for shared environmental feedback and rules; keep important story moments in a specific child region. A child value overrides the matching inherited value when it is configured locally.

## 5. Events and Actions

Built-in event keys:

- `enter`: the player enters a region
- `leave`: the player leaves a region
- `interact`: the player right-clicks a block with their main hand
- `left-click`: the player left-clicks a block
- `right-click`: the player right-clicks a block

Event modes are `always`, `first`, and `repeat`.

```yaml
events:
  enter:
    enabled: true
    mode: first
    actions:
      - type: message
        value: "&6You discovered the forest entrance."
      - type: sound
        sound: BLOCK_PORTAL_TRIGGER
        volume: 1.0
        pitch: 0.9
```

Built-in actions cover messages, sounds, player and console commands, teleportation, variables, region states, items, experience, money, unlocking, and completion. Prefer these actions for simple feedback. Use Kether only for branching, waiting, or longer sequences.

## 6. External Quest Callbacks

WorldScript does not create or track quest steps. When an external plugin finishes a quest, it can update a player's location progress with:

```text
/ws progress <player> <region> unlock
/ws progress <player> <region> complete
```

External API callbacks must be moved back to the server thread before calling WorldScript.

## 7. HUD Placeholders

With PlaceholderAPI installed, these placeholders are available:

```text
%worldscript_region_name%
%worldscript_parent_name%
%worldscript_child_name%
%worldscript_region_role%
%worldscript_region_content_id%
%worldscript_region_depth%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
```

Use stable English keys such as `chapter`, `short_name`, and `biome` for region variables.

## 8. Region Atmosphere

Particles provide location atmosphere, not constant notifications. Keep normal areas subtle, then use stronger effects for portals, entrances, and major points of interest.

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

Only the deepest region containing a player displays its particle effect. A child can override its parent, preventing duplicate effects in nested regions.

## 9. Commands

```text
/ws help
/ws wand
/ws create <region-id>
/ws delete <region-id>
/ws list
/ws info <region-id>
/ws gui
/ws edit <region-id>
/ws reload
/ws validate [region-id]
/ws progress <player> <region-id> <unlock|complete>
```

## 10. Troubleshooting

1. Run `/ws validate [region-id]` first.
2. Confirm the file is in `plugins/WorldScript/regions/`.
3. Check the world name, `world-id`, and bounds.
4. Confirm a parent and child are in the same world and the parent contains the child.
5. Check `enabled`, event mode, and cooldown.
6. Test external commands from the console before adding them to an action.
7. For Kether, start with one simple `message` action, then add complexity gradually.

## 11. Compatibility and Build

WorldScript targets Paper 1.12.2 through 1.21.8. Use Java 8 for 1.12.2-1.16.x, Java 17 for 1.17-1.20.4, and Java 21 for 1.20.5-1.21.8. Test the exact target server version before production use.

Build from source with:

```text
gradlew.bat clean build
```

## 12. Languages

Language files are located in the plugin `lang/` directory:

- `en_US.yml`: default language
- `zh_CN.yml`: Simplified Chinese
- `zh_TW.yml`: Traditional Chinese

Set `language` in `config.yml`, then run `/ws reload`. To customize a language, copy `en_US.yml` and edit only the value after each key. Do not rename keys, placeholders, or color codes. Missing keys fall back to the default language after an upgrade.
