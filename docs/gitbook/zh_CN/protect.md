# Protect 区域保护

这个功能用来处理最常见的一件事：主城不准打人，野外可以打。

不用单独给每个区域写一套保护规则，直接用区域状态就行。

## 主城禁 PVP

主城、安全区、出生点，一般写成 `peaceful`：

```yaml
state:
  statuses: [peaceful]
```

玩家在这里打人，伤害会被拦住。

## 野外允许 PVP

野外、战场、危险区，一般写成 `dangerous`：

```yaml
state:
  statuses: [dangerous]
```

玩家在这里可以正常打人。

如果区域没有写这两个状态，就按配置里的默认值走。默认是允许 PVP。

## 配置文件

一般不用改配置。你想改全服保护习惯时，再看这个文件：

```text
plugins/WorldScript/settings/protect.yml
```

```yaml
enabled: true
pvp:
  enabled: true
  default-allow: true
  blocked-statuses:
    - peaceful
  allowed-statuses:
    - dangerous
  message:
    enabled: true
    cooldown-ms: 1500
```

如果你的服务器希望“默认都不能打，只有危险区能打”，就把默认值改掉：

```yaml
pvp:
  default-allow: false
  allowed-statuses:
    - dangerous
```

## 测一下

站在区域里执行：

```text
/ws protect test
```

也可以测试别人：

```text
/ws protect test <玩家>
```

它会告诉你：玩家当前在哪个区域，这里现在是允许 PVP 还是禁止 PVP。

改过 `protect.yml` 后，执行：

```text
/ws protect reload
```

## 边界怎么处理？

攻击者和被攻击者都会检查。

只要有一方在安全区里，这次伤害就会被拦住。这样可以避免有人站在主城外面，隔着边界打主城里面的人。
