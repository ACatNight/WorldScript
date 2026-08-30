# WorldScript

WorldScript 是给 Paper 服务器做“区域玩法”的插件。

你可以把地图切成一个个区域：森林入口、主城广场、副本门口、Boss 房、隐藏洞穴……玩家走进去以后，插件可以自动触发提示、音效、事件、条件判断、解锁进度，也可以在这个区域里刷怪。

简单说：任务插件负责“任务怎么走”，WorldScript 负责“玩家到了哪里，这里该发生什么”。

## 它能帮你做什么？

- 圈普通区域，也能圈不规则多边形区域
- 做父子区域，比如“大森林”下面有“入口”“遗迹”“狼王洞穴”
- 玩家进入、离开、左键、右键时触发动作
- 设置进入条件，条件不够就进不去
- 首次发现区域时弹 Title、Toast、音效和奖励
- 用聊天栏编辑器改配置，不用一直翻 YAML
- 给 HUD、计分板、Tab 提供 PlaceholderAPI 变量
- 在区域里随机刷怪，支持 MythicMobs 怪物库点选
- 用 `modules/` 目录管理后续模块扩展

## 下载

- [SpigotMC 下载 WorldScript](https://www.spigotmc.org/resources/worldscript-1-12-2-1-21-8-%EF%B8%8Frpg-region-framework-%E2%9A%A1dynamic-events-player-progression%E2%9A%A1.138114/)

## 讨论与反馈

遇到问题、想提建议，或者想聊后续功能，可以用这两个入口：

- [加入 Discord 讨论](https://discord.gg/NPSwPHG9R)
- 发邮件到 `acatnight@gmail.com`

## 第一次看文档，从这里开始

1. [安装与兼容](install.md)
2. [快速开始](quick-start.md)
3. [区域系统](regions.md)
4. [编辑器](editor.md)
5. [Toast 与发现提示](discovery-toast.md)
6. [Spawn 怪物刷新](spawn.md)
