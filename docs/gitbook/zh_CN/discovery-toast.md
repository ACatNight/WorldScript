# Toast 与发现提示

发现提示就是玩家第一次来到某个区域时，给他一点反馈。

比如：

```text
发现新区域
古老森林
```

可以是 Title，也可以是原版成就那种 Toast，还可以带音效和奖励。

## 在哪里改？

```text
/ws edit <区域ID> discovery
```

能改这些：

- 发现系统开关
- Title 开关
- Toast 开关
- Toast 标题
- Toast 描述
- Toast 图标
- Toast 样式
- 首次发现奖励动作

## 怎么测试？

```text
/ws toast test <区域ID>
```

如果不弹：

```text
/ws toast diagnose <区域ID>
```

常见原因就这几个：

- 区域没开 Toast
- 图标物品在当前版本不存在
- 服务器版本对 Toast 数据格式比较挑
- 玩家已经发现过这个区域，但你测的是首次发现逻辑

想绕过“是否首次发现”，就用 `/ws toast test` 直接预览。
