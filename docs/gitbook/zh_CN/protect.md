# Protect 区域保护

Protect 模块第一期先管 PVP。

它的规则很简单：看玩家所在区域的状态，决定这里能不能打人。

## 默认规则

- `peaceful`：禁止 PVP
- `dangerous`：允许 PVP
- 没写状态：按默认配置处理，默认允许

主城、安全区这类地方，可以这样写：

```yaml
state:
  statuses: [peaceful]
```

野外、战场、危险区，可以这样写：

```yaml
state:
  statuses: [dangerous]
```

## 配置文件

文件位置：

```text
plugins/WorldScript/settings/protect.yml
```

默认配置：

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

如果你想默认都不能 PVP，只允许危险区打人，可以改成：

```yaml
pvp:
  default-allow: false
  allowed-statuses:
    - dangerous
```

## 怎么测试？

站在区域里执行：

```text
/ws protect test
```

也可以测试别人：

```text
/ws protect test <玩家>
```

改完配置后执行：

```text
/ws protect reload
```

## 判断方式

攻击者和被攻击者都会检查。

只要有一方站在禁止 PVP 的区域里，这次伤害就会被拦住。这样可以避免有人站在安全区外面，隔着边界打安全区里面的人。

