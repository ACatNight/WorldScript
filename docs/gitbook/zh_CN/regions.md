# 区域系统

## 普通区域

普通区域使用两个角点组成一个立方体范围，适合房间、广场、山谷、建筑内部等规则形状。

```yaml
schema: 2
id: starter_valley
identity:
  name: 初始山谷
  role: open_zone
location:
  world: world
  min: {x: 0, y: 60, z: 0}
  max: {x: 40, y: 90, z: 40}
```

## 多边形区域

多边形区域适合斜路、城墙、山体边界、不规则建筑入口等场景。

```text
/ws polygon start <区域ID>
/ws polygon finish
```

编辑时使用多边形工具点选顶点。完成后保存，区域会使用多边形水平边界和高度范围判断玩家是否进入。

## 父子区域

父区域适合放大范围氛围、变量和通用状态。子区域适合放精确触发点和剧情事件。

```yaml
identity:
  parent: whispering_forest
state:
  inherit: true
```

子区域可以覆盖父区域的变量、粒子和事件。

## 进入条件

如果条件没达到，玩家进不去，并且可以执行失败提示动作。

```yaml
conditions:
  - type: player_region_status
    key: forest_gate
    value: completed
```

