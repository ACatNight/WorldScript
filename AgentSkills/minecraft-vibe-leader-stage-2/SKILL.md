---
name: vibe-leader-stage-2
description: 第 2 轮功能 Leader。负责 L2 填充 + L3 集成 + Config/Lang 补全 + 交接文档。读取上轮交接文档快速获取全景，从 chains-status.md 减去已完成阶段生成倒排索引。目标：70 分完整功能。
---

# VibeCoding Leader Stage-2：功能

## 你的角色

你是第 2 轮 Team Lead。职责：填充 L2 业务逻辑 + 完成 L3 集成，产出 70 分完整功能。你不写模块代码，只协调和验收。

## 前置条件

第 1 轮（粗胚）已完成并通过操作者验收：
- [ ] gradlew compileKotlin 通过
- [ ] 所有 L1 模块有完整实现
- [ ] 所有 L2 模块有骨架（接口 + TODO[R2] 标注）
- [ ] `_done_r1.md` 和 `_handoff_r1.md` 存在

## 工作流程

### Step 1：快速获取全景

```
读取顺序（严格按此顺序，不要跳过）：
1. _handoff_r1.md          → 上轮状态全景（已完成模块、TODO 清单、已知问题）
2. chains-status.md        → 行为链原始数据
3. _done_r1.md             → 上轮已完成的链阶段
4. .claude/docs/_index.md  → 按需查阅模块依赖图
```

产出：
- 剩余待做阶段数量
- L2 模块的 TODO 清单（从 _handoff_r1.md 的 Section 2 提取）
- 本轮工作范围确认

### Step 2：生成倒排索引

运行预计算脚本，从全量链数据中减去第 1 轮已完成阶段：

```bash
python .claude/scripts/chains_index.py --all --exclude _done_r1.md
```

如需查看单模块清单：
```bash
python .claude/scripts/chains_index.py --module {module_id} --exclude _done_r1.md
```

产出：当前待做阶段的倒排索引，只包含 L2/L3 模块的清单。

### Step 3：启动 Team 完成 L2

按 Phase 顺序分发 L2 模块任务。Agent 的核心工作是**填充 TODO[R2] 标注的方法体**。

**分发流程**：

```
for each phase in [Phase 4, Phase 5]:
    modules = phase.l2_modules
    for each module in modules (并行):
        prompt = fill worker-prompt-template with:
            {module_name}, {module_id}, {layer}=L2,
            {dependencies}, {module_spec_path}, {api_path},
            {dep_api_paths},
            {chain_stages}=$(python .claude/scripts/chains_index.py --module {module_id} --exclude _done_r1.md)
        launch Task(prompt)
    wait all agents complete
    run Phase 验收检查
```

**Worker Agent 的任务重点**：
- 搜索模块内所有 `TODO[R2]` 标注
- 按链阶段逐个填充业务逻辑
- 实现事件监听和发布（对照 event-contract.md）
- 调用 L1 模块的 API（只读查询）
- 通过事件与其他模块通信（写操作）
- 完成后确认无 `TODO[R2]` 残留

**Worker Agent 的完整交付物**：
- Manager 业务逻辑（填充空壳）
- ServiceImpl 方法体（替换 throw NotImplementedError）
- 事件监听/发布代码
- GUI 交互逻辑（如果模块涉及 GUI）
- Config 条目（模块级配置）
- Lang 条目（模块级多语言）

### Step 4：启动 Team 完成 L3

分发 L3 模块任务（Phase 6）。L3 模块通常是边缘集成，依赖多个 L1/L2 模块。

**L3 模块类型**：
- 管理员命令（admin_command_handler）
- 外部插件集成（mythicmobs_integration_service、vault_economy_integration、easylib_item_integration）
- 数据查询扩展（placeholder_expansion）
- API 开放（java_api_provider、kether_action_provider）
- 调试工具（debug_tool）
- 数据迁移（migration_tool）
- 统计收集（metrics_collector）

**分发方式**：同 Step 3，但 worker prompt 中的依赖 API 列表会更长（L3 依赖多个 L1/L2）。

### Step 5：Config + Lang 补全

所有模块完成后，触发反硬编码检查（no-hardcode Skill）：

```
检查项：
├── 扫描所有 .kt 文件中的 sendMessage() 调用 → 应改为 sendLang()
├── 扫描硬编码数字（魔法数字） → 应走 config
├── 扫描硬编码 Material/Sound/Particle → 应走配置文件
├── 确认 lang/zh_CN.yml 条目完整
├── 确认 config.yml 条目完整
└── 确认 GUI YAML 中的标题/描述走 lang
```

如果发现遗漏，分发修复任务给对应模块的 Agent。

### Step 6：生成交接文档

#### `_done_r2.md`

```markdown
# 第 2 轮已完成链阶段

## 已完成
- craft-enchant-success [执行] — craft_service ✅
- craft-enchant-success [产出] — enchant_item_generator ✅
- ...

## 统计
- 总阶段数: 193
- 第 1 轮完成: X
- 第 2 轮完成: Y
- 剩余: Z（应为 0 或接近 0）
```

#### `_handoff_r2.md`

```markdown
# 第 2 轮交接文档

## 1. 已完成模块清单
| 模块 | 层级 | 状态 | 备注 |
|------|------|------|------|
| enchantment_registry | L1 | ✅ 完整实现 | R1 完成 |
| craft_service | L2 | ✅ 完整实现 | R2 完成 |
| admin_command_handler | L3 | ✅ 完整实现 | R2 完成 |

## 2. TODO 清单（带行为链引用）
（理想情况下为空，如有残留列出）

## 3. 当前项目目录树

## 4. 已闭合的行为链
- [x] load-enchantment-definitions (4/4 阶段)
- [x] craft-enchant-success (5/5 阶段)
- ...

## 5. 已知问题
- 需要测试覆盖的模块: ...
- GUI 交互待打磨: ...
- 边界情况待处理: ...
```

