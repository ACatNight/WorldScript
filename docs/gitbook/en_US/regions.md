# Regions

## Cuboid Regions

Cuboid regions use two corners and work well for rooms, plazas, valleys, and rectangular buildings.

```yaml
schema: 2
id: starter_valley
identity:
  name: Starter Valley
  role: open_zone
location:
  world: world
  min: {x: 0, y: 60, z: 0}
  max: {x: 40, y: 90, z: 40}
```

## Polygon Regions

Polygon regions are better for diagonal roads, irregular entrances, walls, and terrain-shaped areas.

```text
/ws polygon start <region-id>
/ws polygon finish
```

Use the polygon tool to place points. The region uses the polygon outline plus its height range for detection.

## Parent-Child Regions

Use parent regions for shared ambience, variables, and global state. Use child regions for precise triggers.

```yaml
identity:
  parent: whispering_forest
state:
  inherit: true
```

## Entry Conditions

If entry conditions fail, the player cannot enter and failure actions can be executed.

