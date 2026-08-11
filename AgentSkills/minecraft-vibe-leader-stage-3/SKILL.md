---
name: vibe-leader-stage-3
description: 第 3 轮打磨 Leader。负责测试编写 + MCP 最佳实践应用 + 交互体验打磨 + 最终报告。读取上轮交接文档，确认所有模块已实现，聚焦质量提升。目标：90 分可上线品质。
---

# VibeCoding Leader Stage-3：打磨

## 你的角色

你是第 3 轮 Team Lead。职责：测试覆盖 + 交互打磨 + 最佳实践应用，产出 90 分可上线品质。你不写模块代码，只协调和验收。

## 前置条件

第 2 轮（功能）已完成并通过操作者验收：
- [ ] gradlew compileKotlin 通过
- [ ] 所有模块有完整实现（无 TODO 残留）
- [ ] GUI 界面可打开、可交互
- [ ] Config 和 Lang 文件完整
- [ ] `_done_r2.md` 和 `_handoff_r2.md` 存在

## 工作流程

### Step 1：快速获取全景

```
读取顺序：
1. _handoff_r2.md              → 上轮状态全景、已知问题
2. 运行脚本确认剩余工作：
   python .claude/scripts/chains_index.py --stats --exclude _done_r1.md _done_r2.md
3. _handoff_r2.md Section 5    → 已知问题（测试/打磨/边界情况）
```

产出：
- 确认所有链阶段是否已完成（理想：193/193）
- 提取已知问题清单，作为本轮工作输入
- 识别需要测试覆盖的模块列表

### Step 2：测试编写

为每个 L1/L2 模块编写 TestRunner，触发 vibe-test Skill。

**分发策略**：按模块分发，每个 Agent 负责一个模块的 TestRunner。

```
for each module in [all L1 modules, all L2 modules]:
    prompt = """
    为 {module_name} 编写 TestRunner。

    读取顺序：
    1. .claude/docs/modules/{layer}/{module}.md → 模块规格
    2. modules/{layer}/{module}/service/Manager.kt → 内部实现
    3. 参考已有 TestRunner（如有）

    交付物：
    - peripheral/test/{Module}TestRunner.kt
    - TestCommand.kt 注册 subCommand
    - TestCommand.main 帮助列表更新
    - TestCommand.all 调用链更新
    """
    launch Task(prompt)
```

**测试优先级**：
1. 纯计算函数（概率计算、槽位计算、经验公式）
2. Config 解析（YAML → Model 的正确性）
3. 算法逻辑（保底机制、互斥检测、套装组合检测）
4. 数据序列化/反序列化（PDC 读写）

**测试用例设计**：
- 正常值：典型输入，验证预期输出
- 边界值：0、负数、最大值、空集合
- 随机一致性：相同输入多次执行结果一致

**验证**：所有 TestRunner 编写完成后，执行 `cvtest all`，确认全部 PASS。

### Step 3：MCP 知识库最佳实践（限定范围）

查询知识库，只针对以下领域应用最佳实践：

| 领域 | 查询方式 | 应用目标 |
|------|----------|----------|
| GUI 交互 | `get_detail(id="easylib/EasyGui")` + `get_structure(category="gui")` | 分页、动画、点击防抖 |
| 配置热重载 | `search_knowledge(query="config reload safe")` | reload 期间的数据竞争防护 |
| 事件性能 | `search_knowledge(query="event performance high frequency")` | 高频事件（战斗触发）优化 |
| PDC 序列化 | `search_knowledge(query="PDC persistent data")` | 附魔数据的 PDC 读写最佳实践 |

**不查**：基础语法、通用设计模式、已在 Structure 中覆盖的内容。

**应用方式**：识别需要优化的代码 → 分发修复任务给对应模块 Agent → 验证修复。

### Step 3.5：代码质量扫描（可执行检查，必须全部通过）

在交互打磨之前，先用自动化扫描发现结构性问题。每项检查附修复指引。

**A. 命令空壳检查（系统闭合度 P0）**

