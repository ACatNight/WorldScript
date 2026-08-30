# 配置文件

WorldScript 不建议把所有东西都堆进一个 `config.yml`，所以配置是拆开的。

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

区域放这里：

```text
plugins/WorldScript/regions/
```

新区域建议用 `schema: 2`。结构会清楚很多，后面维护也轻松。

## settings

`settings/` 里放全局功能配置，比如模块、编辑器、Toast、粒子这类东西。

你平时不一定要手改它们，大部分内容可以在游戏里用编辑器改。

## 语言文件

语言文件放这里：

```text
plugins/WorldScript/lang/
```

内置：

- `en_US.yml`
- `zh_CN.yml`
- `zh_TW.yml`

修改语言后执行：

```text
/ws reload
```

自己改语言文件时，不要改键名和 `%placeholder%`，只改显示文本。
