# PlaceholderAPI

装了 PlaceholderAPI 以后，就能在 HUD、计分板、Tab、聊天里显示玩家当前区域。

## 先理解几个概念

### 当前区域

玩家脚下最精确的那个区域。

如果玩家同时站在“大森林”和“大森林里的狼王洞穴”里，当前区域通常是更深的子区域，也就是“狼王洞穴”。

### 父区域

当前区域外面那一层大区域。

比如：

```text
低语森林
└─ 狼王洞穴
```

玩家站在“狼王洞穴”时：

- 当前区域：狼王洞穴
- 父区域：低语森林

### 子区域

当前玩家所在路径里更具体的区域。做 HUD 时常用它来显示“玩家真正所在的小地点”。

### 区域变量

区域变量是你自己给区域写的自定义信息。

比如：

```yaml
variables:
  biome: forest
  danger_level: "3"
  short_name: 狼洞
```

这些变量不会自动改变游戏内容，它们更像“标签”和“数据”。你可以拿它们显示在 HUD 上，也可以给脚本和条件判断使用。

## 常用区域信息

这些是插件内置的，不需要你在 `variables:` 里写。

```text
%worldscript_region_id%
%worldscript_region_name%
%worldscript_region_role%
%worldscript_region_path%
%worldscript_parent_name%
%worldscript_child_name%
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
```

含义：

- `%worldscript_region_id%`：当前区域 ID，比如 `wolf_cave`
- `%worldscript_region_name%`：当前区域显示名，比如 `狼王洞穴`
- `%worldscript_region_role%`：区域角色，比如 `danger_zone`
- `%worldscript_region_path%`：完整路径，比如 `低语森林 / 狼王洞穴`
- `%worldscript_parent_name%`：父区域名字，比如 `低语森林`
- `%worldscript_child_name%`：当前最深子区域名字
- `%worldscript_region_unlocked%`：玩家是否解锁当前区域，返回 `true` 或 `false`
- `%worldscript_region_entered%`：玩家是否进入过当前区域
- `%worldscript_region_completed%`：玩家是否完成过当前区域

## 自定义变量怎么显示？

如果区域配置里有：

```yaml
variables:
  biome: forest
  danger_level: "3"
  short_name: 狼洞
```

可以使用：

```text
%worldscript_biome%
%worldscript_region_var_biome%
%worldscript_parent_biome%
%worldscript_child_biome%
```

区别：

- `%worldscript_biome%`：最短写法，优先读当前有效区域变量
- `%worldscript_region_var_biome%`：明确读取当前区域变量
- `%worldscript_parent_biome%`：读取父区域变量
- `%worldscript_child_biome%`：读取当前最深子区域变量

举个例子：

```yaml
# 低语森林
variables:
  biome: forest
  danger_level: "1"

# 狼王洞穴
identity:
  parent: whispering_forest
variables:
  danger_level: "5"
  short_name: 狼洞
```

玩家站在狼王洞穴时：

```text
%worldscript_biome%          -> forest
%worldscript_danger_level%   -> 5
%worldscript_parent_biome%   -> forest
%worldscript_parent_danger_level% -> 1
%worldscript_short_name%     -> 狼洞
```

也就是说：子区域写了同名变量，会覆盖父区域；子区域没写的变量，会从父区域继承。

## 玩家进度变量是什么意思？

这些变量是“每个玩家自己一份”的状态：

```text
%worldscript_region_unlocked%
%worldscript_region_entered%
%worldscript_region_completed%
```

比如 A 玩家发现过狼王洞穴，B 玩家没发现过，那么两个人看到的结果可以不一样。

常见用途：

- HUD 显示当前区域是否已发现
- 剧情服判断玩家有没有来过这里
- 任务插件完成后调用 `/ws progress` 解锁区域

## 测试

站在区域里执行：

```text
/papi parse me %worldscript_region_name%
/papi parse me %worldscript_region_path%
/papi parse me %worldscript_biome%
/papi parse me %worldscript_region_unlocked%
```

如果这里正常，但 HUD/计分板不显示，那通常不是 WorldScript 没给变量，而是对应显示插件没有解析 PlaceholderAPI。
