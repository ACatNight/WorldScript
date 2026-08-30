# Spawn Module

The Spawn module answers one question: what should spawn in this region?

For example, when players approach a wolf cave, WorldScript can spawn wolves inside the cave. If nobody is nearby, it does nothing and saves performance.

## Simplest Flow

```text
/ws edit <region-id> spawn
```

Then click:

```text
[+ Select Mob]
```

If MythicMobs is installed, the GUI shows your MythicMobs mob library. Click a mob and WorldScript uses that mob ID.

After the rule is created, tune it in the chat editor:

- amount per spawn
- spawn interval
- max alive mobs
- player range
- random safe positions

```text
/ws spawn test <rule-id>
```

## Test

```text
/ws spawn test <rule-id>
```

This tells you whether the rule is disabled, the region has no safe location, or the MythicMobs ID does not exist.

## Common Settings

- Region ID: where mobs spawn.
- Mob ID: MythicMobs ID or vanilla entity type.
- Amount: default random 1 to 3.
- Max alive: default max 10.
- Player range: spawn only when players are nearby.
- Random spawning: find safe points inside the region.

## Small tip

Do not start with dense spawns in open-world areas. Begin with low amounts, longer intervals, and a max-alive limit. Then increase it after testing.
