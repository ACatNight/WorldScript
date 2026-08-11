# Phase 进度

## Phase 0

- 状态：已完成
- 已完成：项目契约、模块索引、共享模型、API 接口、Kotlin/Gradle 骨架、默认配置、语言文件和 Wrapper
- 验收：`gradlew.bat compileKotlin` 通过
- 暂不计入：区域运行逻辑、脚本动作执行、GUI 交互和测试 Runner

## Phase 1

- 状态：进行中
- 已完成：区域 YAML 存储、`/ws` 基础命令、选点工具、进入/离开事件和玩家状态清理
- 验收：`gradlew.bat compileKotlin` 和 `gradlew.bat runWorldScriptTests` 通过
- 待完成：正式 TestRunner 验证、服务器集成验证、阶段交接文档

## Phase 2

- 状态：已完成
- 已完成：动作模型读取与保存、玩家/控制台指令、消息、传送动作、进入/离开/点击事件动作监听、区域列表 GUI、事件开关 GUI、GUI 聊天添加动作、每玩家事件冷却
- 验收：`gradlew.bat runWorldScriptTests` 和 `gradlew.bat clean build -x test` 通过
- 后续：Paper 服务器集成验证进入 Phase 3

## Phase 3

- 状态：进行中
- 已完成：配置 Material 容错、动作异常隔离、玩家状态清理、串行构建验证、静态质量检查
- 验收：`gradlew.bat runWorldScriptTests` 和 `gradlew.bat clean build -x test` 通过
- 阻塞项：当前工作区没有 Paper 1.21.8 服务端实例，尚未完成真实服务器 GUI/事件验证
