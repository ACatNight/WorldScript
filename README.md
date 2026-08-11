# WorldScript

WorldScript is a Paper 1.21.8 region scripting plugin built with Kotlin and Gradle.

## Current Scope

- Parent and child regions with inherited variables, statuses, and event scripts
- Enter, leave, and interaction events
- Player variable persistence
- Conditions for levels, permissions, items, variables, and region statuses
- Actions and rewards for commands, messages, teleportation, items, experience, money, variables, and region unlocks
- Admin selection tool, commands, and GUI

Quest state and quest definitions are intentionally not implemented here. Quest behavior should be provided by the server's dedicated quest plugin, such as Chemdah, and invoked through that plugin's supported command or API.

## Build

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

The plugin jar is written to `build/libs/WorldScript-<version>.jar`.
