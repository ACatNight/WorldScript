# WorldScript

WorldScript 是一个面向 Paper 1.21.8 的区域脚本插件，用于配置地图区域、区域事件和玩家区域进度。

## 功能

- 父区域与子区域，支持变量、状态和事件继承
- 进入、离开、方块交互事件
- 玩家解锁、首次进入、完成和一次性奖励状态
- 条件、动作、奖励和 PlaceholderAPI HUD 变量
- 选区工具、管理命令、GUI 和 `/ws validate` 配置检查

## 安装

1. 将 `build/libs/WorldScript-<版本>.jar` 放入服务器的 `plugins/` 目录。
2. 启动一次服务器，让插件生成 `plugins/WorldScript/regions/`。
3. 将 `examples/region-progression-template.yml` 复制到 `regions/` 作为开放世界示例。
4. 按服务器实际地图修改世界名、区域边界和外部插件命令。
5. 先执行 `/ws validate`，确认无错误后再执行 `/ws reload`。

默认语言为英文。若要切换中文消息，在 `plugins/WorldScript/config.yml` 中设置：

```yaml
language: zh_CN
```

修改后执行 `/ws reload`。语言文件位于 `plugins/WorldScript/lang/`；自定义语言文件名称只能使用英文、数字、`_` 和 `-`。

## 管理命令

```text
/ws wand                         获取区域选择工具
/ws create <名称>                使用选择范围创建区域
/ws delete <名称>                删除区域
/ws list                         列出区域
/ws info <名称>                  查看区域信息
/ws gui                          打开管理界面
/ws reload                       重新加载配置
/ws validate                     检查配置错误
/ws progress <玩家> <区域> <状态> 回写玩家区域状态
```

`/ws progress` 供外部插件回写玩家的区域解锁或完成状态。WorldScript 不管理任务定义和任务步骤。

## 文档

- [配置参考](docs/config-reference-zh_CN.md)
- [外部整合](docs/integration-zh_CN.md)
- [开放世界示例](examples/region-progression-template.yml)

## 构建与验证

```text
gradlew.bat runWorldScriptTests
gradlew.bat clean build
```

构建产物位于 `build/libs/WorldScript-<版本>.jar`。`check` 会自动执行项目内的逻辑检查。

## 许可证

WorldScript 当前完全免费，并使用 [MIT License](LICENSE) 开源发布。
