---
name: vibe-test
description: VibeCoding 模块测试规范。当 Agent 完成模块构建后自动触发，要求编写 TestRunner 并验证。测试命令和模块映射等项目特定信息从 .claude/CLAUDE.md 获取。与 vibe-build、vibe-docs、agent-team、no-hardcode 联动，构成 VibeCoding 黄金五角。
---

# VibeCoding 模块测试规范

## 你是谁

你是 VibeCoding 测试协调者。你的职责是确保每个模块在构建完成后都有对应的 TestRunner，并通过服务器内测试验证正确性。

## 项目特定信息在哪里

**测试命令、模块前缀映射、TestRunner 清单等项目特定信息，必须从 `.claude/CLAUDE.md` 的「测试规划」段落获取。**

本 Skill 只定义通用规范和流程，不硬编码任何项目特定的命令或模块名。

---

## 核心原则

1. **模块完成 = 代码 + 测试**：没有 TestRunner 的模块不算完成
2. **纯函数优先**：优先测试无副作用的纯计算函数（Manager 层的数学/逻辑方法）
3. **Mock 不依赖外部**：测试用 mock 数据直接构造 Model 对象，不依赖数据库/玩家/服务器状态
4. **日志即断言**：所有测试输出通过 `[TEST]` 前缀写入服务器日志，MCP `read_test_log` 可过滤验证
5. **增量可扩展**：新模块只需新增 TestRunner + 在 TestCommand 注册一个 subCommand

---

## 日志输出格式（通用）

```
[TEST] START {prefix}
[TEST] PASS {prefix}.{testName}: {description}
[TEST] FAIL {prefix}.{testName}: {description} | expected={X} actual={Y}
[TEST] ERROR {prefix}.{testName}: {exceptionClass}: {message}
[TEST] SUMMARY {prefix}: passed={N} failed={N} total={N}
```

MCP `read_test_log` 的 `event_filter` 参数可按 `START`/`PASS`/`FAIL`/`ERROR`/`SUMMARY` 过滤。

---

## 触发场景与行为

### 场景 A：Agent 完成模块构建（vibe-build Step 6 之后）

自动进入 Step 7：编写 TestRunner。

### 场景 B：用户说「给 XXX 模块写测试」

为指定模块编写 TestRunner。

### 场景 C：用户说「运行测试」或「验证模块」

1. 读取 `.claude/CLAUDE.md` 中的「测试规划」段落，获取测试命令和模块前缀
2. 通过 RCON 执行测试命令
3. 用 `read_test_log` 检查结果

---

## TestRunner 编写规范

### 文件位置与命名

```
peripheral/test/{被测类名}TestRunner.kt
```

### 前缀命名规则

- 全小写，单词，不含下划线
- 与测试子命令名一致
- 日志中体现为 `[TEST] PASS {prefix}.{testName}`
- 具体的模块→前缀映射见 `.claude/CLAUDE.md`

### 模板

```kotlin
object XxxTestRunner {

    private fun mockXxx(...) = XxxModel(...)

    fun run(): List<TestFramework.TestResult> = TestFramework.runSuite("xxx") {

        runTest("functionName.scenario") {
            val mock = mockXxx(...)
            val result = XxxManager.someFunction(mock)
            assertEquals("functionName.scenario", "描述", expected, result)
        }

        runTest("random.stability") {
            val rng = kotlin.random.Random(42)
            repeat(20) { i ->
                val param = rng.nextInt(0, 1000)
                val result = XxxManager.someFunction(param)
                assertInRange("random.stability.$i", "随机参数=$param", result, 0, 99999)
            }
        }

        runTest("boundary.zeroValues") {
            val result = XxxManager.someFunction(0)
            assertEquals("boundary.zeroValues", "零值不崩溃", expectedZero, result)
        }
    }
}
```

### 注册到 TestCommand

在项目的 TestCommand 中添加 subCommand，同时更新 `main` 帮助列表和 `all` 调用链。
具体的注册模式参考项目中已有的 TestRunner 实现。

