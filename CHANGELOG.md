# Changelog

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