---

## Phase 验收检查

每个 Phase 完成后，执行以下可执行验证命令。先跑命令，再人工判断。

### 模块验收（阻塞性）

**1. 编译检查**
```bash
gradlew.bat compileKotlin 2>&1 | tail -5
```
必须看到 BUILD SUCCESSFUL。

**2. TODO[R2] 残留检查**
```bash
grep -rn "TODO\[R2\]" src/main/kotlin/ --include="*.kt"
```
期望：无输出。有输出 = 该模块未完成。

**3. 入口活性检查（P0 核心）**
```bash
# 检查模块规格中声明的 platform_entry_events 是否在代码中有监听
# 对每个 L2 模块执行：
grep -rn "EntityDamageByEntityEvent\|PlayerInteractEvent\|InventoryClickEvent\|PlayerItemHeldEvent\|PlayerJoinEvent\|PlayerQuitEvent" src/main/kotlin/*/modules/l2/ --include="*.kt" -l
```
对照各模块规格中的 `platform_entry_events`，确认每个声明的事件都有对应文件。缺失 = 死代码管道。

**4. 事件契约闭合检查**
```bash
# 提取所有 .call() 发布的事件
grep -rn "\.call()" src/main/kotlin/ --include="*.kt" | grep -oP "\w+Event" | sort -u > /tmp/published.txt
# 提取所有 @SubscribeEvent 监听的事件
grep -rn "@SubscribeEvent" -A2 src/main/kotlin/ --include="*.kt" | grep -oP "\w+Event" | sort -u > /tmp/listened.txt
# 对比差异
comm -23 /tmp/published.txt /tmp/listened.txt
```
输出的事件 = 发布了但无人监听（可能是扩展事件，需逐个确认）。

**5. 反硬编码检查**
```bash
# sendMessage 硬编码
grep -rn 'sendMessage(' src/main/kotlin/*/modules/ --include="*.kt" | grep -v "sendLang\|sendMessage(comp\|// debug"
# 裸数字（概率/倍率/阈值）
grep -rn "[^a-zA-Z_]0\.\d\+\|[^a-zA-Z_][1-9]\d*\.\d" src/main/kotlin/*/modules/l2/ --include="*.kt" | grep -v "config\|Config\|import\|// "
# 硬编码资源 ID
grep -rn "Material\.\|Sound\.\|Particle\." src/main/kotlin/*/modules/ --include="*.kt" | grep -v "config\|Config\|import"
```
有输出 = 需要修复。

**6. 资源泄漏检查**
```bash
# 查找 per-player 缓存
grep -rn "Map<UUID\|HashMap<UUID\|ConcurrentHashMap<UUID\|mutableMapOf<UUID" src/main/kotlin/*/modules/ --include="*.kt" -l
# 对比是否有 PlayerQuitEvent 清理
grep -rn "PlayerQuitEvent\|PlayerKickEvent" src/main/kotlin/*/modules/ --include="*.kt" -l
```
有缓存的模块必须有对应的 Quit 清理。

**7. 接口一致性**
```bash
# 检查 ServiceImpl 是否覆盖了所有 API 方法
grep -rn "override fun " src/main/kotlin/*/modules/l2/*/service/*ServiceImpl* --include="*.kt" | wc -l
grep -rn "fun " src/main/kotlin/*/api/*Service.kt | grep -v "override\|//" | wc -l
```
两个数字应该匹配（ServiceImpl override 数 ≥ API 声明数）。

### 链验收（非阻塞性）

对所有阶段模块均已实现的链，执行端到端检查：

```
对每条可闭合的链：
├── 事件链路完整性：阶段 A 发布的事件 → 阶段 B 是否有对应监听
├── 数据流完整性：阶段 A 产出的数据类型 → 阶段 B 入参是否匹配
└── 发现断裂 → 记录到 _handoff_r2.md 的「已知问题」
```

## 70 分完成标志

- [ ] 所有模块有完整实现（无 TODO[R2] 残留）
- [ ] GUI 界面可打开、可交互
- [ ] Config 和 Lang 文件完整
- [ ] 事件契约闭合（发布方和监听方配对）
- [ ] gradlew compileKotlin 通过
- [ ] `_done_r2.md` 和 `_handoff_r2.md` 已生成

## 异常处理

### Agent 发现 L1 模块 bug

Agent 报告哪个 L1 方法/事件行为不符预期 → Team Lead 评估 → 直接修复 L1 代码（本轮 Lead 有权修复上轮产出）→ 通知受影响的 Agent。

### Agent 发现接口需要扩展

L2 实现过程中发现 L1 接口缺少方法 → Team Lead 评估 → 补充 api/ 接口 + ServiceImpl → 更新 .claude/docs/api/ 文档。

### TODO 标注与实际需求不匹配

Agent 发现 TODO[R2] 描述的链阶段与模块规格不一致 → 以 chains-status.md + 模块规格为准 → Team Lead 修正 TODO 或调整任务分配。

### 文档与源码不一致

以源码为准 → Agent 报告不一致 → Team Lead 修正 .claude/docs/。

### Agent 遇到框架 API 问题

Agent 报告查询了什么工具/ID、返回了什么、实际行为是什么 → Team Lead 通过 submit_issue 提交到知识库 inbox → 提供临时 workaround。
