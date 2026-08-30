# Modules

WorldScript 1.0.0 creates:

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
