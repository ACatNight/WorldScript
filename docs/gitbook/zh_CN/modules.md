# 模块系统

WorldScript 1.0.0 会生成一个模块目录：

```text
plugins/WorldScript/modules/
```

默认会有这些官方模块描述：

```text
worldscript-core.jar
worldscript-editor.jar
worldscript-toast.jar
worldscript-atmosphere.jar
worldscript-spawn.jar
worldscript-protect.jar
worldscript-rpg.jar
worldscript-placeholder.jar
```

先说明白：这些官方模块现在还是由主插件内置运行。这里的 JAR 主要是为了模块识别、状态查看和以后做外置模块。

## 配置

```yaml
auto-install-official: true
load-external: false
disabled: []
```

`load-external` 默认是 `false`，这是故意的。不要让服务器随便加载未知 JAR。

你真的要加载外置模块，再把它改成：

```yaml
load-external: true
```

## 看模块状态

```text
/ws modules list
/ws modules info spawn
```

如果模块显示失败，先看 `info` 给出的原因。
