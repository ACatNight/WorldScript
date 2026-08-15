# WorldScript 外部整合

WorldScript 不管理任务定义或任务过程。Chemdah 等外部插件负责内容本身；WorldScript 只记录玩家与地点之间的解锁、进入和完成状态。

## 命令回写

外部插件可从控制台执行：

```text
ws progress <玩家> <区域> unlock
ws progress <玩家> <区域> complete
```

目标玩家可以离线，但必须曾经进入过服务器。区域 ID 必须已经存在。

## Kotlin API

在服务器主线程中取得插件实例后，可用 UUID 写入玩家区域进度：

```kotlin
val worldScript = server.pluginManager.getPlugin("WorldScript") as? com.worldscript.WorldScriptPlugin
worldScript?.playerProgress?.unlockRegion(playerId, "danger_canyon")
worldScript?.playerProgress?.markRegionCompleted(playerId, "sunken_ruins")
```

可用方法：

- `isRegionUnlocked(playerId, regionId)`
- `unlockRegion(playerId, regionId)`
- `hasEnteredRegion(playerId, regionId)`
- `markRegionEntered(playerId, regionId)`
- `isRegionCompleted(playerId, regionId)`
- `markRegionCompleted(playerId, regionId)`

不要从异步线程调用这些方法。若外部插件在异步回调中收到结果，请先切回服务器主线程。
