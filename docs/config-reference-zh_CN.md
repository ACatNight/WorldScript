# WorldScript 配置参考

区域文件位于 `plugins/WorldScript/regions/`。一个文件对应一个区域；修改后执行 `/ws validate`，确认无错误后再执行 `/ws reload`。

## Schema 2

新区域推荐使用 `schema: 2`。文件按身份、位置、状态、变量和事件组织；旧的平铺字段仍可读取。

```yaml
schema: 2
id: sunken_ruins
identity:
  name: 沉没遗迹
  role: point_of_interest
  content-id: ruins_intro
  parent: whispering_forest
location:
  world: world
  world-id: world
  priority: 0
  min: {x: 32, y: 50, z: 80}
  max: {x: 120, y: 100, z: 150}
state:
  inherit: true
  statuses: [dangerous]
variables:
  biome: ruins
events:
  enter:
    enabled: true
    inherit: false
    mode: first
    cooldown-seconds: 0
    conditions: []
    actions:
      - type: message
        value: "&6发现了沉没遗迹。"
    rewards: []
```

`identity.role` 可选值：`hub`、`open_zone`、`point_of_interest`、`danger_zone`、`gate`。

## 父子区域与状态

`identity.parent` 指向父区域 ID。`state.inherit: true` 时，子区域会继承父区域的变量、状态和事件；事件只有在 `inherit: false` 时才使用子区域自己的同类事件。

`state.statuses` 只表示全服共享状态：`locked`、`open`、`dangerous`、`peaceful`。玩家解锁、进入、完成状态不应写在这里。

## 事件

区域支持 `enter`、`leave`、`interact`。`interact` 只在玩家主手右键方块且事件没有被其他插件取消时触发。

`mode` 可选值：`always`、`first`、`repeat`。`first` 只在玩家第一次进入时执行，`repeat` 跳过第一次进入。旧配置中的 `first-entry-only`、`repeat-entry-only` 和 `override-parent` 仍然兼容。

## 条件

支持：`permission`、`item`、`variable`、`region_status`、`player_region_status`。玩家区域状态可用：`unlocked`、`entered`、`completed`。使用 `region.变量名` 可读取当前区域及其父级继承变量。

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

`set_region_status` 格式为 `区域ID,全局状态`，例如 `danger_canyon,open`。拼写错误、未知类型、未知状态、未知比较符和无效坐标都会由 `/ws validate` 报告。
