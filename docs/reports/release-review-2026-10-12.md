# WorldScript 发布前复审（2026-10-12）

本报告记录修复前发现的问题。后续修复与验证结果见 [发布修复报告](release-fixes-2026-10-12.md)。

结论：暂不建议发布。审查对象为当前工作区，包含未提交修改；本轮未修改业务代码。

## 验证与边界

- 执行 `gradlew.bat clean build`，3 分 7 秒后失败于项目配置阶段：无法解析 `io.izzel.taboolib:common-env:6.3.0`、`common-platform-api`、`minecraft-kether` 等依赖。尚未进入本次编译或测试，不应表述为源码编译错误或测试通过。
- 现有 `build/libs/WorldScript-1.0.0.jar` 是此前产物，不代表当前工作区。旧交付报告中的成功构建记录不能代替本次证据。
- 使用 javap 核对本机 Spigot 1.12.2、Paper 1.20.1 和 Paper 1.21.8 API；并检查现有 JAR 的 GUI 字节码。
- 下列业务缺陷基于源码控制流，未进行真实服务器复现。没有验证 GUI 实际画面、MythicMobs 实服行为或全版本启动矩阵。

## 发布阻塞与高优先级问题

### B1：当前构建无法重现

位置：`build.gradle.kts` 的 TabooLib 6.3.0 依赖与版本配置。

本次 Gradle 从配置的仓库无法找到多个依赖。需先确认可用的发布版本、仓库或可重现的依赖供应方式，再重新 clean build；不要直接发布工作区现存旧包。

### P1-1：GUI 调用与宣称支持的旧版服务端不兼容

位置：`src/main/kotlin/com/worldscript/modules/l2/admin_gui/RegionGuiService.kt:109`；`src/main/kotlin/com/worldscript/modules/l3/spawn/SpawnMobSelectorGui.kt:59`。

编译目标 Paper 1.21.8 中 InventoryView 是接口，而本机核验的 1.12.2 和 1.20.1 中为抽象类。直接调用 `event.view.topInventory` 生成接口调用；现有 JAR 的 javap 输出明确为 `invokeinterface InventoryView.getTopInventory`。旧版点击库存会发生 IncompatibleClassChangeError，而且检查 holder 之前就调用，因此普通库存点击也经过此失败路径。Java 8 字节码和 plugin.yml api-version 无法修复这种 API 二进制差异。

另在 `RegionGuiService.kt:184` 直接使用 Block.isPassable；1.12.2 API 无此方法，区域传送另有 NoSuchMethodError 风险。需采用兼容的编译基线或版本适配调用，并在旧版与新版实服验证。

### P1-2：重复进入模式对新玩家永远不执行

位置：`src/main/kotlin/com/worldscript/modules/l2/script_actions/ScriptActionServiceImpl.kt:103`。

`repeatEntryOnly && firstEntry` 提前返回，而唯一的 entered 标记在第 121 行，导致新玩家每次进入仍被判定首次。配置 `events.enter.mode: repeat`，无进度玩家反复进出即可触发。应在确认真实进入后记录进入状态，将进度记录和本次是否执行动作分离。

### P1-3：背包满会永久丢失一次性物品奖励

位置：`src/main/kotlin/com/worldscript/modules/l2/rpg/RewardService.kt:35`。

忽略 Inventory.addItem 返回的剩余物品，并无条件返回成功，随后记录 once 奖励已领取。背包装满后触发一次性奖励，腾出空间也不能补领。应保证完整投递或处理剩余物品，部分成功时也需避免重试复制。

### P1-4：取消的移动或传送仍可触发区域奖励

位置：`src/main/kotlin/com/worldscript/modules/l1/region_events/RegionEvents.kt:138`；移动路径位于第 72–104 行。

传送监听在 NORMAL 阶段捕获目的地，下一 tick 仅检查在线，仍对捕获的旧目的地发发现/进入事件。其他插件随后取消或改目的地，玩家未到达也可能被发奖、解锁。移动监听也未跳过取消事件，且在最终移动结果前执行副作用。应分离准入拦截与最终位置确认后的事件执行。

### P1-5：重载刷怪丢失存活实体追踪

位置：`src/main/kotlin/com/worldscript/modules/l3/spawn/SpawnService.kt:43`。

reload 直接清空 runtime，却不移除或重新接管已刷实体。达到 maxAlive 后重载，下一轮从零重新计数，可再次刷满，旧怪也不再参与空区清理。toggle/delete 同样删除追踪。应保留存活 UUID 或在清空前按明确策略清除实体。

