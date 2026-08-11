# WorldScript 文档索引

## 模块

| ID | 名称 | 层级 | 依赖 | 状态 |
|---|---|---|---|---|
| region-core | 区域核心模型与存储 | L1 | 无 | Phase 0 |
| region-events | 区域事件入口 | L1 | region-core | Phase 0 |
| script-actions | 脚本动作系统 | L2 | region-core, region-events | Phase 0 |
| admin-command | 管理员命令 | L3 | region-core | Phase 0 |
| admin-gui | GUI 配置管理 | L2 | region-core, script-actions | Phase 0 |

## Phase 顺序

1. Phase 0：项目契约、模型、接口和可编译骨架
2. Phase 1：区域选择、保存、进入/离开事件
3. Phase 2：动作配置和 GUI 编辑
4. Phase 3：测试、性能、错误恢复和发布审查
