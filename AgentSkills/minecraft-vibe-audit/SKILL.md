---
name: vibe-audit
description: VibeCoding 静态审计循环。纯代码级审计，无需进入游戏测试。对已构建的 Minecraft 插件项目执行多维度评分，输出优先级修复清单，修复后重新审计直到达标。支持 40/70/90 三档目标分。
---

# VibeCoding 静态审计循环

## 你的角色

你是审计官。职责：对已构建的项目执行多维度静态分析，输出评分 + 优先级修复清单。你不写代码，只审计和分发修复任务。

## 核心循环

```
审计 → 评分 → 修复清单 → 分发修复 → 重新审计 → 直到达标
```

每轮审计产出：
1. 16 维度评分表（满分 160，折算百分制）
2. P0/P1/P2 优先级修复清单
3. 修复后的 delta 复查（只查改动项，不全量重审）

## 使用时机

| 触发条件 | 目标分 |
|----------|--------|
| R1 粗胚完成后 | 40 分 |
| R2 功能完成后 | 70 分 |
| R3 打磨完成后 | 90 分 |
| 任意修复批次完成后 | 上次目标分 |

## 16 维度评分体系

### 1. 编译检查 (10分)

```bash
cd "{project_path}" && ./gradlew.bat compileKotlin 2>&1 | tail -20
```
- 10分: BUILD SUCCESSFUL
- 0分: 编译失败

### 2. 测试覆盖 (10分)

```bash
# TestRunner 数量和签名
find "{project_path}/src/main/kotlin" -name "*TestRunner*" -type f
grep -rn "fun runAll" "{project_path}/src/main/kotlin/" --include="*.kt"
# TestCommand 注册
find "{project_path}/src/main/kotlin" -name "TestCommand*" -type f
```
- 10分: L1+L2 全覆盖，签名统一 `fun runAll(player: Player)`
- 7分: L1 全覆盖，签名统一
- 4分: 部分覆盖或签名不统一
- 0分: 无 TestRunner

### 3. TODO 残留 (10分)

```bash
# 功能性 TODO（扣分）
grep -rn "TODO" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -v "TODO\[R3\]"
# R3 标记（不扣分，已知技术债）
grep -rn "TODO\[R3\]" "{project_path}/src/main/kotlin/" --include="*.kt"
```
- 10分: 0 处功能性 TODO
- 7分: 1-3 处
- 4分: 4-10 处
- 0分: >10 处或有 TODO[R2] 残留

### 4. 事件契约闭合 (10分)

```bash
# 发布的事件
grep -rn "\.call()" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -oP "\w+Event" | sort -u
# 监听的事件
grep -rn "@SubscribeEvent" -A3 "{project_path}/src/main/kotlin/" --include="*.kt" | grep -oP "\w+Event" | sort -u
```
- 10分: 核心业务链全闭合
- 7分: 核心链闭合，边缘链有缺口
- 4分: 有核心链断裂
- 0分: 大面积未闭合

### 5. 反硬编码 (10分)

```bash
# sendMessage 硬编码（玩家可见文本）
grep -rn "sendMessage(" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -v "sendLang\|import\|//\|console"
# 硬编码资源 ID
grep -rn "Material\.\|Sound\.\|Particle\." "{project_path}/src/main/kotlin/" --include="*.kt" | grep -v "config\|Config\|import\|//\|\.AIR"
# 硬编码数值（概率/倍率/阈值）
grep -rn "[^a-zA-Z_]0\.\d\+\|[^a-zA-Z_][1-9]\d*\.\d" "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -v "config\|Config\|import\|//"
```
- 10分: 零硬编码
- 7分: 仅 console 日志或 fallback 默认值
- 4分: 有玩家可见硬编码文本
- 0分: 大面积硬编码

### 6. 资源泄漏 (10分)

```bash
# 有 per-player 缓存的文件
grep -rn "Map<UUID\|HashMap<UUID\|ConcurrentHashMap<UUID\|mutableMapOf<UUID" "{project_path}/src/main/kotlin/" --include="*.kt" -l
# 有 PlayerQuitEvent 清理的文件
grep -rn "PlayerQuitEvent" "{project_path}/src/main/kotlin/" --include="*.kt" -l
```
对比两个列表：有缓存的模块必须有对应的 Quit 清理。
- 10分: 所有缓存都有清理
- 5分: 有缓存无清理（内存泄漏）
- 额外检查: 清理方法是否真的被调用（防止死代码）

### 7. 交付文档 (10分)

```bash
find "{project_path}" -maxdepth 1 -name "_done_r*.md" -o -name "_handoff_r*.md" -o -name "_final_report.md"
```
- 10分: 当前轮次的 done + handoff/final_report 存在且内容准确
- 7分: 存在但自检清单与实际不符
- 0分: 不存在

