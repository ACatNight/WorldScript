---
name: vibe-init
description: 项目脚手架初始化。读取 MPGA 流水线产出物（.claude/docs/），生成可编译的空项目骨架（目录结构、build.gradle.kts、主类、数据模型、模块包和空接口）。在三轮构建的第 0 轮使用，单 Agent 完成，无需 Team。
---

# Phase 0：项目脚手架初始化

## 你的角色

你是项目初始化 Agent。职责：读取 MPGA 流水线产出物，生成一个**可编译的空项目骨架**。你不实现业务逻辑，只搭建结构。

## 核心原则

1. **只搭骨架，不写逻辑** — 所有方法体为空或 TODO，不猜测实现
2. **文档驱动** — 一切信息从 `.claude/docs/` 读取，不凭记忆假设
3. **编译必须通过** — 每一步产出都要能 `gradlew compileKotlin`
4. **MCP 知识库先行** — 写 build.gradle.kts 和项目结构前，先查询框架约束

## 输入

你需要读取以下文件（按顺序）：

```
.claude/CLAUDE.md                           → 项目信息（插件名、包名）
.claude/docs/_index.md                      → 模块清单、依赖图、Phase 顺序
.claude/docs/foundation/model.md            → 共享数据模型目录
.claude/docs/foundation/event-contract.md   → 事件契约表
.claude/docs/status/phase-progress.md       → Phase 进度（确认 Phase 0 待做）
```

## 产出

```
{project_root}/
├── build.gradle.kts                        # 构建配置
├── settings.gradle.kts                     # 项目设置
├── gradlew / gradlew.bat                   # Gradle Wrapper
├── src/main/kotlin/{package}/
│   ├── {PluginName}.kt                     # 主类（onEnable/onDisable 空壳）
│   ├── foundation/
│   │   └── model/                          # 所有共享数据模型类
│   ├── api/                                # 所有模块的 API 接口
│   │   └── {Module}Service.kt
│   ├── modules/
│   │   ├── l1/{module_id}/                 # L1 模块包目录
│   │   ├── l2/{module_id}/                 # L2 模块包目录
│   │   └── l3/{module_id}/                 # L3 模块包目录
│   └── peripheral/
│       └── test/                           # 测试框架占位
├── src/main/resources/
│   ├── plugin.yml                          # 插件描述
│   └── config.yml                          # 空配置文件
└── .claude/docs/                           # 保持不动（流水线产出物）
```

## 工作流程

### Step 1：读取项目信息

从 `.claude/CLAUDE.md` 提取：
- `plugin_name` — 插件名（如 RuneForge）
- `package_name` — 包名（如 com.example.runeforge）

从 `.claude/docs/_index.md` 提取：
- 模块清单表（模块 ID、名称、层级、依赖）
- Phase 构建顺序
- 依赖图

从 `.claude/docs/foundation/model.md` 提取：
- 所有共享数据模型（模型名、所属模块、字段说明）

从 `.claude/docs/foundation/event-contract.md` 提取：
- 所有事件名称和发布/监听方

### Step 2：查询 MCP 知识库

写代码前必须查询框架约束，不要凭记忆猜测。

```
必查项：
├── get_build_kit(topic="project-setup")     → 项目结构和 build.gradle.kts 规范
├── get_structure(category="config")          → Config 系统约束
├── get_structure(category="database")        → Database 系统约束
├── get_structure(category="command")         → 命令系统约束
└── get_structure(category="language")        → 多语言系统约束
```

如果 MCP 工具不可用，跳过此步，按通用 TabooLib 项目结构生成。

### Step 3：生成构建配置

创建 `build.gradle.kts`：
- group = `{package_name}`
- 插件名 = `{plugin_name}`
- 依赖项根据 _index.md 中的技术栈确定（TabooLib、EasyLib 等）
- Kotlin 版本、JVM target 等按框架要求

创建 `settings.gradle.kts`：
- rootProject.name = `{plugin_name}`

### Step 4：创建主类

路径：`src/main/kotlin/{package_path}/{PluginName}.kt`

```kotlin
// 主类模板
object {PluginName} : Plugin() {
    override fun onEnable() {
        // TODO: Phase 1+ 模块注册
    }

    override fun onDisable() {
        // TODO: 资源释放
    }
}
```

### Step 5：创建共享数据模型

路径：`src/main/kotlin/{package_path}/foundation/model/`

对 model.md 中的每个模型，创建一个 data class：
- 类名 = 模型名（如 `EnchantmentDefinition`）
- 字段从 model.md 的「说明」列解析
- 字段类型尽量精确（String、Int、List<String> 等）
- 无法确定的字段类型用 `Any` + TODO 注释

