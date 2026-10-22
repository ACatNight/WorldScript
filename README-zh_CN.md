<p align="center">
  <img src="docs/assets/readme/logo.png" width="112" height="112" alt="WorldScript 标志">
</p>

<h1 align="center">WorldScript</h1>

<p align="center">为 Minecraft 服务器配置区域事件、探索进度与区域遭遇。</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-36cbb5" alt="MIT 许可证"></a>
  <img src="https://img.shields.io/badge/platform-Paper-36cbb5" alt="Paper 平台">
  <img src="https://img.shields.io/badge/Minecraft-1.12.2–1.21.8-192431" alt="Minecraft 兼容目标 1.12.2–1.21.8">
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/worldscript-rpg-regions.138114/">SpigotMC</a> ·
  <a href="https://modrinth.com/plugin/worldscript">Modrinth</a> ·
  <a href="docs/gitbook/zh_CN/README.md">使用文档</a> ·
  <a href="CHANGELOG.md">更新记录</a> ·
  <a href="README.md">English</a>
</p>

## 项目介绍

WorldScript 是面向 Paper 服务器的区域玩法插件。为主城、森林或副本入口定义边界，再配置玩家进入、离开或交互时发生的事件。

通过命令、PlaceholderAPI 和 Kether 脚本，将区域接入已有的任务、NPC、经济与战斗系统。WorldScript 管理区域与玩家区域进度；任务定义与任务步骤由任务插件负责。

## 核心功能

| | 功能 | 可配置内容 |
| --- | --- | --- |
| <img src="docs/assets/readme/regions.svg" width="28" alt=""> | **区域与层级** | 长方体和多边形边界、优先级、父子区域及支持的配置继承。 |
| <img src="docs/assets/readme/events.svg" width="28" alt=""> | **事件与脚本** | 进入、离开和方块交互事件，条件、冷却、动作与 Kether 脚本。 |
| <img src="docs/assets/readme/progress.svg" width="28" alt=""> | **探索与进度** | 玩家解锁、首次进入、完成、一次性奖励和原生成就样式 Toast。 |
| <img src="docs/assets/readme/spawn.svg" width="28" alt=""> | **区域遭遇** | 原版或 MythicMobs 刷怪、附近玩家检查、安全位置与存活上限。 |
| <img src="docs/assets/readme/protect.svg" width="28" alt=""> | **保护与氛围** | 区域 PVP 策略、氛围粒子，以及供 HUD 使用的 PlaceholderAPI 数据。 |
| <img src="docs/assets/readme/editor.svg" width="28" alt=""> | **游戏内配置** | 选区工具、区域列表 GUI、可点击聊天编辑器、YAML 文件与配置检查。 |

内置英文、简体中文和繁体中文。内置模块提供状态诊断；外置模块可以通过模块 API 扩展功能。

## 环境要求

兼容目标为 **Paper 1.12.2–1.21.8**。请以使用的服务端构建要求为准：

| Minecraft | 常见 Java 要求 |
| --- | --- |
| 1.12.2–1.16.x | Java 8 |
| 1.17.x | Java 16 |
| 1.18–1.20.4 | Java 17 |
| 1.20.5–1.21.8 | Java 21 |

- PlaceholderAPI：可选，用于支持占位符的 HUD、菜单和计分板。
- MythicMobs：可选，用于从 MythicMobs 怪物库生成怪物。
- TabooLib 与 Kether：已打包，无需另外安装。

自动化检查覆盖核心逻辑与新旧 API 适配，不能替代各版本服务端和第三方整合的实际运行测试。

## 安装

1. 从 [SpigotMC](https://www.spigotmc.org/resources/worldscript-rpg-regions.138114/) 或 [Modrinth](https://modrinth.com/plugin/worldscript) 下载插件 JAR。
2. 放入服务器的 `plugins/` 目录。
3. 重启服务器。

升级时先备份 WorldScript 配置与数据，停服替换旧 JAR 后重启。版本特定说明见[更新记录](docs/gitbook/zh_CN/changelog.md)。

## 快速开始

获取选区工具：

```text
/ws wand
```

左键选择第一个角，右键选择第二个角，然后创建并编辑区域：

```text
/ws create forest_entrance Forest Entrance
/ws edit forest_entrance
```

添加进入动作，再走出并重新进入区域测试。使用 `/ws list` 打开区域列表 GUI。管理命令需要 `worldscript.admin` 权限，默认授予 OP。

区域保存到 `plugins/WorldScript/regions/forest_entrance.yml`。手动修改 YAML 后，先执行 `/ws validate`，再执行 `/ws reload`。

默认语言为 `en_US`。在 `plugins/WorldScript/config.yml` 中设置 `language: zh_CN` 或 `language: zh_TW`，然后执行 `/ws reload`。

## 文档导航

| 主题 | 文档 |
| --- | --- |
| 安装与配置 | [安装](docs/gitbook/zh_CN/install.md) · [配置](docs/gitbook/zh_CN/config.md) |
| 区域与编辑 | [区域](docs/gitbook/zh_CN/regions.md) · [编辑器](docs/gitbook/zh_CN/editor.md) · [命令](docs/gitbook/zh_CN/commands.md) |
| 玩法 | [发现 Toast](docs/gitbook/zh_CN/discovery-toast.md) · [刷怪](docs/gitbook/zh_CN/spawn.md) · [PVP 保护](docs/gitbook/zh_CN/protect.md) |
| 整合与扩展 | [占位符](docs/gitbook/zh_CN/placeholders.md) · [模块](docs/gitbook/zh_CN/modules.md) · [模块示例](examples/modules/hello-worldscript-module/README.md) |
| 示例与排错 | [区域示例](examples/regions) · [故障排查](docs/gitbook/zh_CN/troubleshooting.md) |

完整资料分类见[文档索引](docs/README.md)。

## 开发与构建

安装 JDK 8 和 JDK 21。插件编译为 Java 8 字节码，现代 API 兼容测试使用 Java 21。

```powershell
# Windows
.\gradlew.bat clean build
```

```sh
# Linux / macOS
./gradlew clean build
```

构建包含 JUnit 回归测试、WorldScript 逻辑检查和 Paper 1.21.8 API 兼容测试。产物为 `build/libs/WorldScript-<版本>.jar`。

贡献流程与验证说明见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 使用统计

WorldScript 使用 bStats 统计使用情况，没有添加自定义统计图表。服主可在 `plugins/bStats/config.yml` 中设置 `enabled: false` 并重启服务器，关闭统计。

## 支持与许可证

请在 [GitHub Issues](https://github.com/ACatNight/WorldScript/issues) 提交可复现问题，附上插件版本、服务端构建、Java 版本、复现步骤和相关日志。

WorldScript 免费开源，使用 [MIT License](LICENSE)。
