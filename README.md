# WorldScript

WorldScript is a region scripting plugin for Paper 1.21.8 servers.

中文文档：[README-zh_CN.md](README-zh_CN.md)

## License

WorldScript is currently fully free and available under the [MIT License](LICENSE).

## Features

- Parent and child regions with inherited variables, statuses, and scripts
- Enter, leave, and block interaction events
- Per-player unlock, first-entry, completion, and one-time reward state
- Conditions, actions, rewards, and PlaceholderAPI variables
- Selection tool, admin commands, GUI, and configuration validation

## Install

1. Put the jar in the server `plugins` directory.
2. Start the server once.
3. Add region files to `plugins/WorldScript/regions/`.
4. Run `/ws validate`, then `/ws reload`.

The open-world starter file is [examples/region-progression-template.yml](examples/region-progression-template.yml).

## Commands

```text
/ws wand
/ws create <name>
/ws delete <name>
/ws list
/ws info <name>
/ws gui
/ws reload
/ws validate
/ws progress <player> <region> <unlock|complete>
```

`/ws progress` is intended for external plugins to update a player's region progress. WorldScript does not manage quest definitions or quest steps.

## Placeholders

With PlaceholderAPI: `%worldscript_region_name%`, `%worldscript_parent_name%`, `%worldscript_child_name%`, `%worldscript_region_role%`, `%worldscript_region_content_id%`, `%worldscript_region_depth%`, `%worldscript_region_unlocked%`, `%worldscript_region_entered%`, `%worldscript_region_completed%`, and `%worldscript_region_world%`.

## Documentation

- [Chinese configuration reference](docs/config-reference-zh_CN.md)
- [Chinese integration guide](docs/integration-zh_CN.md)
- [Chinese README](README-zh_CN.md)

## Compatibility

- Paper 1.21.8
- Java 21
- PlaceholderAPI is optional

The default language is `en_US`. Set `language: zh_CN` in `plugins/WorldScript/config.yml` and run `/ws reload` to use Chinese messages.

## Build

```text
gradlew.bat clean build
```

The jar is written to `build/libs/WorldScript-<version>.jar`.
