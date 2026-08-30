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

