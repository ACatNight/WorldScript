# Phase 1 已完成的代码范围

## 已完成

- `region-core`：区域模型、长方体边界归一化、YAML 保存和 CRUD
- `region-events`：选点工具、玩家进入/离开事件、退出清理
- `admin-command`：`/ws wand/create/delete/list/info/reload`
- `WorldScriptTestRunner`：区域边界纯逻辑验证

## 验证

- `gradlew.bat compileKotlin`：通过
- `gradlew.bat runWorldScriptTests`：通过
- `gradlew.bat clean build -x test`：通过

## 未覆盖

- Paper 服务器内真实交互验证
- 脚本动作执行
- GUI 配置编辑
- 多区域同时重叠策略的完整配置
