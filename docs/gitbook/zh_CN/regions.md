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
/ws polygon finish
```

大概流程是：开始编辑、拿工具点顶点、看粒子预览、确认没问题后保存。

如果只是普通房间，别硬用多边形。普通选区更稳，也更好维护。

## 父子区域

父区域可以理解成“大范围”，子区域是里面的“小点位”。

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

子区域可以覆盖父区域的变量、粒子和事件。

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