### P1-6：文件名不同于区域 ID 时编辑丢失、删除后复活

位置：`src/main/kotlin/com/worldscript/modules/l1/region_core/RegionCoreServiceImpl.kt:45`、`:170`、`:176`。

加载允许任意 yml 文件名并读取内部 id，保存/删除却只操作 `${id}.yml`，未记录原文件路径。自带 `examples/01-simple-region.yml` 的 id 为 starter_valley，按示例说明直接复制即可遇到：编辑生成 starter_valley.yml，重载时排序靠前的 01-simple-region.yml 先加载，编辑后的文件因重复 ID 被忽略；删除也只删 starter_valley.yml，原文件仍在，重载后区域复活。应维护源文件映射或安全地规范化文件名，并验证持久化结果。

## 其他应修复问题

| 优先级 | 位置 | 触发与影响 | 建议 |
|---|---|---|---|
| P2 | RewardService.kt:44；SettingsLayout.kt:40 | settings/economy.yml 被合并到 economy 节点，实际键为 economy.economy-command，奖励却读取根 economy-command；自定义经济命令不起作用，退回 eco give | 统一读取路径并覆盖非默认经济插件场景 |
| P2 | SpawnMobSelectorGui.kt:60；RegionGuiService.kt:110 | 底部背包 Shift 点击在取消前返回，可将物品移入顶部空槽，关闭界面后物品丢失 | 识别自定义 holder 后先取消所有库存点击，再判断操作槽位 |
| P2 | foundation/module/WorldScriptModuleManager.kt:159、:194 | 依赖只校验描述符存在；A 禁用或加载失败时，依赖 A 的 B 仍可执行 onEnable 并报告 ENABLED | 加载前验证所有硬依赖已成功启用，传播失败 |
| P2 | SpawnRuleRepository.kt:235 | 生成 global-max-alive: 300，但无读取或总数限制；多规则合计可越过该配置 | 实现全局存活上限或移除无效配置 |
| P2 | ProtectService.kt:80 | 提示冷却按 UUID 缓存，退出不清理，仅 reload 清空；长期运行随不同玩家累计 | 加入退出清理或有界淘汰 |

## 14 维度静态评分

这是发布证据完整度与静态质量评分，不是运行正确性概率。管理员工具不要求额外玩家命令，相关维度排除分母；未因缺少无关玩家 GUI 扣分。

| 维度 | 分数 / 10 | 依据 |
|---|---:|---|
| 编译检查 | 0 | 依赖解析失败，当前构建未完成 |
| 测试覆盖 | 4 | 现有 JUnit 与纯逻辑测试，未覆盖上述运行时链路，本次未执行 |
| TODO 残留 | 10 | 未发现功能性 TODO/FIXME |
| 事件契约闭合 | 4 | 取消事件及 repeat 模式缺陷 |
| 反硬编码 | 7 | 大部分配置化，存在失效配置 |
| 资源泄漏 | 5 | 怪物追踪丢失、PvP 冷却缓存未退出清理 |
| 交付文档 | 7 | 文档齐全，但旧报告不能证明当前版本可发布 |
| Config/Lang 完整性 | 7 | 多语言与设置齐全，经济路径及全局上限失效 |
| 功能完整性 | 4 | 奖励、重复进入、区域持久化存在缺陷 |
| 管理员命令 | 10 | 有权限、帮助、补全与管理入口 |
| 玩家命令 | 不适用 | 管理员区域工具无需玩家菜单命令 |
| 语言配置 | 7 | 主要反馈走语言文件，存在 fallback |
| 界面配置与反馈 | 7 | 有分页、布局与音效配置；未做视觉验证 |
| 事件发布/订阅 | 7 | 核心订阅存在，但实际触发结果仍有缺陷 |

合计 79/130，约 61/100。当前未通过发布门槛；评分不能抵消任何阻塞问题。

## 修复后验收重点

1. 修复依赖解析并对当前工作区完成 clean build、JUnit 和 runWorldScriptTests。
2. 对 repeat 首次/再次进入、满背包奖励、取消移动与传送、重载刷怪、非 ID 文件名持久化建立回归验证。
3. 在 1.12.2、1.20.1、1.21.8 至少验证启动、普通库存点击、管理 GUI、区域传送与奖励；这只是抽样，不能据此宣称整个版本区间已全部实测。
4. 使用新构建产物与对应校验值交付，并更新旧报告的验证范围。