---

## 测试用例设计原则

### 优先测试什么

```
优先级从高到低：
1. Manager 层的纯计算函数（无 DB/Player 依赖，直接构造 mock 数据）
2. Config 层的解析逻辑（构造 YAML 内容，验证解析结果）
3. Engine 层的算法逻辑（构造 mock 数据，验证引擎输出）
4. ServiceImpl 的组合逻辑（需要更多 mock，优先级较低）
```

### 不测试什么

```
跳过：
- GUI 渲染（无法在服务端验证）
- 数据库 CRUD（需要真实连接，属于集成测试）
- 事件监听器（需要真实 Bukkit 事件触发）
- 外部 API 调用（Cobblemon 等）
```

### Mock 数据构造规则

```
1. 直接构造 Model 对象，不调用 Repository/Database
2. 使用已知的确定性参数，便于计算期望值
3. 随机测试使用固定种子 Random(42)，保证可复现
```

### 测试命名规则

```
{functionName}.{scenario}

示例：
- repairCost.fullDurability     — 满耐久时的维修费
- upgradeCost.maxLevel          — 满级时的升级费
- successRate.range             — 成功率范围验证
- random.multipleInputs         — 随机参数一致性
- boundary.zeroValues           — 零值边界
```

---

## 可用断言方法

| 方法 | 用途 |
|------|------|
| `assertEquals(testName, desc, expected, actual)` | 精确相等 |
| `assertTrue(testName, desc, value)` | 布尔真 |
| `assertFalse(testName, desc, value)` | 布尔假 |
| `assertInRange(testName, desc, value, min, max)` | 数值范围 |
| `assertGreaterThan(testName, desc, value, threshold)` | 大于阈值 |
| `assertNotNull(testName, desc, value)` | 非空 |

---

## 验证流程

```
1. 编写 TestRunner + 注册到 TestCommand
2. 提示用户执行 gradlew build 并部署
3. 从 .claude/CLAUDE.md 获取测试命令，通过 RCON 执行：
   mcp: run_command_with_log("{测试命令} {模块前缀}", wait_seconds=10)
4. 检查结果：
   mcp: read_test_log(event_filter="SUMMARY")  → 确认 passed/failed 数
   mcp: read_test_log(event_filter="FAIL")     → 查看失败详情
5. 如有失败 → 修复代码 → 重新构建 → 重新测试
```

### 注意事项

- RCON 执行测试时服务器可能短暂卡顿，这是正常的
- 如果 `run_command_with_log` 返回空结果，不代表失败，等待后用 `read_test_log` 检查日志即可
- `read_test_log` 支持 `since_position` 增量读取，避免读到旧测试结果

---

## Phase 验收检查（追加项）

```
测试检查：
- 每个模块是否有对应的 TestRunner
- 执行全量测试命令（从 CLAUDE.md 获取），是否全部 PASS
- 无 FAIL 或 ERROR

文档自检（联动 vibe-docs）：
- 扫描 .claude/docs/ 下所有文件，不得残留未处理的 {xxx} 占位符
- 所有观察/审计/疑问必须已标记为 TODO / WONTFIX / DISCUSS
- _index.md 模块清单与 api/ 目录文件一一对应
- phase-progress.md 状态与 _index.md 状态一致
```

---

## 本地日志过滤脚本

`scripts/filter_test_log.py` — 从服务器日志文件中提取 `[TEST]` 行，彩色高亮显示。

位置：`C:\Users\pc\.claude\skills\minecraft-vibe-test\scripts\filter_test_log.py`

```bash
python filter_test_log.py latest.log                    # 全部
python filter_test_log.py latest.log --event FAIL       # 只看失败
python filter_test_log.py latest.log --event SUMMARY    # 只看摘要
python filter_test_log.py latest.log --module team      # 只看某模块
python filter_test_log.py latest.log --follow           # 实时跟踪
```
