# WorldScript 发布修复报告（2026-10-12）

本次已修复发布复审中的构建问题、6 项高优先级缺陷，以及经济配置、GUI 物品丢失、模块依赖、全局刷怪上限和退出缓存问题。保留了工作区已有的服务接口注册、多边形编辑和文档修改。

## 修复结果

| 问题 | 现在的行为 |
|---|---|
| TabooLib 依赖无法解析 | 固定使用仓库已发布的 `6.3.0-75b18a2`，编译依赖和运行时版本一致 |
| 新旧 Bukkit API 二进制差异 | 编译基线为 Spigot 1.12.2；InventoryView、Block passability 和 Sound 解析使用兼容适配 |
| repeat 事件始终跳过 | 通过进入条件后先记录进入进度，再判断本次是否执行动作 |
| 满背包吞掉 once 奖励 | 剩余物品掉落到玩家脚边，投递完成后记录领取；经济/指令返回失败时不记录领取 |
| 取消/改目的地后误触发区域 | 准入检查与区域副作用分开；下一 tick 按在线玩家实际位置执行发现、进入和离开，并合并同 tick 的重复更新 |
| 重载刷怪突破存活限制 | 保留有效规则的存活追踪，执行全局存活上限；禁用、删除和关闭清理归属实体 |
| 卸载区块中的实体丢失追踪 | 区块卸载期间保留追踪；待移除实体记录在 pending-removals.yml，重新加载区块时清理，记录可跨正常重启保留 |
| 非 ID 文件名保存丢失/删除复活 | 保存回加载时的原文件，使用临时文件替换；删除同时处理重复 ID 来源；创建新区域避免覆盖其他区域的自定义文件名 |
| 自定义经济命令无效 | 读取合并后的 `economy.economy-command`，管理员仍在 settings/economy.yml 编辑 economy-command |
| 底部背包 Shift 点击丢物品 | 两个管理菜单先取消所有库存点击，再判断顶部槽位 |
| 依赖失败仍加载外置模块 | 每个硬依赖必须处于 BUILTIN 或 ENABLED，禁用/失败/循环依赖会阻止消费者启用 |
| PvP 冷却缓存累积 | 玩家退出时移除对应 UUID |
| 变量大小写更新读到旧值 | 写入时移除同名的大小写变体，读取与更新语义一致 |

模块示例的编译依赖已更新到 WorldScript-1.0.0.jar。中英文说明与更新记录同步了奖励溢出、刷怪清理和构建要求。

## 验证证据

- `gradlew.bat clean build --console=plain`：BUILD SUCCESSFUL，10 个任务执行。
- JUnit：19/19 通过，其中原有核心测试 5 项，新发布缺陷回归测试 14 项；无失败、无错误。
- `runWorldScriptTests`：12/12 组逻辑检查通过。
- `runModernCompatibilityTests`：在 Java 21 和真实 Paper 1.21.8 API 类路径下，InventoryView 接口与 Block passability 适配检查通过。该检查是独立进程 API 检查，并非服务端冒烟测试。
- legacy API 回归：在 Spigot 1.12.2 类路径下验证 InventoryView 抽象类、旧版 Block 回退和旧版音效名称。
- 模块示例：`gradlew.bat -p examples/modules/hello-worldscript-module clean jar --console=plain` 构建通过。
- 打包检查：全部非 multi-release class 字节码不高于 Java 8；plugin.yml 版本为 1.0.0；Kether 和重定位后的 bStats 存在；没有打入 Bukkit API、JUnit、Mockito 或新增测试类。
- GUI 字节码检查：菜单使用 BukkitCompatibility.topInventory，不再直接调用 InventoryView.getTopInventory。

## 构建产物

主插件：`build/libs/WorldScript-1.0.0.jar`

本次验证构建的 SHA-256：`74EF69B5965236960CFCE4A7B6B2161F57E280B10E249946214A19F82BA9B22B`。重新打包的归档时间戳可能变化，最终交付包以其实际校验值为准。

模块示例：`examples/modules/hello-worldscript-module/build/libs/worldscript-hello-0.1.0.jar`

## 验证范围

本次完成源码修复、本地回归、API 适配验证和打包检查。没有启动真实 Paper 服务器，因此没有宣称覆盖全版本服务端启动、GUI 实际画面、MythicMobs 实服刷怪或客户端 Toast 显示。发布到实际服务器前仍需对目标版本执行这些冒烟检查。待清理记录仅覆盖正常关闭/重启，不代表异常断电后的完整实体恢复。
