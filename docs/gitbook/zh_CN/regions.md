# 区域系统

区域就是地图上的一块地方。玩家进出这块地方时，WorldScript 才知道该触发什么。

## 普通区域

普通区域是一个立方体，用两个角点确定范围。

适合：

- 房间
- 广场
- 主城
- 副本入口
- 比较方正的建筑

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

多边形适合不规则地方，比如斜路、山道、城墙边、弯曲入口。

```text
/ws polygon start <区域ID>
/ws polygon preview
/ws polygon finish
```

开始编辑后用多边形工具放置顶点，执行 `/ws polygon preview` 预览，确认后 `/ws polygon finish` 保存。编辑中可以用 `/ws polygon undo` 和 `/ws polygon redo` 撤销或重做。多边形仍受区域高度范围限制。

如果只是普通房间，别硬用多边形。普通选区更稳，也更好维护。

## 父子区域

父区域是大范围，子区域是里面的小点位。子区域必须与父区域同世界，边界也必须位于父区域内。

例子：

```text
低语森林
├─ 森林入口
├─ 古树祭坛
└─ 狼王洞穴
```

父区域放通用氛围，子区域放具体事件。

```yaml
identity:
  parent: whispering_forest
state:
  inherit: true
```

子区域可以覆盖父区域的变量、粒子和事件。某个事件设为 `inherit: false` 后，可以定义自己的动作。解锁和完成进度仍按玩家分别保存，区域配置继承不会合并玩家进度。

## 进入条件

条件没达到，玩家就进不去。

比如必须完成 `forest_gate`：

```yaml
conditions:
  - type: player_region_status
    key: forest_gate
    value: completed
```

失败时可以配置提示动作，比如发消息、播放音效、弹 Title。
