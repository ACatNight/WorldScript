# 配置文件

WorldScript 会把配置拆到不同文件，避免全部堆在一个 `config.yml`。

常见目录：

```text
plugins/WorldScript/
├─ config.yml
├─ settings/
├─ lang/
├─ regions/
└─ modules/
```

## 区域文件

区域文件位于：

```text
plugins/WorldScript/regions/
```

推荐使用 `schema: 2`，按身份、位置、状态、变量和事件组织。

## 语言文件

语言文件位于：

```text
plugins/WorldScript/lang/
```

支持：

- `en_US.yml`
- `zh_CN.yml`
- `zh_TW.yml`

修改语言后执行：

```text
/ws reload
```

