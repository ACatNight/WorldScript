# WorldScript

WorldScript 是一个面向 Paper 1.21.8 的区域脚本插件。它把地图中的区域变成可配置的游戏内容节点，负责区域识别、父子继承、世界状态、玩家区域进度和事件反馈。

## 核心定位

WorldScript 不负责创建或管理任务。任务过程应交给服务器已有的任务插件，例如 Chemdah；WorldScript 只负责：

- 判断玩家当前位于哪个区域
- 组织父区域与子区域的继承关系
- 维护共享的世界状态和玩家区域进度
- 在进入、离开、交互时执行配置好的动作
- 根据外部插件回写结果解锁区域或标记完成
- 向 HUD 提供当前区域及父子区域变量

等级、声望和完整任务系统暂不内置，避免与其他插件重复维护同一份数据。

## 开放世界设计

区域不是必须按顺序通关的关卡，而是地图上的玩法节点。例如：

```text
中心据点
├─ 低语森林       采集、猎人内容、野外探索
├─ 废弃矿区       资源、矿洞和工匠内容
├─ 古代遗迹       探索、解谜和世界线索
└─ 危险峡谷       高风险区域，作为软门槛内容
```

建议每个区域只设置一个主要进入门槛。开放世界优先使用“可以进入但很危险”的软门槛，只有剧情封锁、特殊道具或明确的世界状态才使用完全锁定。

推荐把区域分成三层：

1. 父区域：提供环境提示、通用变量和通用规则。
2. 子区域：负责具体地点的进入、离开、交互和奖励。
3. 外部内容插件：负责任务、战斗、NPC 或其他专业玩法。

## 安装

1. 将 `build/libs/WorldScript-<版本>.jar` 放入服务器的 `plugins/` 目录。
2. 启动一次服务器，让插件生成 `plugins/WorldScript/regions/`。
3. 将 `examples/region-progression-template.yml` 复制到 `regions/` 作为开放世界示例。
4. 按服务器实际地图修改世界名、区域边界和外部插件命令。
5. 先执行 `/ws validate`，确认无错误后再执行 `/ws reload`。

新安装默认使用英文语言文件。若要切换中文消息，在 `plugins/WorldScript/config.yml` 中设置：

```yaml
language: zh_CN
```

修改后执行 `/ws reload`。语言文件位于 `plugins/WorldScript/lang/`；自定义语言文件名称只能使用英文、数字、`_` 和 `-`。

## 区域配置思路

每个区域文件对应一个区域。子区域可以通过 `parent-id` 继承父区域配置；只有显式配置的内容才覆盖父区域，避免新增默认值意外改变旧区域行为。

```yaml
id: ancient_ruins
name: 被遗忘的古代遗迹
world: world
min: {x: 120, y: 50, z: -80}
max: {x: 240, y: 120, z: 40}
parent-id: whispering_forest
role: point_of_interest
content-id: chemdah:ancient_ruins
statuses: []
variables:
  display_name: 被遗忘的古代遗迹
  parent_display_name: 低语森林
events:
  enter:
    first-entry-only: true
    actions:
      - type: message
        message: '&e你发现了被遗忘的古代遗迹。'
```

区域角色用于表达设计意图：`hub`、`open_zone`、`point_of_interest`、`danger_zone` 和 `gate`。`content-id` 只是外部内容的关联标识，不会让 WorldScript 自动创建任务。

父区域进入事件先执行，子区域进入事件后执行；离开时按相反顺序执行。处于 `locked` 状态的区域不会参与区域事件。

## 状态边界

共享世界状态适合描述所有玩家都能看到的世界变化：`locked`、`open`、`dangerous` 和 `peaceful`。

玩家区域进度只属于单个玩家：解锁、首次进入、完成和一次性奖励领取状态不会污染其他玩家，也不会改变世界的共享状态。

## 与任务插件协作

例如 Chemdah 接管任务流程时，可以采用以下边界：

```text
进入区域
→ WorldScript 显示地点信息或调用 Chemdah 的公开入口
→ Chemdah 管理任务接受、进行和完成
→ Chemdah 完成后执行 /ws progress <玩家> <区域> complete
→ WorldScript 更新该玩家的区域完成状态
```

命令格式：

```text
/ws progress <玩家> <区域> <unlock|complete>
```

这条命令的含义是“外部插件把结果通知给 WorldScript”，不是让 WorldScript 代替任务插件创建任务。已在服务器玩过的玩家即使当前离线，也可以被回写状态。插件集成还可以通过公开的 UUID 玩家进度服务完成回写。

## HUD 变量

安装 PlaceholderAPI 后，HUD 可以使用以下变量：

```text
%worldscript_region_name%
%worldscript_parent_name%
%worldscript_child_name%
%worldscript_region_role%
%worldscript_region_content_id%
%worldscript_region_depth%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
%worldscript_region_world%
```

其中 `region_name` 是当前有效区域名称，`parent_name` 是父区域名称，`child_name` 是当前区域的子区域名称；没有对应层级时返回空值。变量只表达区域信息和玩家区域进度，不包含等级或声望。

## 管理命令

```text
/ws wand                         获取区域选择工具
/ws create <名称>                使用选择范围创建区域
/ws delete <名称>                删除区域
/ws list                         列出区域
/ws info <名称>                  查看区域信息
/ws gui                          打开管理界面
/ws reload                       重新加载配置
/ws validate                     检查配置错误
/ws progress <玩家> <区域> <状态> 回写玩家区域状态
```

## 构建与验证

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

构建产物位于 `build/libs/WorldScript-<版本>.jar`。`check` 会自动执行项目内的纯逻辑检查。

## 当前明确不做的内容

- 不读取或依赖 Germ/GermEngine
- 不内置任务定义、任务步骤或任务数据库
- 不内置等级和声望系统
- 不把地图编辑器变成剧情编辑器或任务编辑器

未来如果制作可视化编辑器，它的职责应保持为：在地图上选择已有区域，并编辑区域边界、名称、继承关系、变量和脚本；核心插件仍保持轻量、可组合、可由外部插件扩展。

## 许可证

WorldScript 免费核心使用 [MIT License](LICENSE)。未来的可视化 Pro 编辑器可以独立发布，并使用单独的商业授权条款。