### 8. Config/Lang 完整性 (10分)

```bash
# lang 条目数
wc -l "{project_path}/src/main/resources/lang/zh_CN.yml"
# config 条目数
wc -l "{project_path}/src/main/resources/config.yml"
# 模块配置文件数
find "{project_path}/src/main/resources/modules/" -name "*.yml" | wc -l
# GUI 配置文件数
find "{project_path}/src/main/resources/" -path "*/gui/*.yml" | wc -l
```
- 10分: lang/config/模块配置/GUI配置全部完整
- 7分: 缺少部分条目
- 4分: 大面积缺失

### 9. L2 模块功能完整性 (10分)

对每个 L2 模块执行入口活性检查：

```bash
# 平台入口事件是否有监听
grep -rn "@SubscribeEvent" "{project_path}/src/main/kotlin/*/modules/l2/" --include="*.kt" -A2 | grep -oP "\w+Event" | sort -u
# 装备扫描范围（是否只扫描 mainHand）
grep -rn "itemInMainHand\|itemInOffHand\|helmet\|chestplate\|leggings\|boots" "{project_path}/src/main/kotlin/*/modules/l2/" --include="*.kt" -l
# ConcurrentHashMap 使用
grep -rn "mutableMapOf<UUID\|HashMap<UUID" "{project_path}/src/main/kotlin/*/modules/l2/" --include="*.kt"
```
- 10分: 所有 L2 模块入口活跃，装备扫描完整，线程安全
- 7分: 个别模块有小问题
- 4分: 有模块是死代码（无平台事件监听）
- 0分: 多个模块结构性不完整

### 10. 管理员命令审核 (10分)

```bash
# 查找管理员命令注册
grep -rn "@CommandHeader\|@CommandBody\|subCommand\|registerCommand" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -i "admin\|manage\|reload\|give\|reset\|debug"
# 检查权限注解
grep -rn "permission\|Permission\|\.hasPermission\|@CommandHeader" "{project_path}/src/main/kotlin/*/modules/l3/admin" --include="*.kt"
# 检查 Tab 补全
grep -rn "suggest\|tabComplete\|TabComplete\|@CommandBody" -A5 "{project_path}/src/main/kotlin/*/modules/l3/admin" --include="*.kt" | grep -i "suggest\|complete"
```
- 10分: 所有管理员命令有权限检查 + Tab 补全 + 帮助文本 + 反馈消息走 lang
- 7分: 命令存在且有权限检查，缺 Tab 补全或帮助文本
- 4分: 命令存在但无权限检查
- 0分: 无管理员命令模块

### 11. 玩家命令入口审核 (10分)

```bash
# 查找玩家可用命令
grep -rn "@CommandHeader\|@CommandBody" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -v "admin\|debug\|test\|migration"
# 检查命令是否有对应的 GUI 入口或快捷方式
grep -rn "openGui\|openMenu\|GuiNavigator\|push(" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -v "import\|//"
# 检查命令反馈是否走 lang
grep -rn "sendLang\|sendMessage" "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -i "command\|cmd"
```
- 10分: 玩家有明确的命令入口（如 /enchant）打开主 GUI，命令反馈走 lang，有帮助子命令
- 7分: 有命令入口但缺帮助或反馈不走 lang
- 4分: 命令入口存在但功能不完整
- 0分: 玩家无任何命令入口

### 12. 最佳实践 — Matcher 使用审核 (10分)

```bash
# 查找条件判断逻辑（应该用 EasyLib-Matcher）
grep -rn "if.*level\|if.*permission\|if.*hasItem\|if.*condition\|if.*require\|if.*unlock" "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -v "import\|//"
# 查找是否使用了 Matcher
grep -rn "Matcher\|matcher\|MatcherGroup\|matcherGroup\|createMatcher" "{project_path}/src/main/kotlin/" --include="*.kt"
# 查找 config 中的条件配置（应该是 Matcher 格式）
grep -rn "conditions:\|requirements:\|matchers:" "{project_path}/src/main/resources/" --include="*.yml"
```
- 10分: 所有条件判断使用 EasyLib-Matcher，config 中条件用 Matcher DSL 格式
- 7分: 核心条件用了 Matcher，个别边缘逻辑用 if 硬编码
- 4分: 知道 Matcher 存在但大部分条件仍是 if 硬编码
- 0分: 完全没用 Matcher，所有条件都是 if/else 硬编码

### 13. 最佳实践 — GUI Matcher 渲染审核 (10分)

