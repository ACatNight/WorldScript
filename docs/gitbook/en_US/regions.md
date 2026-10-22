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
/ws polygon preview
/ws polygon finish
```

Start with an existing region, then use the polygon tool to place its outline points. Preview them before finishing. `/ws polygon undo` and `/ws polygon redo` are available while editing. The region uses the outline plus its height range for detection.

## Parent-Child Regions

Use parent regions for shared ambience, variables, and statuses. Use child regions for precise triggers. A child must be in the same world and inside its parent's bounds.

```yaml
identity:
  parent: whispering_forest
state:
  inherit: true
```

Child settings can override inherited values. Event-level `inherit: false` lets a child define its own actions. Unlock and completion progress is stored separately for each player; inheriting region configuration does not merge their progress.

## Entry Conditions

If entry conditions fail, the player cannot enter and failure actions can be executed.

