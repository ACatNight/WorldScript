# PlaceholderAPI

With PlaceholderAPI installed, WorldScript can show the player's current region in HUDs, scoreboards, tabs, and chat.

## The Basic Idea

### Current Region

The most specific region the player is standing in.

If a player is inside both `Whispering Forest` and its child `Wolf Cave`, the current region is usually the deeper child region: `Wolf Cave`.

### Parent Region

The larger region outside the current one.

```text
Whispering Forest
└─ Wolf Cave
```

When the player is in `Wolf Cave`:

- Current region: `Wolf Cave`
- Parent region: `Whispering Forest`

### Region Variables

Region variables are custom values you put on a region.

```yaml
variables:
  biome: forest
  danger_level: "3"
  short_name: Wolf Cave
```

They do not change gameplay by themselves. They are labels and data that can be used by HUDs, scripts, conditions, and other plugins.

## Common Region Placeholders

These are built in. You do not need to define them under `variables:`.

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_region_role%
%worldscript_region_path%
%worldscript_parent_name%
%worldscript_child_name%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
```

Meaning:

- `%worldscript_region_id%`: current region ID, for example `wolf_cave`
- `%worldscript_region_name%`: current display name, for example `Wolf Cave`
- `%worldscript_region_role%`: region role, for example `danger_zone`
- `%worldscript_region_path%`: full path, for example `Whispering Forest / Wolf Cave`
- `%worldscript_parent_name%`: parent region name
- `%worldscript_child_name%`: deepest child region name
- `%worldscript_region_unlocked%`: whether this player unlocked the current region
- `%worldscript_region_entered%`: whether this player entered the current region before
- `%worldscript_region_completed%`: whether this player completed the current region

## Custom Region Variables

If a region has:

```yaml
variables:
  biome: forest
  danger_level: "3"
  short_name: Wolf Cave
```

Use:

```text
%worldscript_biome%
%worldscript_region_var_biome%
%worldscript_parent_biome%
%worldscript_child_biome%
```

Difference:

- `%worldscript_biome%`: short form; reads the effective current-region variable
- `%worldscript_region_var_biome%`: explicitly reads the current region variable
- `%worldscript_parent_biome%`: reads the parent region variable
- `%worldscript_child_biome%`: reads the deepest child region variable

Example:

```yaml
# Whispering Forest
variables:
  biome: forest
  danger_level: "1"

# Wolf Cave
identity:
  parent: whispering_forest
variables:
  danger_level: "5"
  short_name: Wolf Cave
```

When the player is in `Wolf Cave`:

```text
%worldscript_biome%          -> forest
%worldscript_danger_level%   -> 5
%worldscript_parent_biome%   -> forest
%worldscript_parent_danger_level% -> 1
%worldscript_short_name%     -> Wolf Cave
```

Child variables override parent variables with the same key. Missing child variables can inherit from the parent.

## Player Progress Placeholders

These are per-player values:

```text
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
```

Two players can see different values for the same region.

Common uses:

- Show whether the current area has been discovered
- Check whether a player has visited a story location
- Let a quest plugin unlock a region through `/ws progress`

## Test

```text
/papi parse me %worldscript_region_name%
/papi parse me %worldscript_region_path%
/papi parse me %worldscript_biome%
/papi parse me %worldscript_region_unlocked%
```

If this works but your HUD or scoreboard still shows raw placeholders, the display plugin probably is not parsing PlaceholderAPI.
