# WorldScript 外置模块示例

这是一个最小可用的 WorldScript 外置模块模板。它演示：

- `module.yml` 如何声明模块 ID、版本、API 版本、入口类和依赖。
- 入口类如何实现 `WorldScriptModule`。
- 如何通过 `ModuleContext` 注册 Bukkit 监听器。
- 如何读取并保存模块自己的 `plugins/WorldScript/modules/hello/config.yml`。

## 构建

先在主项目构建一次 WorldScript：

```text
gradlew.bat build
```

然后进入本目录构建示例模块：

```text
cd examples/modules/hello-worldscript-module
..\..\..\gradlew.bat -p . jar
```

生成的 JAR 位于：

```text
examples/modules/hello-worldscript-module/build/libs/worldscript-hello-0.1.0.jar
```

## 安装测试

1. 将 `worldscript-hello-0.1.0.jar` 放入服务器的 `plugins/WorldScript/modules/`。
2. 修改 `plugins/WorldScript/settings/modules.yml`：

```yaml
load-external: true
```

3. 重启服务器，或执行 `/ws modules reload`。
4. 执行 `/ws modules list`，应看到 `hello` 状态为已启用。

如果状态是失败，请执行：

```text
/ws modules info hello
```

重点检查 `api-version`、`worldscript-version`、`main` 和入口类的 `id`。
