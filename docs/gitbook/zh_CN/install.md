# 安装与兼容

## 安装

1. 从 [SpigotMC](https://www.spigotmc.org/resources/worldscript-1-12-2-1-21-8-%EF%B8%8Frpg-region-framework-%E2%9A%A1dynamic-events-player-progression%E2%9A%A1.138114/) 下载 `WorldScript-1.0.0.jar`。
2. 放入服务器的 `plugins/` 目录。
3. 启动服务器一次，生成 `plugins/WorldScript/`。
4. 执行 `/ws validate` 检查配置。
5. 修改配置或区域文件后执行 `/ws reload`。

TabooLib 与 Kether 已经打包在 WorldScript 内，不需要额外安装 TabooLib。

## 可选依赖

- PlaceholderAPI：用于 HUD、计分板、Tab 等变量显示。
- MythicMobs：用于 Spawn 模块从 MM 怪物库选择并生成怪物。

## 兼容目标

WorldScript 目标兼容 Paper 1.12.2 至 1.21.8。

不同服务端版本建议使用对应 Java：

- 1.12.2 - 1.16.x：Java 8
- 1.17 - 1.20.4：Java 17
- 1.20.5 - 1.21.8：Java 21

发布到正式服前，请至少在目标服务端版本做一次冒烟测试。
