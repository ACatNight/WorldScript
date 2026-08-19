# Changelog

## 0.1.91

- Fixed bStats startup failure by relocating the bundled bStats classes through TabooLib.

## 0.1.90

- Added bStats metrics with plugin ID `33524`.
- Documented the bStats opt-out configuration.

## 0.1.89

- Added explicit Chinese image placement comments and BBCode placeholders to the SpigotMC description.

## 0.1.88

- Removed built-in Chinese example text from new title and message actions.
- Added compatibility cleanup for the previous example values when loading existing region files.

## 0.1.87

- Added localized editor labels for title fade-in, stay, and fade-out parameters.
- Removed the remaining raw animation parameter names from the normal chat editor display.
- Reworked the SpigotMC description around the in-game editor, region events, and prepared screenshot order.

## 0.1.86

- Added a complete SpigotMC resource description with installation, commands, actions, placeholders, and compatibility details.
- Linked the SpigotMC description from the README.

## 0.1.85

- Fixed the chat editor crashing on Spigot 1.12.2 when opening `/ws edit`.
- Replaced the newer Bungee hover-text type with the legacy-compatible component API.

## 0.1.84

- Added a ready-to-paste Modrinth project description and linked it from the README.

## 0.1.83

- Localized action preset names in the chat editor instead of displaying the YAML name directly.
- Kept custom preset names as fallbacks and kept machine-readable action IDs visible.

## 0.1.82

- Added working PlaceholderAPI access for `variables` with `var_<key>`, `region_var_<key>`, and `parent_var_<key>`.
- Replaced the English Wiki with shorter, task-focused installation and configuration notes.
- Corrected the documented placeholder list to match the implementation.

## 0.1.81

- Moved region configuration validation into a dedicated validator with parent-boundary coverage.
- Moved chat editor menus, action labels, candidates, and session records into a dedicated editor catalog.

## 0.1.80

- Centralized Bukkit sound and particle compatibility lookups used by the editor, runtime actions, and region validation.
- Removed duplicate action placeholder expansion and unnecessary non-null assertions.
- Ignored local Adyeshach reference jars so they cannot be added to releases accidentally.

## 0.1.79

- Moved the remaining chat-editor controls, hover text, page names, parameter labels, and particle feedback into language files.
- Added English `cancel` and `confirm` input aliases while preserving the Chinese aliases.
- Added an English Wiki covering installation, configuration, events, integrations, HUD placeholders, and troubleshooting.

## 0.1.78

- Moved editor page groups, property labels, buttons, statuses, and modes into language files.
- Completed the localized editor labels used by the main region, event, action, sound, and particle pages.

## 0.1.77

- Moved editor feedback and event labels into the language files.
- Added language customization and upgrade fallback notes to the Chinese Wiki.

## 0.1.76

- Added editor input cleanup on page changes, player quit, reload, and close.
- Added a configurable editor input timeout.
- Documented left-click and right-click block events.

## 0.1.75

- Centralized editor action target parsing for input, deletion, sound, and region selection.
- Added action target parsing coverage to the built-in logic test runner.

## 0.1.74

- Centralized editor command and mutation routing in `EditorRoute`.
- Added route parsing coverage to the built-in logic test runner.

## 0.1.73

- Editing an inherited action now creates a local child-region override first.
- Added chat confirmation before deleting an action.
- Removed duplicate feedback from sound and region parameter updates.

## 0.1.72

- Cleaned configuration comments and example wording.
- Replaced mixed simplified/traditional text in the Traditional Chinese language file.
- Simplified the chat editor source comment.

## 0.1.71

- Added a user-facing Chinese Wiki for installation, configuration, integrations, and troubleshooting.
- Replaced the internal design-agent document with concise project design principles.
- Updated README feature wording to match the region list and chat editor workflow.

## 0.1.70

- Simplified the region list state and removed obsolete GUI holder fields.
- Centralized region ordering and click handling for easier maintenance.

## 0.1.69

- Replaced the multi-page inventory editor with a compact region list.
- Left-click a region to teleport; right-click it to open the chat editor.
- Added pagination and hover details for role, status, parent, world, and content.

## 0.1.68

- Added the complete Traditional Chinese language pack (`zh_TW`).
- Loads `zh_TW.yml` automatically on first startup.
- Fixed right-click interaction fallback to respect inherited parent event settings.

