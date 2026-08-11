# WorldScript 项目契约

## 项目身份

- plugin_name: WorldScript
- package_name: com.worldscript
- 目标平台: Paper 1.21.8
- 目标 Java: 21
- 首版命令: `/ws`
- 首版语言: 简体中文

## 开发约束

- 正式工程使用 Kotlin + Gradle。
- 玩家可见文本统一从 `src/main/resources/lang/zh_CN.yml` 读取。
- 可调参数、Material、Sound 和 GUI 布局统一从 YAML 读取。
- 区域核心逻辑必须与 Bukkit/Paper 事件入口分离。
- 每个阶段完成后必须编译并补充阶段交接文档。

## 当前范围

第一阶段只实现基础骨架和核心区域模型契约：长方体区域、世界标识、两点坐标、区域事件类型和动作定义接口。

## 测试规划

正式业务阶段使用 `WorldScriptTestRunner`，测试日志使用 `[TEST]` 前缀；GUI 渲染和真实服务器事件保留为集成测试范围。
