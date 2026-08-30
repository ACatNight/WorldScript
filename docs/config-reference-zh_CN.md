# WorldScript 配置参考

区域文件位于 `plugins/WorldScript/regions/`。一个文件对应一个区域；修改后执行 `/ws validate`，确认无错误后再执行 `/ws reload`。

新手可以先复制项目中的 `examples/01-simple-region.yml`。配置页面中的变量会显示“本区域”或“继承自父区域”，避免把继承值误认为本地值；变量也可以直接在 `/ws edit <区域ID> variables` 中添加、修改和移除本地覆盖。

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

区域氛围粒子默认关闭。开启后只显示当前玩家所在的最深区域粒子；子区域配置粒子时会覆盖父区域效果，避免多层区域叠加造成视觉噪声。

```yaml
particle:
  enabled: true
  type: END_ROD
  count: 2
  interval-ticks: 20
  spread:
    x: 1.5
    y: 0.8
    z: 1.5
  speed: 0.0
```

`count` 控制每次生成数量，`interval-ticks` 控制频率，`spread` 控制粒子范围，`speed` 控制粒子速度。建议开放世界普通区域使用低密度和较长间隔，重要地点再提高密度。

`state.statuses` 只表示全服共享状态：`locked`、`open`、`dangerous`、`peaceful`。玩家解锁、进入、完成状态不应写在这里。

## 事件

区域支持 `enter`、`leave`、`left-click`、`right-click` 和兼容用的 `interact`。`right-click` 与 `interact` 只在玩家主手右键方块且事件没有被其他插件取消时触发；`left-click` 用于左键方块。

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

动作 `type`：`kether`、`message`、`player_command`、`console_command`、`teleport`、`set_variable`、`set_region_status`、`give_item`、`give_experience`、`give_money`、`unlock_region`、`complete_region`。

推荐使用内置预设，只填写参数，不必直接编写 Kether：

```yaml
events:
  enter:
    actions:
      - preset: text-display
        title: '&b遗迹核心'
        subtitle: '&f发现了新的线索'
        fade-in: 20
        stay: 100
        fade-out: 20
      - preset: sound
        sound: BLOCK_PORTAL_TRIGGER
        volume: 1.0
        pitch: 0.8
```

当前内置预设包括 `text-display`、`message`、`sound`、`player-command`、`console-command`、`teleport`、`set-variable`、`set-region-status`、`give-item`、`give-experience`、`give-money`、`unlock-region` 和 `complete-region`。完整默认参数位于插件目录的 `presets/actions.yml`；复杂流程仍可使用 `type: kether`。

## 模块系统

模块配置位于 `plugins/WorldScript/settings/modules.yml`。WorldScript 1.0.0 会默认生成官方基础模块描述 JAR 到 `plugins/WorldScript/modules/`，升级时也会刷新旧版官方描述 JAR。

```yaml
auto-install-official: true
load-external: false
disabled: []
```

`auto-install-official` 控制缺失官方基础模块时是否自动生成。`load-external` 默认关闭，避免未知 JAR 在服务器上直接运行；只有明确改成 `true` 后，带入口类的外置模块才会执行。`disabled` 用于禁用外置模块；当前官方基础模块仍由主插件内置运行，不会关闭现有功能。

外置模块开发模板位于项目的 `examples/modules/hello-worldscript-module/`。

当前默认生成：

```text
worldscript-core.jar
worldscript-editor.jar
worldscript-toast.jar
worldscript-atmosphere.jar
worldscript-spawn.jar
worldscript-rpg.jar
worldscript-placeholder.jar
```

查看状态：

```text
/ws modules list
/ws modules info toast
/ws modules enable toast
/ws modules disable toast
/ws modules reload
```

## 刷怪模块

刷怪模块配置位于 `plugins/WorldScript/modules/spawn/config.yml`，不写入区域文件。推荐通过聊天栏编辑器管理：

```text
/ws edit <区域ID> spawn
```

默认生成逻辑：

- 绑定区域 ID。
- 在区域内随机刷新。
- 每次数量默认 `1~3`，硬上限默认 `10`。
- 刷新间隔默认 `30~60` 秒。
- 需要区域内或附近有玩家。
- 默认 8 格内不刷怪，避免刷脸。
- 默认检查安全落点。
- 默认最多存活 30 只。

如果安装 MythicMobs，可以在怪物选择 GUI 中直接点击 MM 怪物创建规则；没有安装时会显示原版怪物列表。

`kether` 用于复杂逻辑，脚本值可以使用 YAML 多行字符串。WorldScript 会自动提供 `player`、`uuid`、`region` 和 `world` 变量；玩家变量以 `ws_var_<变量名>` 提供，区域变量以 `ws_region_var_<变量名>` 提供。聊天编辑器适合短脚本，多行脚本请直接写入 YAML；简单消息、命令和状态更新优先使用原生动作，便于维护。

```yaml
actions:
  - type: kether
    value: |-
      tell "发现了新的线索"
      wait 20 tick
      tell "继续向遗迹深处探索"
```

标题示例：颜色代码写在文本中，不要写成 `title color "..."`。`by` 也可以写成 `with`：

```yaml
value: 'title "&b遗迹核心" subtitle "&f测试脚本" by 20 100 20'
```

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
