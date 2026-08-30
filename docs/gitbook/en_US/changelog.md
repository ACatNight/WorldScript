# Changelog

## 1.0.0

WorldScript is now ready to be used as a stable release.

If you only use region events, your existing workflow should stay the same. If you want a fuller exploration setup, the editor now gives you a clearer path for discovery prompts, Toasts, entry conditions, and region spawning.

Worth checking in this release:

- The `modules/` directory is created automatically, giving future extensions a cleaner home.
- Official built-in modules show up in `/ws modules list`, which makes feature status easier to diagnose.
- Old official module descriptor JARs are refreshed automatically, so you do not need to delete them by hand after upgrading.
- The Spawn module can bind mob spawning to regions. With MythicMobs installed, you can pick mobs directly from the GUI.
- The Protect module adds PVP control through region statuses such as `peaceful` and `dangerous`.
- The GitBook docs now include Chinese and English pages for installation, regions, polygons, Toasts, variables, and spawning.

After upgrading:

1. Run `/ws validate` to check region files.
2. Run `/ws modules list` to check module status.
3. If you use spawning, run `/ws spawn test <rule-id>` once for each important rule.
4. If you use PVP protection, stand in the region and run `/ws protect test`.