逐个审查 Command 类中的每个子命令：
```
对每个命令方法：
├── 是否只有 sendLang 而没有调用任何 Service 方法？→ 空壳命令，必须补实现
├── 是否有 TODO/FIXME/hint 占位？→ 必须补实现
└── 带参数的命令（如 page）是否真正使用了参数？→ 参数未使用 = 假实现
```
常见空壳模式：`reset` 命令只发消息不调 service、`forcecomplete` 只发提示不执行、分页参数被忽略永远显示固定条数。

**B. 命令层异常保护检查（健壮性 P0）**

```bash
# 找到所有 Command 类中的 Service 调用
grep -rn "ServiceImpl\.\|Service\." src/main/kotlin/*/command/ --include="*.kt" | grep -v "import\|//"
```
每个 Service 调用必须有 try-catch 包裹，异常时向执行者发送 sendLang 错误消息，而非让异常抛到控制台。

**C. 音效/粒子配置-执行一致性检查（GUI体验 P0）**

```
对 config.yml / notification.yml 中声明的每个 sound/particle 配置项：
├── 搜索代码中是否有读取该配置项的逻辑
├── 读取后是否有 player.playSound() / player.spawnParticle() 调用
└── 配置项存在但代码未读取 = 死配置，必须补执行代码
```
常见问题：config 里配了 `claim-sound` 和 `claim-particle`，但 GUI 领奖逻辑里没有读取和播放。

**D. GUI Material 配置化检查（版本兼容性）**

```bash
# 搜索 GUI 类中硬编码的 XMaterial/Material
grep -rn "XMaterial\.\|Material\." src/main/kotlin/*/gui/ --include="*.kt" | grep -v "import\|//"
```
GUI 中的状态材质（如 LIME_DYE/YELLOW_DYE/GRAY_DYE）应走 GUI YAML 配置或 config，不应硬编码在代码中。边框材质（BLACK_STAINED_GLASS_PANE）如果在 GUI YAML 中已配置则可接受。

**E. 代码重复检查（架构质量）**

```
搜索模式：
├── 相同方法名出现在多个文件 → 应抽到公共层
├── 相同的 GUI 辅助逻辑（setBackIcon、buildLoreLine）在多个 GUI 类重复 → 应抽到 GUI 基类
└── 相同的数据转换逻辑（buildRewardBundle）在多个 ServiceImpl 重复 → 应抽到对应的 L1 Service
```

**F. 主类生命周期检查**

```
├── onEnable 是否有显式的模块初始化编排（或确认 @Awake 顺序无冲突）
├── onDisable 是否有显式的关闭编排（保存数据 → 取消任务 → 释放资源）
└── 如果依赖 @Awake(DISABLE)，确认各模块的关闭顺序不会导致数据丢失
```

**执行方式**：Leader 逐项执行扫描命令，汇总问题清单，按模块分发修复任务给 Agent。所有问题修复后重新扫描确认。

### Step 4：交互体验打磨

逐项检查并优化用户体验：

```
检查清单：
├── 音效反馈
│   ├── 操作成功/失败有不同音效
│   ├── GUI 打开/关闭有音效
│   ├── 错误操作有警告音效
│   └── 【验证】config 中声明的音效是否在代码中被实际播放（非死配置）
├── 视觉反馈
│   ├── 关键操作有粒子特效
│   ├── 等级/赛季提升有 Title 提示
│   ├── 状态变化有视觉标识
│   └── 【验证】config 中声明的粒子是否在代码中被实际生成（非死配置）
├── 错误提示
│   ├── 材料不足 → 明确提示缺少什么、缺多少
│   ├── 权限不足 → 明确提示需要什么权限
│   ├── 操作冷却 → 提示剩余冷却时间
│   ├── 配置错误 → 控制台有清晰的错误日志
│   └── 【验证】GUI 领奖失败时是否有失败反馈（非静默无反应）
├── 边界情况
│   ├── 背包满 → 物品不丢失（掉落到脚下或暂存）
│   ├── 断线重连 → 数据不丢失（内存缓存 + 定时保存）
│   ├── 并发操作 → 不产生数据竞争
│   ├── 配置缺失 → 有合理默认值，不崩溃
│   └── 【验证】GUI 快速连点是否有防抖保护（防重复领奖）
├── 命令完整性
│   ├── 每个子命令是否有真实的 Service 调用（非空壳）
│   ├── 带参数的命令是否真正使用了参数
│   └── 管理员命令的 Service 调用是否有 try-catch
└── Lang 完整性
    ├── 所有玩家可见文本有 lang 条目
    ├── 管理员命令反馈有 lang 条目
    └── 错误消息有 lang 条目
```

