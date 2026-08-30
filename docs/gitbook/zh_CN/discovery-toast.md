# Toast 与发现提示

发现提示用于玩家首次进入、首次解锁或特殊事件时给出反馈。

## 支持内容

- Title 标题
- Subtitle 副标题
- Toast 原版成就弹窗
- 音效
- 首次发现奖励动作

## 游戏内编辑

```text
/ws edit <区域ID> discovery
```

可以修改：

- 是否启用发现系统
- 是否显示 Title
- 是否显示 Toast
- Toast 标题和描述
- Toast 图标
- Toast 样式
- 首次发现奖励动作

## 测试 Toast

```text
/ws toast test <区域ID>
```

如果不弹出，执行：

```text
/ws toast diagnose <区域ID>
```

重点检查图标物品 ID、服务器版本和 Toast 是否启用。

