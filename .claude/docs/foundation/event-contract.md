# 事件契约

## 平台输入事件

| Paper 事件 | 用途 |
|---|---|
| PlayerInteractEvent | 选点工具和区域内交互入口 |
| PlayerMoveEvent | 判断区域进入和离开 |
| InventoryClickEvent | GUI 操作入口 |
| PlayerQuitEvent | 清理玩家区域状态 |

## 核心事件

Phase 1 预留 `RegionEnterEvent`、`RegionLeaveEvent` 和 `RegionInteractEvent`。

核心事件只携带标准模型和玩家上下文，不直接依赖 GUI 或配置文件实现。