```bash
# 查找 GUI 中的条件渲染（应该用 gui-matcher-render）
grep -rn "gui-matcher-render\|GuiMatcherRender\|matcherRender\|conditionRender\|renderMatcher" "{project_path}/src/main/kotlin/" --include="*.kt"
# 查找 GUI 中手动条件渲染（反模式）
grep -rn "if.*lore\|if.*color\|if.*icon\|statusColor\|§a.*§c\|green.*red" "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -i "gui\|menu\|button\|slot"
# 查找 GUI YAML 中的条件渲染配置
grep -rn "condition-display:\|matcher-render:\|status-icon:" "{project_path}/src/main/resources/" --include="*.yml" -l
```
- 10分: 所有 GUI 条件状态展示使用 gui-matcher-render，条件满足/不满足的颜色/图标/lore 走配置
- 7分: 部分 GUI 用了 matcher-render，部分手动渲染
- 4分: 知道 matcher-render 但基本没用
- 0分: 所有 GUI 条件展示都是 if/else 硬编码颜色和 lore

### 14. 语言硬编码审核 (10分)

```bash
# 玩家可见的中文硬编码（最严重）
grep -rn '"§\|"&\|"[\u4e00-\u9fff]' "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -v "import\|//\|log\|info(\|warning(\|severe(\|debug("
# GUI 标题/lore 硬编码
grep -rn 'title.*=.*"\|lore.*=.*"\|name.*=.*"§' "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -v "import\|//\|val \|var "
# 对比 lang 条目数 vs sendLang 调用数
grep -rn "sendLang" "{project_path}/src/main/kotlin/" --include="*.kt" | wc -l
grep -c ":" "{project_path}/src/main/resources/lang/zh_CN.yml"
```
- 10分: 零中文/颜色码硬编码，所有玩家可见文本走 sendLang，lang 条目数 >= sendLang 调用数
- 7分: 个别 fallback 硬编码，主体走 lang
- 4分: 混合使用，部分走 lang 部分硬编码
- 0分: 大面积中文硬编码，lang 系统形同虚设

### 15. 界面美化程度审核 (10分)

```bash
# GUI 是否有音效反馈
grep -rn "playSound\|Sound\." "{project_path}/src/main/kotlin/*/modules/" --include="*.kt" | grep -i "gui\|menu\|click\|open\|close\|success\|fail"
# GUI 是否有分页
grep -rn "page\|Page\|PAGE_SIZE\|nextPage\|prevPage\|pagination" "{project_path}/src/main/kotlin/" --include="*.kt"
# GUI 是否有边框/装饰物品
grep -rn "border\|Border\|GLASS_PANE\|filler\|decoration" "{project_path}/src/main/kotlin/" --include="*.kt"
# GUI 是否有动态 lore（进度条、状态指示）
grep -rn "progressBar\|progress_bar\|██\|▮\|■\|░" "{project_path}/src/main/kotlin/" --include="*.kt"
# GUI 配置文件是否有布局定义
find "{project_path}/src/main/resources/" -path "*/gui/*.yml" -exec grep -l "layout\|slots\|border\|decoration" {} \;
```
- 10分: GUI 有音效反馈 + 分页 + 边框装饰 + 动态 lore + 布局走配置
- 7分: 有音效和分页，缺装饰或动态 lore
- 4分: 纯功能性 GUI，无任何美化
- 0分: GUI 布局混乱，无反馈

### 16. 事件链路缺失审核 (10分)

```bash
# 读取 event-contract.md 中声明的所有事件
grep -oP "\| \w+Event" "{project_path}/.claude/docs/foundation/event-contract.md" | sort -u | wc -l
# 实际代码中定义的事件类
find "{project_path}/src/main/kotlin" -name "*Event.kt" -type f | wc -l
grep -rn "class \w*Event\b" "{project_path}/src/main/kotlin/" --include="*.kt" | wc -l
# 定义了但从未 .call() 的事件（死事件）
# 步骤：提取所有事件类名 → 对比 .call() 中出现的事件名 → 差集 = 死事件
grep -rn "class \w*Event" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -oP "class (\w+Event)" | sort -u > /tmp/defined_events.txt
grep -rn "\.call()" "{project_path}/src/main/kotlin/" --include="*.kt" | grep -oP "\w+Event" | sort -u > /tmp/called_events.txt
comm -23 /tmp/defined_events.txt /tmp/called_events.txt
```
- 10分: 所有定义的事件都有发布方和监听方，零死事件
- 7分: 核心事件全闭合，1-3 个边缘死事件（扩展预留可接受）
- 4分: 4-8 个死事件，有核心链路缺失
- 0分: 大面积死事件，事件系统形同虚设

---

## 审计执行策略

### 全量审计（首次）

并行启动 8 个 Agent：