发现问题后，分发修复任务给对应模块 Agent。

### Step 5：最终报告

生成两个文件：

#### `_done_r3.md`

```markdown
# 第 3 轮已完成工作

## 测试覆盖
| 模块 | TestRunner | 用例数 | 状态 |
|------|-----------|--------|------|
| enchantment_registry | EnchantmentRegistryTestRunner | 12 | ✅ PASS |
| ...

## 最佳实践应用
- GUI: 应用了分页防抖 (easylib/EasyGui)
- ...

## 交互打磨
- 补充了 15 个音效反馈
- 修复了 3 个边界情况
- ...
```

#### `_final_report.md`

```markdown
# 项目最终报告

## 项目概览
- 模块总数: X (L1: a, L2: b, L3: c)
- 行为链: 138 条, 193 个阶段
- 已闭合链: X/138

## 三轮构建摘要
| 轮次 | 目标 | 完成模块 | 链阶段完成 |
|------|------|----------|-----------|
| R1 粗胚 | 40 分 | L1 全量 + L2 骨架 | X/193 |
| R2 功能 | 70 分 | L2 填充 + L3 集成 | Y/193 |
| R3 打磨 | 90 分 | 测试 + 打磨 | 193/193 |

## 测试覆盖
- TestRunner 数量: X
- 测试用例总数: Y
- cvtest all: ✅ ALL PASS

## 质量检查
- [ ] 编译通过
- [ ] 无 TODO 残留
- [ ] 无硬编码残留
- [ ] 事件契约全部闭合
- [ ] Config/Lang 完整
- [ ] 测试全部 PASS

## 待持续迭代项（100 分路线）
- 上线后根据玩家反馈调整数值平衡
- 性能监控（高并发场景）
- 国际化（其他语言 lang 文件）
```

---

## 90 分完成标志

- [ ] 所有模块有 TestRunner，cvtest all 全部 PASS
- [ ] GUI 交互流畅，音效/粒子反馈完整
- [ ] 边界情况有合理处理（不崩溃、有提示）
- [ ] 无硬编码残留（no-hardcode 检查通过）
- [ ] MCP 最佳实践已应用到关键模块
- [ ] Step 3.5 代码质量扫描全部通过：
  - [ ] 无命令空壳（每个子命令有真实 Service 调用）
  - [ ] 命令层 Service 调用有 try-catch 保护
  - [ ] config 中声明的音效/粒子在代码中有实际执行
  - [ ] GUI 状态材质走配置而非硬编码
  - [ ] 无跨模块重复代码（公共逻辑已抽取）
  - [ ] 主类 onDisable 有显式关闭编排或确认 @Awake 顺序安全
- [ ] `_final_report.md` 已生成

## 异常处理

### 测试发现 bug

TestRunner 报告 FAIL → 定位到具体模块和方法 → 分发修复任务 → 重新执行测试确认 PASS。

### 最佳实践与现有代码冲突

MCP 知识库建议的模式与已实现代码不兼容 → 评估改动范围 → 如果改动过大（影响 >3 个模块），记录到 `_final_report.md` 的待迭代项，不在本轮强行修改。

### 打磨任务发现架构问题

交互打磨过程中发现需要重构的架构问题 → 记录到 `_final_report.md` → 不在本轮重构，留给持续迭代阶段。
