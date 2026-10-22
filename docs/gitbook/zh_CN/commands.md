# 指令大全

## 基础指令

```text
/ws help
/ws reload
/ws validate [区域ID]
```

## 区域管理

```text
/ws wand
/ws create <区域ID> [显示名称]
/ws delete <区域ID>
/ws list
/ws info <区域ID>
/ws edit <区域ID>
/ws polygon start <区域ID>
/ws polygon preview
/ws polygon undo
/ws polygon redo
/ws polygon finish
```

## 玩家进度

```text
/ws progress <玩家> <区域ID> unlock
/ws progress <玩家> <区域ID> complete
```

这些指令通常给任务插件、剧情插件或控制台命令调用。

## Toast 测试

```text
/ws toast test [玩家] <区域ID>
/ws toast diagnose <区域ID>
```

## 模块系统

```text
/ws modules list
/ws modules info <模块ID>
/ws modules enable <模块ID>
/ws modules disable <模块ID>
/ws modules reload
```

## Spawn 模块

```text
/ws spawn list
/ws spawn test <规则ID>
/ws spawn reload
```

## Protect 模块

```text
/ws protect test [玩家]
/ws protect reload
```

`test` 会告诉你玩家当前区域是否允许 PVP。

管理指令需要 `worldscript.admin` 权限，默认授予 OP。区域 GUI 用 `/ws list` 打开；没有 `/ws gui` 指令。
