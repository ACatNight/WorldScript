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
/ws edit starter_valley
```

能点的地方尽量直接点。先别急着写 YAML，聊天栏编辑器已经能改大部分内容。

## 4. 测一下

```text
/ws validate starter_valley
/ws test starter_valley
```

没有报错的话，就进区域走一圈，看进入事件、Toast、粒子、刷怪是不是正常。
