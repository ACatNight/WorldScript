# WorldScript

WorldScript 是一个面向 Paper 1.12.2 至 1.21.8 的区域脚本插件，用于配置地图区域、区域事件和玩家区域进度。

## 功能

- 父区域与子区域，支持变量、状态和事件继承
- 可选区域氛围粒子，子区域可以覆盖父区域效果
- 进入、离开、方块交互事件
- 玩家解锁、首次进入、完成和一次性奖励状态
- 条件、动作、奖励和 PlaceholderAPI HUD 变量
- 选区工具、区域列表、聊天编辑器和 `/ws validate` 配置检查
- 内置 TabooLib 6.3.0 与 Kether，可在区域事件中执行高级脚本
- Kether 可读取玩家变量与区域变量

## 安装

1. 将 `build/libs/WorldScript-<版本>.jar` 放入服务器的 `plugins/` 目录。
2. 启动一次服务器，让插件生成 `plugins/WorldScript/regions/`。
3. 将 `examples/region-progression-template.yml` 复制到 `regions/` 作为 Schema 2 开放世界示例。
4. 按服务器实际地图修改世界名、区域边界和外部插件命令。
5. 先执行 `/ws validate`，确认无错误后再执行 `/ws reload`。

完整的五区域开放世界示例位于 [examples/regions](examples/regions)。

默认语言为英文。若要切换简体中文或繁体中文，在 `plugins/WorldScript/config.yml` 中设置：

```yaml
language: zh_CN
# 或：zh_TW
```

修改后执行 `/ws reload`。语言文件位于 `plugins/WorldScript/lang/`；自定义语言文件名称只能使用英文、数字、`_` 和 `-`。

区域进入和离开提示默认关闭，避免玩家在区域边界附近反复移动时刷屏。如需开启，在配置中设置：

```yaml
messages:
  region-enter-enabled: true
  region-leave-enabled: true
```

## 管理命令

```text
/ws wand                         获取区域选择工具
/ws create <名称>                使用选择范围创建区域
/ws delete <名称>                删除区域
/ws list                         列出区域
/ws info <名称>                  查看区域信息
/ws edit <区域ID>                打开聊天区域编辑器
/ws edit <区域ID> events         编辑区域事件
/ws list                         打开主区域编辑器
/ws reload                       重新加载配置
/ws validate [区域ID]            检查全部或指定区域的配置错误
/ws progress <玩家> <区域> <状态> 回写玩家区域状态
```

`/ws progress` 供外部插件回写玩家的区域解锁或完成状态。WorldScript 不管理任务定义和任务步骤。

## 文档

- [中文 Wiki](docs/wiki-zh_CN.md)
- [配置参考](docs/config-reference-zh_CN.md)
- [外部整合](docs/integration-zh_CN.md)
- [开放世界示例](examples/region-progression-template.yml)

## 构建与验证

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

构建产物位于 `build/libs/WorldScript-<版本>.jar`。`check` 会自动执行项目内的逻辑检查。

兼容目标为 Paper 1.12.2 至 1.21.8。不同 Minecraft 版本需要使用对应 Java：1.12.2-1.16.x 使用 Java 8，1.17-1.20.4 使用 Java 17，1.20.5-1.21.8 使用 Java 21。当前代码已降为 Java 8 字节码并加入旧版材质回退，但发布前仍需要在各版本服务端进行冒烟测试。

## 许可证

WorldScript 当前完全免费，并使用 [MIT License](LICENSE) 开源发布。

TabooLib 与 Kether 已随 WorldScript 打包，不需要服务器另外安装 TabooLib。简单逻辑建议使用原生动作，复杂逻辑再使用 `type: kether`。
