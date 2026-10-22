# 快速开始

先做一个最简单的区域。跑通以后，再慢慢加事件、Toast、刷怪。

## 1. 拿选区工具

```text
/ws wand
```

手里会出现选区工具。

- 左键：设置第一个点
- 右键：设置第二个点

两个点会组成一个立方体区域。

## 2. 创建区域

```text
/ws create starter_valley 初始山谷
```

这里：

- `starter_valley` 是区域 ID，后面指令和配置都用它
- `初始山谷` 是玩家看到的名字

## 3. 打开编辑器

```text
/ws list
/ws edit starter_valley
```

`/ws list` 打开区域列表 GUI，`/ws edit` 直接进入指定区域的聊天栏编辑器。可以先加入进入事件或发现提示，大部分常见设置不用手写 YAML。

## 4. 测一下

```text
/ws validate starter_valley
/ws test starter_valley
```

走出区域再走回来，检查进入事件是否生效。`/ws test` 只显示形状、进入事件设置、配置问题并预览 Toast，不会执行进入事件的动作。

## 5. 试试 1.0.1 新功能

不规则范围可以用[多边形编辑](regions.md#多边形区域)；发现提示在 `/ws edit starter_valley discovery` 里设置，并可[预览 Toast](discovery-toast.md)。基础区域正常后，再试 [Spawn 刷怪](spawn.md) 和 [Protect 区域保护](protect.md)。