```kotlin
// 示例
data class EnchantmentDefinition(
    val id: String,
    val name: String,
    val rarity: String,          // TODO: 考虑改为枚举
    val maxLevel: Int,
    val slotCost: Int,
    // ... 从 model.md 解析的其他字段
)
```

### Step 6：创建事件类

路径：`src/main/kotlin/{package_path}/foundation/event/`

对 event-contract.md 中的每个事件，创建一个事件类：
- 类名 = 事件名（如 `EnchantCraftEvent`）
- 字段从「携带数据」列解析
- 继承框架的事件基类

```kotlin
// 示例
class EnchantCraftEvent(
    val player: Player,
    val enchantmentId: String,
    // ... 从 event-contract.md 解析
) : BukkitProxyEvent()
```

### Step 7：创建模块 API 接口

路径：`src/main/kotlin/{package_path}/api/`

对 _index.md 模块清单中的每个模块，创建一个 Service 接口：
- 文件名 = `{ModuleName}Service.kt`（ModuleName 为模块名称的 PascalCase）
- 接口内方法暂时为空，只放 TODO 注释标注该模块的职责

```kotlin
// 示例：api/EnchantmentRegistryService.kt
interface EnchantmentRegistryService {
    // TODO: Phase 1 实现 — 附魔定义的注册、查询、校验
}
```

如果 `.claude/docs/modules/{L1|L2|L3}/{module_id}.md` 中有明确的交互表格（调用方/被调用方），可以从中提取方法签名作为接口方法。但方法体一律不实现。

### Step 8：创建模块包目录

对 _index.md 中的每个模块，按层级创建包目录：

```
modules/
├── l1/{module_id}/
│   ├── config/          # 配置加载
│   ├── service/         # 内部实现
│   └── data/            # 数据访问（如需要）
├── l2/{module_id}/
│   ├── config/
│   ├── service/
│   └── gui/             # L2 可能有 GUI
└── l3/{module_id}/
    └── (按需)
```

每个模块目录下放一个占位文件（如 `package-info.kt` 或空的 `README.md`），确保目录被 Git 追踪。

### Step 9：创建资源文件

```
src/main/resources/
├── plugin.yml           # 插件描述（name、version、main、depend 等）
├── config.yml           # 空配置（# TODO: Phase 1+ 填充）
└── lang/
    └── zh_CN.yml        # 空语言文件（# TODO: Phase 1+ 填充）
```

`plugin.yml` 模板：
```yaml
name: {plugin_name}
version: 1.0.0
main: {package_name}.{PluginName}
api-version: '1.20'
depend: [TabooLib]
softdepend: [MythicMobs, Vault, PlaceholderAPI]
```

### Step 10：编译验证

执行 `gradlew.bat compileKotlin`（Windows）或 `./gradlew compileKotlin`（Linux/Mac）。

- 编译通过 → 进入完成检查
- 编译失败 → 逐个修复，常见问题：
  - 导入缺失 → 补充 import
  - 类型不匹配 → 调整数据模型字段类型
  - 框架 API 变更 → 查询 MCP 知识库确认正确用法

---

## 完成检查清单

- [ ] `gradlew compileKotlin` 通过（零错误）
- [ ] 目录结构与 `_index.md` 模块清单一致（每个模块有对应包）
- [ ] 所有共享数据模型类已创建（字段完整，类型合理）
- [ ] 所有事件类已创建（字段从 event-contract.md 解析）
- [ ] 所有模块有 API 接口文件（方法可为空，但文件必须存在）
- [ ] 主类存在且 onEnable/onDisable 为空壳
- [ ] plugin.yml 信息正确（name、main、depend）
- [ ] 无硬编码的包名或插件名（全部从 CLAUDE.md 读取）

## 完成后

向操作者报告：
1. 项目结构概览（目录树）
2. 创建的文件数量统计（模型类 N 个、事件类 N 个、接口 N 个、模块包 N 个）
3. 编译结果
4. 已知的 TODO 项（无法确定的字段类型等）

操作者验收通过后，新开窗口载入 `minecraft-vibe-leader-1` 进入第 1 轮构建。

---

## 注意事项

- **不要创建 ServiceImpl** — 实现类由 Leader-1 的 Worker Agent 编写
- **不要写业务逻辑** — 方法体只放 TODO 或 throw NotImplementedError
- **不要修改 .claude/docs/** — 流水线产出物是只读的
- **数据模型字段宁多勿少** — 后续轮次删字段比加字段容易
- **事件类字段宁简勿繁** — 只放 event-contract.md 明确提到的数据
