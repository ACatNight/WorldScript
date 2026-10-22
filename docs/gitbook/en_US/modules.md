# Modules

WorldScript 1.0.1 creates:

```text
plugins/WorldScript/modules/
```

Official built-in module descriptor JARs:

```text
worldscript-core.jar
worldscript-editor.jar
worldscript-toast.jar
worldscript-atmosphere.jar
worldscript-spawn.jar
worldscript-protect.jar
worldscript-rpg.jar
worldscript-placeholder.jar
```

These official modules are currently executed by the main plugin. The descriptor JARs provide module status, diagnostics, and the external module API surface.

## Configuration

```yaml
auto-install-official: true
load-external: false
disabled: []
```

External modules are disabled by default. Set `load-external: true` only when you intentionally want to load external module entry classes.

## Status

```text
/ws modules list
/ws modules info spawn
```

## Services for external modules

An external module can request WorldScript's core services from `onLoad(context)`. The nullable lookup lets a module keep working when an optional service is unavailable.

```kotlin
val regions = context.service(RegionCoreService::class.java)
val progress = context.service(PlayerRegionProgressService::class.java)
```

The currently exposed interfaces are:

- `RegionCoreService`: read and save regions, and query regions at a location.
- `PlayerRegionProgressService`: read and update a player's unlock, entry, and completion state.
- `ScriptActionService`: execute a list of region actions.

Use these interfaces instead of depending on WorldScript's internal implementation classes.
