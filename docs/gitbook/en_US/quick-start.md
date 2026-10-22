# Quick Start

Start with one simple region. Once that works, add events, Toasts, particles, or spawning.

## 1. Get the Selection Tool

```text
/ws wand
```

The tool goes into your hand.

- Left-click: set the first point
- Right-click: set the second point

## 2. Create a Region

```text
/ws create starter_valley Starter Valley
```

`starter_valley` is the internal region ID. `Starter Valley` is the display name.

## 3. Open the Editor

```text
/ws list
/ws edit starter_valley
```

`/ws list` opens the region list GUI. `/ws edit` opens the chat editor for one region. Add an enter event or a discovery prompt there; most common changes do not require editing YAML.

## 4. Test

```text
/ws validate starter_valley
/ws test starter_valley
```

Walk out of the region and back in to check the event. `/ws test` reports the region shape, enter-event settings and validation issues, and sends a Toast preview; it does not execute the enter-event actions.

## 5. Try a 1.0.1 Feature

For an irregular outline, use [polygon editing](regions.md#polygon-regions). For a discovery notification, open `/ws edit starter_valley discovery` and [preview its Toast](discovery-toast.md). The [Spawn Module](spawn.md) and [Protect Module](protect.md) can be added when the basic region works.
