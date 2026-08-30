# Protect Module

The first Protect feature is PVP control.

The rule is simple: WorldScript checks the region status and decides whether players can damage each other there.

## Default rules

- `peaceful`: blocks PVP
- `dangerous`: allows PVP
- no matched status: follows the default setting, which allows PVP by default

For a town or safe zone:

```yaml
state:
  statuses: [peaceful]
```

For wilderness, arenas, or dangerous areas:

```yaml
state:
  statuses: [dangerous]
```

## Config

File:

```text
plugins/WorldScript/settings/protect.yml
```

Default:

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

If most regions should block PVP and only dangerous regions should allow it:

```yaml
pvp:
  default-allow: false
  allowed-statuses:
    - dangerous
```

## Test

Stand in a region and run:

```text
/ws protect test
```

Or test another player:

```text
/ws protect test <player>
```

After editing the config:

```text
/ws protect reload
```

## How damage is checked

WorldScript checks both the attacker and the victim.

If either side is standing in a region that blocks PVP, the damage is cancelled. This prevents players from standing outside a safe zone and attacking someone inside it.

