# Protect Module

This page is for a simple case: no fighting in town, fighting allowed in the wild.

You do not need a separate protection rule for every region. Use the region status.

## Block PVP in towns

For towns, spawn areas, and safe zones, use `peaceful`:

```yaml
state:
  statuses: [peaceful]
```

Damage between players is cancelled there.

## Allow PVP in dangerous areas

For wilderness, arenas, and dangerous areas, use `dangerous`:

```yaml
state:
  statuses: [dangerous]
```

Players can fight there.

If a region has neither status, WorldScript uses the default config value. The default is to allow PVP.

## Config file

You usually do not need to edit this. Use it when you want to change the server-wide habit:

```text
plugins/WorldScript/settings/protect.yml
```

```yaml
enabled: true
pvp:
  enabled: true
  default-allow: true
  blocked-statuses:
    - peaceful
  allowed-statuses:
    - dangerous
  message:
    enabled: true
    cooldown-ms: 1500
```

If most regions should block PVP and only dangerous regions should allow it, change the default:

```yaml
pvp:
  default-allow: false
  allowed-statuses:
    - dangerous
```

## Test it

Stand in a region and run:

```text
/ws protect test
```

Or test another player:

```text
/ws protect test <player>
```

It tells you which region the player is in and whether PVP is allowed there.

After editing `protect.yml`:

```text
/ws protect reload
```

## What happens on the border?

WorldScript checks both the attacker and the victim.

If either side is inside a safe zone, the damage is cancelled. This stops players from standing just outside town and hitting someone inside.
