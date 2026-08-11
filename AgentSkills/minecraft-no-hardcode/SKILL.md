---
name: no-hardcode
description: Minecraft 插件反硬编码规范。当 Agent 编写业务代码、算法逻辑、GUI 界面、命令反馈时自动触发。要求所有面向玩家的文本走 lang 多语言系统，所有可调参数走 config.yml，所有 Material/Sound 等资源 ID 走配置文件。Agent 写代码时必须同步补充对应的 lang 和 config 条目。
---

# 反硬编码规范（Minecraft 插件通用）

## 核心原则

**零硬编码**：业务代码中不允许出现面向玩家的文本字面量、可调数值常量、Minecraft 资源 ID。它们必须外置到配置文件中，让服务器管理员无需改代码即可调整。

---

## 三类硬编码及其处理

### 1. 面向玩家的文本 → `lang/` 多语言文件

**什么算面向玩家的文本**：
- 聊天消息、ActionBar、Title、BossBar 文本
- GUI 标题、按钮名称、Lore 描述
- 命令反馈（成功/失败/用法提示）
- 通知、提醒、错误提示

**规则**：
- 使用 TabooLib 的 `sendLang("key", ...args)` 发送消息，不用 `sendMessage("硬编码文本")`
- lang key 命名格式：`模块-动作-场景`，如 `shop-purchase-success`、`voyage-start-no-fuel`
- 支持占位符：`{0}`, `{1}` 或命名占位符
- GUI 标题和 Lore 在 GUI YAML 中定义，不在代码中拼接

**禁止**：
```kotlin
// ❌ 硬编码文本
player.sendMessage("§a购买成功！花费了 ${price} 金币")
player.sendMessage("§c你的金币不足！")

// ❌ 硬编码 GUI 标题
Bukkit.createInventory(null, 54, "§6商店")
```

**正确**：
```kotlin
// ✅ 走 lang 系统
player.sendLang("shop-purchase-success", price)
player.sendLang("shop-purchase-insufficient-currency")

// ✅ GUI 标题在 YAML 中定义
// gui/shop.yml → title: "&6商店"
```

**Agent 行为**：写代码时同步在 `lang/zh_CN.yml` 中添加对应条目：
```yaml
shop-purchase-success: "&a购买成功！花费了 {0} 金币"
shop-purchase-insufficient-currency: "&c你的金币不足！"
```

---

### 2. 可调数值 → `config.yml` 或模块配置文件

**什么算可调数值**：
- 游戏平衡参数（倍率、概率、阈值、冷却时间）
- 容量限制（背包大小、队伍上限、最大等级）
- 时间间隔（检查周期、刷新间隔、过期时间）
- 经济参数（价格、奖励数量、税率）
- 算法参数（权重、衰减系数、基础值）

**规则**：
- 全局参数放 `config.yml`
- 模块专属参数放模块自己的配置文件
- 代码中通过 Config 对象读取，提供合理默认值
- 配置 key 命名格式：`kebab-case`，层级用 YAML 嵌套

**禁止**：
```kotlin
// ❌ 硬编码数值
val CHECK_INTERVAL = 30L  // 秒
val MAX_TEAM_SIZE = 6
val SUCCESS_BASE_RATE = 0.7
val EXP_PER_LEVEL = 100 + (level - 1) * 50
```

**正确**：
```kotlin
// ✅ 从配置读取
val checkInterval = MainConfig.getLong("voyage.check-interval-seconds", 30)
val maxTeamSize = MainConfig.getInt("team.max-size", 6)
val baseRate = AreaConfig.getDouble("${areaId}.base-success-rate", 0.7)

// ✅ 升级公式参数可配置
val baseExp = MainConfig.getInt("progression.exp-per-level-base", 100)
val expPerLevel = MainConfig.getInt("progression.exp-per-level-increment", 50)
fun getRequiredExp(level: Int): Int = baseExp + (level - 1) * expPerLevel
```

**Agent 行为**：写代码时同步在对应配置文件中添加条目和注释：
```yaml
# config.yml
voyage:
  check-interval-seconds: 30    # 航海状态检查间隔（秒）

progression:
  exp-per-level-base: 100       # 升级所需基础经验
  exp-per-level-increment: 50   # 每级递增经验
```

---

### 3. Minecraft 资源 ID → 配置文件

**什么算资源 ID**：
- `Material`（物品/方块类型）
- `Sound`（音效）
- `Particle`（粒子效果）
- `Enchantment`（附魔）
- `PotionEffectType`（药水效果）

**规则**：
- 在 YAML 配置中用字符串定义，代码中解析
- 提供默认值兜底，避免配置缺失时崩溃

**禁止**：
```kotlin
// ❌ 硬编码 Material
val icon = ItemStack(Material.DIAMOND_SWORD)
player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
```

**正确**：
```kotlin
// ✅ 从配置读取
val materialName = config.getString("icon-material", "DIAMOND_SWORD")
val icon = ItemStack(Material.valueOf(materialName))

// ✅ 音效可配置
val soundName = config.getString("purchase-sound", "ENTITY_EXPERIENCE_ORB_PICKUP")
runCatching { player.playSound(player.location, Sound.valueOf(soundName), 1f, 1f) }
```

---

## 例外情况（允许硬编码）

以下场景允许直接写在代码中，不需要外置：

1. **内部日志**：`info()`, `warning()`, `DebugLogger.debug()` — 这些是给开发者看的，不需要多语言
2. **数据库表名和列名**：属于代码结构，不应让用户修改
3. **枚举值和常量名**：`VoyageStatus.SAILING` 等程序内部标识
4. **框架注解参数**：`@CommandHeader(name = "voyage")` 等框架要求的固定值
5. **YAML 配置的 key 名**：`config.getString("some-key")` 中的 key 本身

---

## Agent 工作流检查清单

每次编写或修改业务代码时，按以下清单自检：

```
写代码时：
├── [ ] 有 sendMessage / 字符串拼接给玩家看的文本？→ 改用 sendLang，补 lang 条目
├── [ ] 有数字字面量控制游戏逻辑？→ 改为从 config 读取，补 config 条目
├── [ ] 有 Material.XXX / Sound.XXX 字面量？→ 改为从配置读取
├── [ ] GUI 标题或 Lore 在代码中拼接？→ 移到 GUI YAML 文件中
└── [ ] 新增的 lang key 和 config key 是否有注释说明？→ 必须有

完成后：
├── [ ] lang/zh_CN.yml 中新增条目已添加
├── [ ] config.yml 或模块配置文件中新增条目已添加（含注释）
└── [ ] 输出本次新增的配置项清单供审核
```

---

## 输出格式

每次完成代码编写后，如果涉及新增配置项，在回复末尾附上清单：

```
📋 新增配置项：
- lang/zh_CN.yml: shop-purchase-success, shop-purchase-insufficient-currency
- config.yml: voyage.check-interval-seconds (默认: 30)
- modules/shop/gui/shop.yml: purchase-sound (默认: ENTITY_EXPERIENCE_ORB_PICKUP)
```
