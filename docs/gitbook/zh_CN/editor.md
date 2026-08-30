# 编辑器

WorldScript 的编辑器目标是“傻瓜式”：尽量通过聊天栏按钮和 GUI 完成配置，不要求管理员直接写 YAML。

## 聊天栏编辑器

```text
/ws edit <区域ID>
```

常用页面：

- `main`：区域基础信息
- `events`：事件列表
- `discovery`：发现提示
- `conditions`：进入条件
- `spawn`：刷怪规则
- `data`：变量和状态

## GUI 编辑

部分内容适合用 GUI 点选，例如：

- Toast 图标
- Spawn 怪物
- 区域列表

## 编辑建议

- 新手优先用聊天栏按钮。
- 复杂逻辑再进入 YAML 或 Kether。
- 每次改完执行 `/ws validate`。
- 实服测试前先用 `/ws test <区域ID>` 或 `/ws spawn test <规则ID>`。

