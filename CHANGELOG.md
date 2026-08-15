# Changelog

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
