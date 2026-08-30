# Spawn Module

The Spawn module binds spawn rules to regions. When players are near a region, WorldScript can randomly find safe positions inside that region and spawn mobs.

## Simplest Flow

```text
/ws edit <region-id> spawn
```

Then:

1. Click `[+ Select Mob]`.
2. Choose a MythicMobs mob in the GUI.
3. A rule is created and bound to the current region.
4. Tune amount, interval, max alive, player range, and random spawning in the chat editor.
5. Test the rule.

```text
/ws spawn test <rule-id>
```

## Common Settings

- Region ID: where mobs spawn.
- Mob ID: MythicMobs ID or vanilla entity type.
- Amount: default random 1 to 3.
- Max alive: default max 10.
- Player range: spawn only when players are nearby.
- Random spawning: find safe points inside the region.

## MythicMobs

When MythicMobs is installed, the GUI reads the MythicMobs mob library. Click a mob to create or update a spawn rule.

