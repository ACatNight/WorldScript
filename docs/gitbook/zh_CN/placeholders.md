# PlaceholderAPI

安装 PlaceholderAPI 后，WorldScript 会注册 `%worldscript_*%` 占位符。

## 常用变量

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

## 自定义区域变量

如果区域配置里有：

```yaml
variables:
  biome: forest
```

可以使用：

```text
%worldscript_biome%
%worldscript_region_var_biome%
%worldscript_parent_biome%
%worldscript_child_biome%
```

## 测试

```text
/papi parse me %worldscript_region_name%
```

如果原样返回，请确认显示插件本身支持 PlaceholderAPI。

