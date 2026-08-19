# WorldScript

WorldScript is a region scripting plugin for Paper 1.12.2 through 1.21.8.

中文文档：[README-zh_CN.md](README-zh_CN.md)

## License

WorldScript is currently fully free and available under the [MIT License](LICENSE).

## Features

- Parent and child regions with inherited variables, statuses, and scripts
- Optional region atmosphere particles with child-region overrides
- Enter, leave, and block interaction events
- Per-player unlock, first-entry, completion, and one-time reward state
- Conditions, actions, rewards, and PlaceholderAPI variables
- Selection tool, region list, chat editor, and configuration validation
- Embedded TabooLib 6.3.0 runtime with Kether script actions
- Kether context variables for player and region data

## Install

1. Put the jar in the server `plugins` directory.
2. Start the server once.
3. Add region files to `plugins/WorldScript/regions/`.
4. Run `/ws validate`, then `/ws reload`.

The open-world starter file uses the recommended Schema 2 format: [examples/region-progression-template.yml](examples/region-progression-template.yml). Existing flat region files remain supported.
The complete five-region sample is available in [examples/regions](examples/regions).

## Commands

```text
/ws wand
/ws create <name>
/ws delete <name>
/ws list
/ws info <name>
/ws edit <region-id>
/ws edit <region-id> events
/ws gui
/ws reload
/ws validate
/ws progress <player> <region> <unlock|complete>
```

`/ws progress` is intended for external plugins to update a player's region progress. WorldScript does not manage quest definitions or quest steps.

## Placeholders

With PlaceholderAPI: `%worldscript_region_id%`, `%worldscript_region_name%`, `%worldscript_region_role%`, `%worldscript_region_content_id%`, `%worldscript_parent_id%`, `%worldscript_parent_name%`, `%worldscript_child_id%`, `%worldscript_child_name%`, `%worldscript_region_depth%`, `%worldscript_region_unlocked%`, `%worldscript_region_entered%`, `%worldscript_region_completed%`, and `%worldscript_region_world%`. Custom region variables are available as `%worldscript_var_<key>%`.

## Documentation

- [Chinese Wiki](docs/wiki-zh_CN.md)
- [English Wiki](docs/wiki.md)
- [Modrinth description](docs/modrinth-description.md)
- [Spigot description](docs/spigot-description.md)
- [Chinese configuration reference](docs/config-reference-zh_CN.md)
- [Chinese integration guide](docs/integration-zh_CN.md)
- [Chinese README](README-zh_CN.md)

## Compatibility

- Paper 1.12.2 through 1.21.8 is the compatibility target. Runtime smoke tests are still required for each server line before production use.
- Java 8 for 1.12.2-1.16.x, Java 17 for 1.17-1.20.4, and Java 21 for 1.20.5-1.21.8
- PlaceholderAPI is optional
- TabooLib and Kether are bundled in the WorldScript jar; no separate installation is required

The default language is `en_US`. Set `language: zh_CN` or `language: zh_TW` in `plugins/WorldScript/config.yml` and run `/ws reload` to use Chinese messages.

## Build

```text
gradlew.bat clean build
```

The jar is written to `build/libs/WorldScript-<version>.jar`.
