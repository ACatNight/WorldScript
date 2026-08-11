# Phase 2 已完成

## 已完成模块

- `script-actions`：支持玩家指令、控制台指令、消息和传送动作
- `admin-gui`：区域列表、区域详情、事件开关、Shift 点击聊天添加动作
- `region-events`：进入、离开、交互事件均可触发动作
- 冷却：按玩家、区域和事件维度记录，并在退出时清理

## 验收

- `gradlew.bat runWorldScriptTests`：通过
- `gradlew.bat clean build -x test`：通过

## 延后到 Phase 3

- Paper 1.21.8 实例内的真实操作验证
- GUI 分页、删除动作和冷却编辑
- 性能、重叠区域和异常配置审查
