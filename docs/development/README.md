# Development notes

These documents describe design decisions and implementation planning. For current behavior, consult the [user documentation](../gitbook/README.md) and source code.

- [Design principles](design-principles-zh_CN.md)
- [Editor UX design](editor-ux-design-zh_CN.md)
- [Module system plan](module-system-plan-zh_CN.md)
- [Implementation requirements](update-requirements-zh_CN.md)

## Source layout

- `src/main/kotlin/com/worldscript/foundation/`: shared models, services and module contracts.
- `src/main/kotlin/com/worldscript/modules/`: region, progression, scripting and gameplay implementations.
- `src/main/kotlin/com/worldscript/command/`: commands and in-game editing.
- `src/main/kotlin/com/worldscript/integration/`: integrations.
- `src/main/resources/`: default settings, language files and plugin metadata.
- `src/test/`: regression and modern API compatibility tests.

Build and verification instructions are in [CONTRIBUTING.md](../../CONTRIBUTING.md).
