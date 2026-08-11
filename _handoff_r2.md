# Phase 2 交接说明

## 交付范围

区域事件现在会读取保存的 `ScriptDefinition.actions`，并由 `ScriptActionServiceImpl` 执行。GUI 使用 `/ws gui` 打开，普通点击切换事件，Shift 点击事件按钮后在聊天输入 `类型|内容` 添加动作。

## 动作格式

- `MESSAGE|文本`
- `PLAYER_COMMAND|菜单 %player%`
- `CONSOLE_COMMAND|give %player% diamond 1`
- `TELEPORT|world,100,64,100`

## 已知问题

- 当前聊天编辑只支持添加动作，不支持删除和排序。
- 需要真实 Paper 服务器验证事件顺序、GUI Holder 和传送动作。
- Bukkit 1.21.8 对部分旧 ChatColor/Inventory API 给出弃用警告，Phase 3 再迁移 Adventure API。
