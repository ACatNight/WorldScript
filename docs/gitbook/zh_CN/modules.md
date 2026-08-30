# 模块系统

WorldScript 1.0.0 会在插件目录生成：

```text
plugins/WorldScript/modules/
```

默认生成官方基础模块描述 JAR：

```text
worldscript-core.jar
worldscript-editor.jar
worldscript-toast.jar
worldscript-atmosphere.jar
worldscript-spawn.jar
worldscript-rpg.jar
worldscript-placeholder.jar
```

这些官方模块目前仍由主插件内置运行。模块 JAR 主要用于模块识别、状态诊断和后续外置模块扩展。

## 配置

```yaml
auto-install-official: true
load-external: false
disabled: []
```

外置模块默认不执行。只有管理员明确设置 `load-external: true` 后，WorldScript 才会加载外置模块入口类。

## 查看状态

```text
/ws modules list
/ws modules info spawn
```

