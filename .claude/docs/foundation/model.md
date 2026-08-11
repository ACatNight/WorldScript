# 共享数据模型

## RegionDefinition

- `id`: 区域唯一标识
- `displayName`: 区域显示名称
- `worldId`: 世界 UUID 字符串
- `worldName`: 世界名称备用信息
- `min`: 最小坐标
- `max`: 最大坐标
- `priority`: 区域优先级
- `events`: 事件到脚本定义的映射

## BlockPosition

- `x`: 整数方块坐标
- `y`: 整数方块坐标
- `z`: 整数方块坐标

## RegionEventType

首版包括 `ENTER`、`LEAVE`、`INTERACT`。

## ScriptDefinition

- `enabled`: 是否启用
- `cooldownSeconds`: 冷却秒数
- `actions`: 有序动作列表

## ActionDefinition

- `type`: 动作类型
- `value`: 动作参数

首版动作类型预留 `PLAYER_COMMAND`、`CONSOLE_COMMAND`、`MESSAGE`、`TELEPORT`。
