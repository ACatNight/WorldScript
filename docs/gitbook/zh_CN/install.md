# 安装与兼容

## 怎么装？

1. 去 [SpigotMC](https://www.spigotmc.org/resources/worldscript-1-12-2-1-21-8-%EF%B8%8Frpg-region-framework-%E2%9A%A1dynamic-events-player-progression%E2%9A%A1.138114/) 下载 `WorldScript-1.0.0.jar`。
2. 把 jar 丢进服务器的 `plugins/`。
3. 启动一次服务器，让它生成 `plugins/WorldScript/`。
4. 执行：

```text
/ws validate
```

5. 没报错就可以开始做区域。以后改完配置，再执行：

```text
/ws reload
```

TabooLib 和 Kether 已经打包进插件，不用另外装。

## 哪些插件是可选的？

- PlaceholderAPI：想在 HUD、计分板、Tab、聊天里显示区域变量，就装它。
- MythicMobs：想用 Spawn 模块刷 MM 怪，就装它。

不装这两个也没关系，基础区域、事件、编辑器都能用。

## 版本怎么选？

WorldScript 目标兼容 Paper 1.12.2 到 1.21.8。

Java 建议：

- 1.12.2 - 1.16.x：Java 8
- 1.17 - 1.20.4：Java 17
- 1.20.5 - 1.21.8：Java 21

正式开服前，建议拿你自己的服务端版本实测一遍。尤其是 Toast、粒子、MythicMobs 这种跟版本关系比较近的功能。
