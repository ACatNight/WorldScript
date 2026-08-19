# WorldScript | RPG Region Events

**Open-world RPG region editor, events, particles, and external progression for Paper servers.**

WorldScript turns a location into a reusable gameplay unit. Create a region, add events and actions, and let the world respond when a player enters, leaves, or interacts with a block.

It is built for servers that already have their own quest, combat, economy, NPC, or HUD plugins. WorldScript manages the region layer instead of replacing those systems.

## Screenshots

The in-game editor uses a chat-based layout with clear sections, clickable values, previews, and chat input as a fallback.

<!-- 中文注释：这是给你编辑文档时看的提示。发布到 SpigotMC 前，请把下面 5 个图片链接替换成实际网络图片链接，并删除所有“中文注释”和“请替换”文字。 -->

<!-- 中文注释：图片 1 放在最顶部，展示玩家进入区域后看到的标题效果，作为资源页主视觉图。 -->
<!-- 中文注释：将“图片 1 的网络链接”替换成你上传后的图片地址。 -->
[IMG]图片 1 的网络链接[/IMG]

<!-- 中文注释：图片 3 放在这里，展示区域编辑首页，包括区域状态、父区域、子区域和继承信息。 -->
[IMG]图片 3 的网络链接[/IMG]

Suggested image order for the resource page:

1. Editor overview: region properties, parent region, child count, and inherited settings.
2. Events page: enter, leave, left-click, right-click, and interaction events.
3. Particle page: enable atmosphere, change particle type, preview, and adjust values.
4. Sound and action page: preview sounds, change volume and pitch, and manage action parameters.
5. Title action page: edit title, subtitle, fade-in, stay, and fade-out values.

## Features

- Parent and child regions for large open-world maps
- Inherited variables, statuses, events, and particle atmosphere
- Region statuses: open, locked, dangerous, and peaceful
- Enter, leave, left-click, right-click, and block interaction events
- Multiple actions in one event
- Messages, titles, sounds, and particles
- Sound preview and volume/pitch adjustment
- Particle preview, count, interval, and spread adjustment
- Player and console commands
- Teleport actions
- Item, experience, and money rewards
- Player-specific unlock, entry, and completion progress
- PlaceholderAPI values for region and HUD displays
- Kether script actions through bundled TabooLib modules
- In-game chat editor with English, Simplified Chinese, and Traditional Chinese
- YAML validation with `/ws validate`

## How It Works

```text
Region
|- Properties and status
|- Variables
|- Events
|  |- Enter
|  |- Leave
|  |- Left click
|  |- Right click
|  `- Interaction
|- Actions
`- Particle atmosphere
```

An event can contain more than one action. For example, entering a region can send a message, show a title, play a sound, spawn particles, and call an external command in sequence.

## Parent and Child Regions

Use a parent region for a large area and child regions for individual locations:

```text
forest
|- forest-entrance
|- hunter-camp
|- wolf-den
`- hidden-cave
```

The parent can provide shared atmosphere, variables, status, and event behaviour. A child region inherits those settings and can override them when it needs different content.

## In-game Editor

<!-- 中文注释：如果上面的图片 3 没有放在截图总览区域，也可以把图片 3 放在本章节标题下面。不要重复插入。 -->

Open the editor with:

```text
/ws edit <region-id>
```

The editor provides:

- Breadcrumb-style region and page context
- Separate sections for properties, data, variables, events, and particles
- Clickable buttons for toggles, selection, preview, and value changes
- Sound selection with listen, previous, and next controls
- Particle selection with preview, previous, and next controls
- Numeric steppers for volume, pitch, count, and interval
- Chat input for text, commands, Kether, and custom values
- Multiple actions per event

The editor changes the same YAML data used by the plugin. YAML remains available for advanced configuration and version control.

## Events

<!-- 中文注释：图片 5 放在这里，展示 Enter、Leave、Left-click、Right-click 和 Interaction 事件列表。 -->
[IMG]图片 5 的网络链接[/IMG]

Supported event types:

- Enter a region
- Leave a region
- Left-click a block inside a region
- Right-click a block inside a region
- Interact with a block inside a region

Events can be enabled or disabled and can contain multiple actions.

## Actions

<!-- 中文注释：图片 2 放在这里，展示音效试听、Previous、Next、Volume 和 Pitch 参数。 -->
[IMG]图片 2 的网络链接[/IMG]

<!-- 中文注释：图片 4 放在粒子章节位置；如果你希望按功能顺序展示，可将图片 4 移到下方的 Particle Atmosphere 小节。 -->
[IMG]图片 4 的网络链接[/IMG]

Available action types include:

- Chat message
- Title and subtitle
- Sound
- Particles
- Player command
- Console command
- Teleport
- Item reward
- Experience reward
- Money reward
- Player variable
- Region status
- Unlock region
- Complete region
- Kether script

## External Plugins

WorldScript does not create quest definitions or quest steps. Use your existing quest or content plugin for the gameplay process, then call WorldScript when a milestone is reached:

```text
/ws progress <player> <region-id> unlock
/ws progress <player> <region-id> complete
```

This keeps region feedback and player progress separate from quest, combat, level, reputation, and economy systems.

## PlaceholderAPI

With PlaceholderAPI installed, these values can be used in HUDs, scoreboards, tab lists, and other supported plugins:

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_region_role%
%worldscript_region_content_id%
%worldscript_parent_id%
%worldscript_parent_name%
%worldscript_child_id%
%worldscript_child_name%
%worldscript_region_depth%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
%worldscript_var_<key>%
%worldscript_region_var_<key>%
%worldscript_parent_var_<key>%
```

## Commands

```text
/ws wand
/ws create <region-id>
/ws delete <region-id>
/ws list
/ws info <region-id>
/ws edit <region-id>
/ws gui
/ws validate
/ws reload
/ws progress <player> <region-id> <unlock|complete>
```

Permission:

```text
worldscript.admin
```

## Installation

1. Download the latest WorldScript jar.
2. Put it in the server's `plugins` folder.
3. Start the server once.
4. Configure `plugins/WorldScript/config.yml` and the region files.
5. Run `/ws validate`.
6. Run `/ws reload`.

PlaceholderAPI is optional. TabooLib and Kether are included in the WorldScript jar and do not need a separate installation.

## Compatibility

- Paper 1.12.2 to 1.21.8
- Java 8 for Minecraft 1.12.2 to 1.16.x
- Java 17 for Minecraft 1.17 to 1.20.4
- Java 21 for Minecraft 1.20.5 and newer
- PlaceholderAPI is optional

The compatibility target is broad, but server owners should test their exact Paper build before using the plugin in production.

## Links

- Source code: https://github.com/ACatNight/WorldScript
- Documentation: https://github.com/ACatNight/WorldScript/tree/main/docs
- Discord: https://discord.gg/5NkEuBR6hV

## License

WorldScript is free and open source under the MIT License.
