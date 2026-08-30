# WorldScript Wiki

WorldScript 用来给 Paper 服务器配置区域。一个区域就是一块范围，可以有名称、父区域、状态、变量、粒子和事件动作。

它不创建任务，也不负责怪物、战斗或经济。Chemdah 等任务插件负责任务流程，WorldScript 负责地点事件和玩家区域进度。

## 1. 安装

1. 将 `WorldScript-<version>.jar` 放入服务器的 `plugins/`。
2. 启动服务器一次，生成 `plugins/WorldScript/`。
3. 将项目 `examples/` 中的示例复制到 `plugins/WorldScript/regions/`。
4. 修改世界名称、坐标、内容 ID 和外部插件命令。
5. 执行 `/ws validate`，没有错误后执行 `/ws reload`。

TabooLib 和 Kether 已包含在 WorldScript JAR 中，不需要另外安装 TabooLib。PlaceholderAPI 是可选依赖。

本文档按 `0.1.93` 编写。

## 3. 第一个区域

先用选区工具确定两个角点：

```text
/ws wand
```

手持工具左键和右键设置两个位置，然后创建区域：

```text
/ws create starter_valley
```

查看区域：

```text
/ws list
/ws info starter_valley
/ws list
```

在区域列表中，左键传送到区域中心，右键打开聊天编辑器。

聊天编辑器支持：

- 修改玩家看到的显示名称，不会改变内部区域 ID。
- 在“区域变量”页面添加和修改本区域变量。
- 查看变量是“本区域”还是“继承自父区域”。
- 删除本地变量覆盖时要求输入“确认”。

## 4. 区域结构

推荐使用 Schema 2：

```yaml
schema: 2
id: forest_entrance
identity:
  name: 森林入口
  role: point_of_interest
  content-id: forest_intro
  parent: whispering_forest
location:
  world: world
  world-id: world
  priority: 0
  min: {x: 0, y: 60, z: 0}
  max: {x: 40, y: 90, z: 40}
state:
  inherit: true
  statuses: [open]
variables:
  short_name: 森林入口
events: {}
```

区域角色：`hub`、`open_zone`、`point_of_interest`、`danger_zone`、`gate`。

父区域通过 `identity.parent` 指定。父区域适合放通用环境和规则，关键剧情放在具体子区域中。子区域明确配置同类内容后，会以本地配置覆盖父区域对应内容。

## 5. 事件和动作

常用事件：

- `enter`：玩家进入区域
- `leave`：玩家离开区域
- `interact`：玩家主手右键方块

常用进入模式：`always`、`first`、`repeat`。

示例：

```yaml
events:
  enter:
    enabled: true
    mode: first
    actions:
      - type: message
        value: "&6你发现了森林入口。"
      - type: sound
        sound: BLOCK_PORTAL_TRIGGER
        volume: 1.0
        pitch: 0.9
```

内置动作包含消息、音效、玩家命令、控制台命令、传送、变量、区域状态、物品、经验、金钱、解锁和完成区域。简单逻辑优先用这些动作；只有需要等待、分支或连续脚本时才使用 Kether。

## 6. 外部任务插件

WorldScript 不创建任务，也不维护任务步骤。外部任务插件完成任务后，可以执行：

```text
/ws progress 玩家名 区域ID unlock
/ws progress 玩家名 区域ID complete
```

也可以通过 API 写入 UUID 对应的玩家状态。外部插件异步回调后，必须切回服务器主线程再调用 API。

## 7. HUD 变量

安装并启用 PlaceholderAPI 后可使用。若 PlaceholderAPI 是在 WorldScript 之后才安装或启用的，请启用后执行 `/ws reload`；如果两者同时启动，WorldScript 会自动注册扩展。

如果占位符仍原样显示，请确认承载它的计分板、Tab、聊天或 HUD 插件本身支持 PlaceholderAPI，并用 `/papi parse me %worldscript_region_id%` 检查。WorldScript 会在日志中记录扩展是否注册；如果存在重复的 `worldscript` 标识符，也会输出注册警告。

安装 PlaceholderAPI 后可使用：

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_region_role%
%worldscript_region_content_id%
%worldscript_parent_id%
%worldscript_parent_name%
%worldscript_child_id%
%worldscript_child_name%
%worldscript_region_path%
%worldscript_region_depth%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
```

`variables:` 下的自定义变量会被自动识别，不需要预先注册：

```text
%worldscript_var_short_name%
%worldscript_region_var_short_name%
%worldscript_parent_var_biome%
%worldscript_short_name%
```

为了让 HUD 配置更简洁，也可以使用 `%worldscript_parent_<key>%` 和 `%worldscript_child_<key>%`。其中 `child` 表示玩家当前所在的最深层区域。

把 `short_name` 和 `biome` 换成配置中的实际键名。当前区域变量也可以直接使用 `%worldscript_<key>%`；子区域覆盖父区域同名变量，父区域变量使用 `parent_var_`。不存在的变量返回空字符串。`region_path` 会按 `config.yml` 中的 `placeholders.region-name-format` 返回显示名称路径。

区域路径格式支持：

```yaml
placeholders:
  region-name-format: '当前位置：{parent} / {current}'
