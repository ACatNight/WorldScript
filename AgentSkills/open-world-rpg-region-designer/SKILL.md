---
name: open-world-rpg-region-designer
description: Design and audit open-world RPG regions, exploration loops, progression gates, parent-child area structure, player state, HUD variables, and external quest-plugin integration. Use when reviewing or creating region chains, region configuration templates, unlock pacing, area identity, or WorldScript gameplay decisions; do not use it to implement a competing quest system.
---

# Open-World RPG Region Designer

Use this skill to keep region design player-facing, coherent, and appropriate for an open-world RPG. Treat a region as a promise of gameplay, not merely as a rectangle that fires an event.

## Review Workflow

1. Identify the intended world loop: exploration, quests, factions, resources, combat, or a deliberate mix.
2. Classify each area by player purpose: open zone, point of interest, danger area, hub, or progression gate.
3. Give every area one primary promise, one primary activity, one readable risk, and one reason to return.
4. Check that the world is a graph of choices rather than a single queue of locked regions. Prefer free access, soft danger, clues, reputation, gear, or external quest state before hard locks.
5. Review first entry, repeat entry, completion, reward, and next-step feedback as separate behaviors.
6. Review parent-child inheritance. Use parents for shared ambience and rules; keep decisive story, rewards, and progression in the smallest meaningful child area.
7. Review state scope explicitly: global, per-player, or party. Do not silently mix scopes.
8. Review HUD data. Define current, parent, child, depth, unlock, and completion semantics before naming placeholders.
9. Separate design findings from implementation findings. Keep external quest plugins responsible for quests; keep WorldScript responsible for spatial triggers, player-region state, feedback, and unlock signals.
10. End with one focused next slice. Do not recommend a broad feature list when a single playable loop is not yet proven.

## Open-World Rules

- Avoid making every region a mandatory step in a linear chain.
- Prefer hubs with two or three meaningful regional choices and later convergence.
- Use hard locks sparingly. If an area is dangerous rather than impossible, let the player see that danger.
- Give locked areas a reason, hint, or alternative route.
- Make each region visually, mechanically, or narratively distinct.
- Keep first-entry content short and purposeful; reserve repeat-entry behavior for utility, discovery, refreshable rewards, or changing world state.
- Make one-time rewards and repeatable rewards visibly different.
- Do not hide a required condition inside inherited parent scripts.
- Do not add a feature merely because the configuration model can express it.

## WorldScript Contract

Read [worldscript-context.md](references/worldscript-context.md) when reviewing this repository. Preserve these boundaries:

- Do not read or apply Germ specifications to this Paper plugin.
- Do not add custom quest definitions, quest persistence, or quest progression logic.
- Use Chemdah or another dedicated plugin for quests, then accept an explicit external result.
- Treat `/ws progress <player> <region> <unlock|complete>` as a narrow state bridge, not a quest engine.
- Treat parent-region names and child-region names as HUD data with deterministic nesting semantics.

## Review Output

Return the following in order:

1. Product judgement: whether the current structure feels like an open world.
2. Findings: concrete design risks, ordered by player impact.
3. Recommended region graph: hubs, branches, optional areas, and convergence points.
4. Region card: promise, activity, risk, entry rule, external content, completion signal, unlock result, first-entry behavior, and repeat behavior.
5. Configuration impact: what should be YAML, GUI, validation, HUD placeholders, or an external plugin.
6. One next implementation slice with a clear success test.

Use concrete examples, call out assumptions, and flag any choice that changes the player loop. Do not bury the main design decision under implementation details.