| Agent | 负责维度 | 推荐模型 |
|-------|----------|----------|
| Agent 1 | 1.编译 + 3.TODO + 7.交付文档 | Haiku |
| Agent 2 | 4.事件契约 + 16.事件链路缺失 | Sonnet |
| Agent 3 | 5.反硬编码 + 14.语言硬编码 | Haiku |
| Agent 4 | 6.资源泄漏 + 9.L2功能完整性 | Sonnet |
| Agent 5 | 2.测试覆盖 + 8.Config/Lang | Haiku |
| Agent 6 | 10.管理员命令 + 11.玩家命令入口 | Haiku |
| Agent 7 | 12.Matcher使用 + 13.GUI Matcher渲染 | Sonnet |
| Agent 8 | 15.界面美化程度 | Haiku |

### Delta 复查（修复后）

只检查修复涉及的维度，不全量重审。单 Agent 即可。

```
修复了资源泄漏 → 只重查维度 6
修复了 TestRunner 签名 → 只重查维度 2
修复了多个维度 → 单 Agent 逐项验证
```

## 修复清单优先级定义

| 优先级 | 定义 | 示例 |
|--------|------|------|
| P0 | 影响运行时稳定性，必须修复 | 内存泄漏、死代码管道、编译失败 |
| P1 | 影响规范合规性，应该修复 | TestRunner 签名不统一、硬编码文本 |
| P2 | 影响完整度，可以修复 | 文档不准确、边缘模块缺测试 |

## 修复分发模板

```
for each issue in P0_issues (串行，修一个验一个):
    launch Task(fix prompt, model=Sonnet/Opus)
    verify fix

for each issue in P1_issues (可并行):
    launch Task(fix prompt, model=Sonnet)

for each issue in P2_issues (可并行):
    launch Task(fix prompt, model=Haiku)

run delta recheck
```

## 各档达标标准

### 40 分线（R1 粗胚）
- [ ] 编译通过 (维度1 = 10)
- [ ] L1 模块有完整实现
- [ ] L2 模块有骨架 + TODO[R2] 标注
- [ ] 数据模型全部就位

### 70 分线（R2 功能）
- [ ] 编译通过 (维度1 = 10)
- [ ] 无 TODO[R2] 残留 (维度3 >= 7)
- [ ] 核心事件链闭合 (维度4 >= 7)
- [ ] L2 模块入口活跃 (维度9 >= 7)
- [ ] Config/Lang 基本完整 (维度8 >= 7)
- [ ] 玩家有命令入口 (维度11 >= 4)
- [ ] 零死事件或已标注扩展预留 (维度16 >= 7)

### 90 分线（R3 打磨）
- [ ] 16 个维度均 >= 7
- [ ] 折算百分制 >= 80
- [ ] P0 问题数 = 0
- [ ] 交付文档存在且准确
- [ ] 条件逻辑使用 Matcher (维度12 >= 7)
- [ ] GUI 条件渲染使用 matcher-render (维度13 >= 7)
- [ ] 语言零硬编码 (维度14 >= 7)
- [ ] GUI 有基本美化 (维度15 >= 7)

## 关键经验（从 BetterEnchant 复盘提炼）

1. **L2 模块最容易出问题** — 同样的 worker template，L1 做得好但 L2 做得差，因为 L2 需要 Bukkit 平台事件入口、装备扫描范围、跨模块事件编排等信息，这些信息如果 pipeline 没生成，Agent 就会猜错或跳过
2. **死代码是最严重的问题** — 没有平台事件监听的模块 = 整个模块是死代码，比 bug 更严重
3. **清理方法存在 ≠ 被调用** — 必须 grep 确认清理方法有调用方，否则是死代码
4. **Agent 会虚报完成** — _done 文档声称完成的链阶段可能功能上是断裂的，必须用 grep 命令验证而非信任文档
5. **预检模式优于后处理** — Vault 经济扣费用 PreCheck 事件比 Success 事件后扣更安全
6. **纯静态审计可以达到 90 分** — 不需要进入游戏，grep + 编译 + 代码阅读就能发现绝大多数问题
7. **最佳实践不用 = 扣分** — EasyLib-Matcher 是条件判断的标准方案，gui-matcher-render 是 GUI 条件渲染的标准方案，知识库里有但不用就是质量缺陷
8. **语言硬编码比数值硬编码更严重** — 数值硬编码影响可调性，语言硬编码直接导致无法国际化且违反框架规范
9. **GUI 美化是用户体验的底线** — 没有音效反馈、没有边框装饰、没有分页的 GUI 在玩家眼里就是半成品
10. **管理员命令是运维生命线** — 没有 reload/give/reset 命令的插件无法运维，权限检查缺失是安全漏洞