```

可用变量为 `{parent}`、`{current}`、`{child}`、`{id}` 和 `{path}`。没有父区域时，默认的父级分隔符会自动去除。

### 占位符测试

玩家站在目标区域内执行：

```text
/papi parse me %worldscript_region_name%
/papi parse me %worldscript_region_path%
/papi parse me %worldscript_short_name%
/papi parse me %worldscript_parent_biome%
```

如果父区域是“低语森林”、子区域是“森林入口”，默认结果为：

```text
低语森林 / 森林入口
```

如果显示占位符原文，请确认 PlaceholderAPI 已安装，并检查 HUD、Tab、计分板或聊天插件是否支持 PlaceholderAPI。

## 8. 粒子氛围

粒子是区域氛围，不是高频提示。建议普通区域使用较低数量和较长间隔，传送门、入口和重要兴趣点再使用明显样式。

```yaml
particle:
  enabled: true
  preset: PORTAL
  type: END_ROD
  count: 2
  interval-ticks: 20
  spread: {x: 1.5, y: 0.8, z: 1.5}
  speed: 0.0
```

同一时间只显示玩家所在最深区域的粒子，子区域可以覆盖父区域效果，避免嵌套区域重复生成。

## 9. 常用命令

```text
/ws help
/ws wand
/ws create <区域ID> [显示名称]
/ws delete <区域ID>
/ws list
/ws info <区域ID>
/ws list
/ws edit <区域ID>
/ws reload
/ws validate [区域ID]
/ws progress <玩家> <区域ID> <unlock|complete>
/ws modules list
/ws modules info <模块ID>
/ws modules enable <模块ID>
/ws modules disable <模块ID>
```

## 10. 模块系统

WorldScript 从 0.1.0 模块系统开始，会在第一次启动时生成 `plugins/WorldScript/modules/`。当前默认生成官方基础模块描述 JAR：

```text
worldscript-core.jar
worldscript-editor.jar
worldscript-toast.jar
worldscript-atmosphere.jar
worldscript-rpg.jar
worldscript-placeholder.jar
```

当前这些基础功能仍由主插件内置运行，模块 JAR 主要用于模块识别、状态诊断和后续拆分。执行 `/ws modules list` 可以查看模块状态，执行 `/ws modules info toast` 可以查看单个模块详情。

模块配置位于 `plugins/WorldScript/settings/modules.yml`：

```yaml
auto-install-official: true
load-external: false
disabled: []
```

外置模块执行默认关闭。管理员明确设置 `load-external: true` 后，WorldScript 才会加载实现 `WorldScriptModule` 的外置模块入口。官方基础模块仍由主插件内置运行，`disabled` 不会关闭现有功能；外置模块可以通过 `disabled`、`/ws modules enable` 和 `/ws modules disable` 管理。

如果要开发自己的外置模块，可以参考项目模板：

```text
examples/modules/hello-worldscript-module/
```

## 11. 排错顺序

1. 先执行 `/ws validate [区域ID]`。
2. 检查区域文件是否放在 `plugins/WorldScript/regions/`。
3. 检查 `world`、`world-id` 和坐标范围。
4. 检查父区域是否与子区域处于同一个世界，且范围确实包含子区域。
5. 检查事件是否 `enabled: true`，以及 `mode` 和冷却时间。
6. 如果使用外部命令，先在控制台单独测试命令格式。
7. 如果使用 Kether，先用一条简单的 `message` 验证触发，再逐步增加脚本内容。
8. 如果模块相关功能异常，执行 `/ws modules list` 和 `/ws modules info <模块ID>` 查看状态。

## 12. 兼容性和构建

目标平台为 Paper 1.12.2 至 1.21.8。不同 Minecraft 版本需要对应 Java：1.12.2-1.16.x 使用 Java 8，1.17-1.20.4 使用 Java 17，1.20.5-1.21.8 使用 Java 21。发布前应在目标服务器版本进行实际冒烟测试。

构建：

```text
gradlew.bat clean build
```

项目使用 MIT License，欢迎提交 Issue、配置示例和 Pull Request。

## 13. 语言文件

语言文件位于插件目录的 `lang/`：

- `en_US.yml`：默认语言
- `zh_CN.yml`：简体中文
- `zh_TW.yml`：繁体中文

在 `config.yml` 中修改 `language` 后执行 `/ws reload`。自定义语言可以复制 `en_US.yml`，只修改等号右侧的文本，不要修改键名、占位符或颜色代码。新版本增加键时，缺少的键会回退到默认语言。
