# WorldScript 配置参考

区域文件位于 `plugins/WorldScript/regions/`。一个文件对应一个区域；修改后执行 `/ws validate`，确认无错误后再 `/ws reload`。

## 区域定义

```yaml
id: sunken_ruins
display-name: "沉没遗迹"
world-name: world
world-id: world
role: point_of_interest
content-id: ruins_intro
parent-id: whispering_forest
inherit-parent: true
priority: 0
min: {x: 32, y: 50, z: 80}
max: {x: 120, y: 100, z: 150}
statuses: [dangerous]
variables:
  biome: ruins
```

`role` 可选值：`hub`、`open_zone`、`point_of_interest`、`danger_zone`、`gate`。

全局 `statuses` 只表示全服共享世界状态：`locked`、`open`、`dangerous`、`peaceful`。`unlocked` 仍会兼容读取为 `open`。不要把玩家完成状态写入这里。

## 事件

区域支持 `enter`、`leave`、`interact`。`interact` 只在玩家主手右键方块、且事件没有被其他插件取消时触发。

```yaml
events:
  enter:
    enabled: true
    first-entry-only: true
    cooldown-seconds: 0
    override-parent: true
    actions:
      - type: message
        value: "&6发现了沉没遗迹。"
    conditions: []
    rewards: []
```

`first-entry-only` 与 `repeat-entry-only` 不能同时为 `true`。子区域只有显式 `override-parent: true` 时才覆盖父级同类事件。

## 条件

支持：`permission`、`item`、`variable`、`region_status`、`player_region_status`。

`player_level` 已禁用；旧配置使用它时，`/ws validate` 会报错。

```yaml
conditions:
  - type: player_region_status
    key: forest
    value: completed
  - type: variable
    key: chapter
    operator: greater_or_equal
    value: "2"
  - type: item
    key: TRIPWIRE_HOOK
    amount: 1
```

玩家区域状态可用：`unlocked`、`entered`、`completed`。区域变量使用 `key: region.变量名` 读取当前区域及其父级继承变量。

## 动作与奖励

动作 `type`：`message`、`player_command`、`console_command`、`teleport`、`set_variable`、`set_region_status`、`give_item`、`give_experience`、`give_money`、`unlock_region`、`complete_region`。

奖励 `type`：`item`、`experience`、`money`、`command`、`message`、`set_variable`、`set_region_status`、`unlock_region`、`complete_region`。奖励可使用 `once: true`，确保每个玩家只领取一次。

```yaml
actions:
  - type: console_command
    value: "your-quest-plugin start ruins_intro %player%"
  - type: unlock_region
    value: hidden_cave
rewards:
  - type: complete_region
    value: sunken_ruins
  - type: message
    value: "&a遗迹探索完成。"
    once: true
```

`set_region_status` 格式为 `区域ID,全局状态`，例如 `danger_canyon,open`。它会影响全服。`unlock_region` 和 `complete_region` 只影响触发玩家。

遇到拼写错误、未知类型、未知状态、未知比较符或无效坐标时，`/ws validate` 会报告区域文件与字段位置；请先修复，不要依赖默认值继续上线。
