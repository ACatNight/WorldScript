# Phase 1 交接说明

## 当前模块状态

| 模块 | 层级 | 状态 | 备注 |
|---|---|---|---|
| region-core | L1 | 已实现 | 由 YAML 保存区域定义 |
| region-events | L1 | 已实现 | 进入/离开和选点入口已连接 |
| script-actions | L2 | 骨架 | Phase 2 实现 |
| admin-gui | L2 | 骨架 | Phase 2 实现 |
| admin-command | L3 | 基础实现 | 后续接入 GUI |

## 已知问题

- 需要在 Paper 1.21.8 实例中验证工具选点和移动事件。
- 当前区域事件只发送语言消息，尚未读取动作列表。
- 当前区域重叠时只选择最高优先级区域。

## Phase 2 目标

实现 `ActionDefinition` 执行链、事件到动作的连接、`/ws gui` 区域列表和事件开关编辑。
