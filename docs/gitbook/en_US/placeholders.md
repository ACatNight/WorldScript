# PlaceholderAPI

With PlaceholderAPI installed, WorldScript registers `%worldscript_*%` placeholders.

## Common Placeholders

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

## Custom Region Variables

If a region has:

```yaml
variables:
  biome: forest
```

Use:

```text
%worldscript_biome%
%worldscript_region_var_biome%
%worldscript_parent_biome%
%worldscript_child_biome%
```

## Test

```text
/papi parse me %worldscript_region_name%
```

If the placeholder is returned unchanged, make sure the display plugin supports PlaceholderAPI.

