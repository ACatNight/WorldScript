# 常见问题

如果这里没找到答案，可以去 [Discord](https://discord.gg/NPSwPHG9R) 讨论，或者发邮件到 `acatnight@gmail.com`。

## 区域没触发

先跑：

```text
/ws validate <区域ID>
```

然后看这几个：

- 世界名对不对
- 坐标范围有没有圈住玩家
- 事件是不是 `enabled: true`
- 进入条件是不是没达到
- 子区域是不是被父区域覆盖了

## Toast 不弹

先跑：

```text
/ws toast diagnose <区域ID>
```

再看：

- Toast 开关有没有开
- 图标物品是不是当前版本存在
- 玩家是不是已经发现过这个区域
- 你是不是应该用 `/ws toast test <区域ID>` 做预览

## 怪物不刷

先看规则：

```text
/ws spawn list
```

再单独测试：

```text
/ws spawn test <规则ID>
```

常见原因：

- MythicMobs 没装
- MM 怪物 ID 写错
- 区域里没有安全落点
- 最大存活已经满了
- 附近没有玩家

## Placeholder 不显示

先测：

```text
/papi parse me %worldscript_region_name%
```

如果这里正常，但 HUD/计分板不显示，那就是对应显示插件没解析 PAPI。
