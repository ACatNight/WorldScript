# Commands

## Basic

```text
/ws help
/ws reload
/ws validate [region-id]
```

## Regions

```text
/ws wand
/ws create <region-id> [display name]
/ws delete <region-id>
/ws list
/ws info <region-id>
/ws edit <region-id>
/ws polygon start <region-id>
/ws polygon preview
/ws polygon undo
/ws polygon redo
/ws polygon finish
```

## Player Progress

```text
/ws progress <player> <region-id> unlock
/ws progress <player> <region-id> complete
```

## Toast

```text
/ws toast test [player] <region-id>
/ws toast diagnose <region-id>
```

## Modules

```text
/ws modules list
/ws modules info <module-id>
/ws modules enable <module-id>
/ws modules disable <module-id>
/ws modules reload
```

## Spawn

```text
/ws spawn list
/ws spawn test <rule-id>
/ws spawn reload
```

## Protect

```text
/ws protect test [player]
/ws protect reload
```

`test` tells you whether the player's current region allows PVP.

The management commands require `worldscript.admin`, granted to operators by default. `/ws list` opens the GUI; there is no `/ws gui` command.
