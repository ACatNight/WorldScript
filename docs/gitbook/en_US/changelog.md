# Changelog

## 1.0.1

- Fixed discovery Toast previews freezing the server thread on modern Paper while Bukkit initialized legacy material conversion.
- Modern Toast icons now use canonical registry keys directly. Explicit legacy material values use the configured fallback icon.

## 1.0.0

WorldScript 1.0.0 adds several ways to build exploration and combat areas:

- Polygon outlines for irregular regions, alongside the existing cuboid selection tool.
- Advancement-style discovery Toasts, including editor controls and preview/diagnostic commands.
- Region-bound vanilla and MythicMobs spawning with nearby-player checks and alive limits.
- PVP protection through `peaceful` and `dangerous` region statuses, checking both players at the boundary.
- Built-in module status and an optional external module API. External loading is disabled by default.
- Improved discovery actions, entry conditions, editor feedback and bilingual documentation.

This release also fixes repeat-entry behavior, effects from cancelled or redirected movement, full-inventory reward loss, and Shift-click item loss in menus. Region edits preserve their source filenames; deleting a region removes duplicate definitions. Spawn reload retains live counts and disabled rules clean up their mobs, including deferred cleanup for unloaded chunks. External modules require enabled dependencies.

The latest 1.0.0 JAR also fixes repeated `ChunkUnloadEvent` errors on Paper 1.21.8.

After upgrading:

1. Run `/ws validate` to check region files.
2. Run `/ws modules list` to check module status.
3. If you use spawning, run `/ws spawn test <rule-id>` once for each important rule.
4. If town PVP should be blocked, stand in town and run `/ws protect test`.
