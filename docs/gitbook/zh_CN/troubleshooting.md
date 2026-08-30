# 常见问题

## 区域没有触发

1. 执行 `/ws validate <区域ID>`。
2. 检查世界名和坐标。
3. 检查事件是否 `enabled: true`。
4. 检查进入条件是否满足。
5. 检查父子区域是否覆盖正确。

## Toast 不弹

1. 执行 `/ws toast diagnose <区域ID>`。
2. 检查 Toast 是否启用。
3. 检查图标是否是当前版本存在的物品。
4. 使用 `/ws toast test <区域ID>` 直接预览。

## 怪物不刷新

1. 执行 `/ws spawn list`。
2. 执行 `/ws spawn test <规则ID>`。
3. 检查 MythicMobs 是否安装。
4. 检查 MM 怪物 ID 是否存在。
5. 检查区域内是否有安全落点。
6. 检查最大存活数量是否达到上限。

## Placeholder 不显示

1. 确认安装 PlaceholderAPI。
2. 执行 `/ws reload`。
3. 用 `/papi parse me %worldscript_region_name%` 单独测试。
4. 确认计分板、Tab 或 HUD 插件支持 PAPI。