## 0.1.67

- Made automatic region enter and leave notifications configurable and disabled them by default.

## 0.1.66

- Changed editor micro-adjustments to use one-line feedback instead of full-page redraws.
- Preview actions no longer write configuration accidentally.

## 0.1.65

- Keeps editor fallback text in the selected language when upgrading from an older language file.

## 0.1.64

- Made the properties, data, and variables tabs open separate editor pages.
- Replaced dense toolbar separators with intentional spacing.

## 0.1.63

- Added deliberate spacing between the archive header, context, tabs, groups, and footer in the chat editor.

## 0.1.62

- Uses configured preset display names instead of exposing internal preset IDs in the editor.

## 0.1.61

- Moved the chat editor shell, tabs, toolbar, action labels, and colors into language files.
- Added a persistent `+ Add action` entry so events can contain multiple actions.
- Shows inherited parent actions in the event editor.

## 0.1.60

- Rebuilt the chat editor shell with Adyeshach-style archive header, tabs, toolbar, and footer navigation.
- Removed dense tree prefixes and section separators from property rows.

## 0.1.59

- Reworked the chat editor layout with a compact breadcrumb, friendly labels, and readable region bounds.
- Added targeted `/ws validate <region>` checks and diagnostics for duplicate IDs, invalid parent geometry, and particle presets.
- Refreshes online players immediately after an external region unlock.

## 0.1.58

- Rebuilt the chat editor around one consistent property-panel layout.
- Separated mutation commands from page rendering to prevent duplicate editor output.
- Added aligned sound, particle, event, action, and navigation controls.
- Fixed chat parameter editing returning to the wrong event page.

## 0.1.51

- Refined the chat editor into a breadcrumb-based region property panel with separated groups and full-width dividers.
- Added region identity context for world, bounds, parent, child count, role, and content ID.

## 0.1.50

- Added a separated breadcrumb-style chat editor layout for regions, events, actions, and parameters.
- Added in-game event toggles, trigger modes, cooldown adjustment, particle cycling, and particle count adjustment.
- Added sound selection, preview, volume/pitch adjustment, and region target cycling for parameterized actions.
- Made action preset YAML the source used by the chat editor and fixed parameterized action validation.
- Kept legacy `value` action syntax compatible with the new parameter map format.

## 0.1.17-SNAPSHOT

- Replaced the single-line `/ws` usage text with a structured command tree and added `/ws help`.

## 0.1.16-SNAPSHOT

- Added the recommended Schema 2 region format with grouped identity, location, state, variables, and events sections.
- Kept existing flat region files compatible and upgraded GUI-saved regions to Schema 2.
- Redesigned the admin GUI as an atlas with region, state, inheritance, event, and action cards.

## 0.1.15-SNAPSHOT

- Removed internal development artifacts and obsolete module placeholder documents.
- Simplified the project README files and kept detailed setup information in `docs/`.

## 0.1.14-SNAPSHOT

- Region transitions now handle cross-world movement and players joining inside a region.
- Corrected the Chinese README region configuration example to use the actual field names.

## 0.1.13-SNAPSHOT

- Clarified that WorldScript is currently fully free and released under the MIT License.

## 0.1.12-SNAPSHOT

- Added `en_US` as the default language configuration with fallback for missing language keys.
- Kept `zh_CN` available through the new `language` setting in `config.yml`.

## 0.1.11-SNAPSHOT

- Added a Chinese README covering the open-world region design, configuration boundaries, external quest integration, HUD placeholders, commands, and build workflow.

## 0.1.10-SNAPSHOT

- Licensed the free WorldScript core under MIT.

## 0.1.9-SNAPSHOT

- Interact scripts now run only for uncancelled main-hand right clicks on blocks.
- `/ws progress` can update a known offline player and the UUID-based `playerProgress` integration service is public.
- Configuration loading now records unknown roles, statuses, action types, condition types, reward types, operators, and invalid bounds for `/ws validate`.
- `check` now runs the project test runner automatically.
- Added Chinese configuration and integration references.

## 0.1.8-SNAPSHOT

- Separated shared world statuses from per-player region progress.
- Batched player state persistence off the gameplay path.
- Repaired Chinese language resources.
