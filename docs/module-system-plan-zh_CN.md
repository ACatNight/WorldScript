# WorldScript 模块系统

> 状态：0.1.0 起步实现
>
> 当前版本已经提供模块目录、官方基础模块生成、`module.yml` 扫描、依赖排序和 `/ws modules` 诊断命令。现有 Editor、Toast、Atmosphere、RPG、Placeholder 能力仍随主插件内置运行，后续再逐步迁出为可执行外置模块。

## 目标

WorldScript 主插件内置模块加载系统。管理员可以把官方发布的功能模块 JAR 放入指定目录，由 WorldScript 在启动时识别并加载，从而按需扩展插件功能。

模块可以承载区域保护、Web 管理、第三方插件联动等非核心功能。主插件核心继续专注于区域管理、区域发现和 RPG 世界区域编排。

## 当前已实现

### 目录

```text
plugins/WorldScript/modules/
├── worldscript-core.jar
├── worldscript-editor.jar
├── worldscript-toast.jar
├── worldscript-atmosphere.jar
├── worldscript-rpg.jar
├── worldscript-placeholder.jar
└── disabled/
```

第一次启动时，如果 `settings/modules.yml` 中 `auto-install-official` 为 `true`，主插件会自动生成这些官方基础模块描述 JAR。

### 配置

```yaml
auto-install-official: true
load-external: false
disabled: []
```

- `auto-install-official`：缺失官方基础模块 JAR 时自动生成。
- `load-external`：是否执行带 `main` 类的外置模块，默认关闭。
- `disabled`：预留给后续外置模块禁用。当前官方基础模块仍由主插件内置运行，不会关闭现有功能。

### 命令

```text
/ws modules list
/ws modules info <模块ID>
/ws modules reload
```

`reload` 会重新扫描模块描述，并调用已加载外置模块的停用流程。完整替换外置模块 JAR 仍建议重启服务器。

### 模块描述

模块 JAR 根目录需要包含 `module.yml`：

```yaml
id: toast
name: WorldScript Toast
version: 0.1.0
api-version: 1
worldscript-version: ">=0.1.0"
main: ""
official: true
builtin: true
required: false
dependencies:
  - core
soft-dependencies: []
```

当前官方基础模块使用 `builtin: true`，代表功能仍由主插件内置提供。未来外置模块可填写 `main` 指向实现 `WorldScriptModule` 的入口类。

## 已确定的规则

### 官方基础模块默认生成

- `core` 为 required 模块，不允许通过配置禁用。
- `editor`、`toast`、`atmosphere`、`rpg`、`placeholder` 默认生成并显示为内置模块。
- 缺失、格式不合法或版本不兼容的模块只会影响自身状态，不应导致主插件整体启动失败。

### 模块依赖

- 当前已经按 `dependencies` 做依赖排序。
- 缺失依赖会让对应模块进入失败状态。
- `soft-dependencies` 仅用于记录和诊断，不阻塞加载。

### 独立配置

每个可执行外置模块可以拥有自己的目录和配置文件，避免模块之间或模块与主插件核心配置互相污染。

```text
plugins/WorldScript/modules/protection/
├── WorldScript-Protection.jar
├── config.yml
└── messages.yml
```

模块配置、消息和资源应由模块自己管理。删除或更新模块时，不应要求修改主插件的核心配置文件。

## 生命周期建议

当前接口为：

```kotlin
interface WorldScriptModule {
    val id: String
    fun onLoad(context: ModuleContext)
    fun onEnable()
    fun onReload()
    fun onDisable()
}
```

模块可以通过 `ModuleContext` 访问主插件、日志、服务注册表、监听器注册和模块私有配置入口。

## 非目标

本规划暂不包含：

- 默认执行第三方任意 JAR
- 模块市场或在线下载
- 不重启服务器的完整热更新
- 将所有现有功能拆分为模块

## 后续讨论项

后续仍需确定模块签名或官方身份校验方式、模块配置热重载策略、第三方模块 API 的稳定性和版本迁移规则。
