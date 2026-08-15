# R4 波次 1a 证据：作物纵向切片（builder + App + 门禁 + 设备截图）

> 按 [RECOVERY.md](RECOVERY.md) R4 第一波“商店与作物”。本切片完成**作物**；
> 商店部分见“残余”节（数据缺口已定位，下一轮继续）。
> 真实资产：`D:\SteamLibrary\steamapps\common\Stardew Valley\Content (unpacked)`；候选 `.tmp\goal-full-candidate-r2`。

## 1. builder 修复

| 问题 | 修复 |
|---|---|
| 作物/鱼类 `seasons` 透传英文（`Spring`、`spring,summer`） | `localized_seasons`：官方季节名 → 中文（春季/夏季/秋季/冬季），多季以空格连接 |
| 商店动态报价规则名进入玩家事实（`runtime-profit-margin`、`out-of-season-price-rule`…） | `PRICE_RULE_REASON_ZH` 玩家文案（受利润率设置影响 / 反季节时按游戏规则加价 / 受条件或随机价格修正影响…）；构建诊断（`shop-price-diagnostics.json`）保留原始 reason |
| 作物卡片只有季节、无成熟摘要；顺序不稳定 | `project_card_actions` 按分类契约优先级排序：作物 = 季节 → 成熟（`成熟：N 天`）；村民 = 生日 → 常住；鱼 = 地点 → 季节 |

## 2. App 修复

| 问题 | 修复 |
|---|---|
| 作物沿用通用「核心信息」无层级 | 新增 `cropSections`：立即行动按作物契约排序（季节 → 成熟（含再生规则）→ 种子（名称+价格或“见商店报价”）→ 收获物 → 出售价格；「不需要每天浇水」仅在例外时显示） |
| 用途散落在重复行 | `used_in`/`used_in_quantity` 成对投影为可展开「用途」submenu（中文名 ×数量，可点击） |
| 动态种子价无玩家文案 | 动态规则显示「价格见商店报价（受游戏规则影响）」，不显示内部 rule 名 |

## 3. 自动门禁（红→绿）

- builder：新增 `test_crop_seasons_are_localized_and_card_actions_follow_contract`（投影级）与真实候选全量门禁的 seasons 检查；**真实候选 11/11 绿**（含全部村民 + 全槽扫描）。
- App：新增 `parsnipEntryFollowsCropContract`（真实包注入）：类别标签中文、立即行动顺序 = [季节, 成熟, 种子, 收获物, 出售价格]、季节=春季、出售价格=35 金币、种子以「防风草种子」开头、零泄露、存在可展开「用途」。**6/6 绿**。
- 回归：builder 全量 229 passed、ruff 通过；App testDebugUnitTest + lintDebug 通过。

## 4. 设备截图（`E:\github\valley-dex\.tmp\recovery-r3\`）

| # | 文件 | 检查 |
|---|---|---|
| 06 | `06-crops-list.png` | 作物列表：中文名 + `季节：春季 夏季 秋季`、`成熟：28 天`（上古水果）；季节 facet chips 全中文；无英文枚举（Qwen-VL 复核） |
| 07 | `07-parsnip-detail.png` | 防风草详情：身份（中文描述）+ 立即行动 5 答案按契约顺序 + 数据说明仅玩家文案 + 用途可展开（Qwen-VL 复核无泄露） |
| 08 | `check-detail.png`/`ui-parsnip-uses4.xml` | 用途展开：春季作物 ×1、高品质作物 ×5、农夫午餐 ×1、防风草汤 ×1（全中文可点击） |

## 5. 完成标准核对（作物部分）

- [x] 真实代表实体：防风草（crop:24，黄金样本）
- [x] 中文卡片行动摘要：季节 + 成熟（契约顺序）
- [x] 分类专属立即行动顺序：季节与成熟 → 种子来源和成本 → 收获/再生/关键要求 → 出售价格
- [x] 完整资料折叠结构：用途 submenu
- [x] 状态样本：regrow not_applicable（一次收获）、动态种子价规则（受游戏规则影响）、季节条件
- [x] 列表、详情、搜索任务：分类浏览 + 分类内英文名搜索 + 详情全路径截图
- [x] 自动黄金输出：投影级 + 真实候选级门禁 + App 真实包门禁

## 6. 残余（商店部分 + 后续波次）

- **商店地点/营业时间数据缺口**：1.6 `Data/Shops.json` 无 Location/Hours（游戏运行时由店主日程推导）。按决策 04 数据缺口规则，下一步从店主本地化日程推导“地点+营业时间”并输出为带证据的派生事实；未完成前页面显示「暂未收录」，不以店主/商品数顶替。
- 鱼类的 `fishing_time`/`weather` 槽仍是英文（波次 3 处理，已列入门禁扩展计划）。
- 作物官方无图（如冬根）显示独立“无图”占位状态（decision 08 合规，非错误图）。
