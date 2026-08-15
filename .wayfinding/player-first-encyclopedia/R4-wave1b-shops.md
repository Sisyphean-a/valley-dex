# R4 波次 1b 证据：商店纵向切片（builder + App + 门禁 + 设备截图）

> 按 [RECOVERY.md](RECOVERY.md) R4 第一波“商店与作物”。本切片完成**商店**（作物见 [R4-wave1a-crops.md](R4-wave1a-crops.md)）。
> 真实资产：`D:\SteamLibrary\steamapps\common\Stardew Valley\Content (unpacked)`；候选 `.tmp\goal-full-candidate-r2`（含本切片重建）。

## 1. 数据缺口结论（决策 02 的 B/E 类）

- 1.6 `Data/Shops.json` 无 Location/Hours；游戏运行时以店主站位开放商店，静态包无法给出固定营业时段（C 类）。
- 处理方式（符合决策 04 数据缺口规则）：
  - **地点**：人工复核映射（官方店主日程为证据），全部 65 个商店覆盖：日常店 → 建筑中文名；`Festival_*` → 鹈鹕镇（节日期间）；`DesertFestival_*` → 沙漠（沙漠节期间）；`NightMarket_*` → 海滩（夜市期间）；家具目录类 → 任意地点（家具目录）。
  - **营业时间**：发布规则而非假装固定时段 —— 日常店 `随店主日程变化`（dynamic_rule + 条件「受星期、天气与节日影响」）；节日店 `仅节日当天开放`（conditional）；目录类 not_applicable。
  - **店主**：从 `Owners[0]` 解析为村民中文名。
  - **商品报价**：复用 `resolve_shop_offer_price`/`shop_condition`，以 scope 配对的 fact items 承载（`shop_offer_item/price/currency/currency_amount/exchange_item_id/exchange_amount/price_rule`），条件紧邻报价。

## 2. 修复的额外泄露（真实包扫描发现）

| 泄露 | 修复 |
|---|---|
| 商品条件摘要透出原始 GameStateQuery：`（季节：SEASON spring）`、`年份：YEAR 2` | `game_state_query_display`：SEASON/LOCATION_SEASON → 春季/夏季…、DAY_OF_WEEK → 周一…、其他谓词保留数字参数；门禁新增「原始游戏状态查询」模式（扫描 fact 值 + 条件摘要） |
| 鱼类地点条件 `季节：Spring` | `fish_condition` 季节参数本地化 |
| 商店卡片无摘要、详情无商品 | 卡片行动摘要（地点/营业）+ 商品报价投影 |

## 3. 自动门禁（红→绿）

- builder：新增 `test_shop_projection_emits_localized_profile_and_quotes`（投影级，SeedShop 黄金 fixture：地点/营业/店主/报价/卡片摘要）；真实候选门禁扩到条件摘要扫描。**真实候选 12/12 绿**（含全量事实 + 条件摘要零原始谓词）。
- App：新增 `pierreShopFollowsShopContract`（真实包注入）：类别标签中文、立即行动顺序 [地点, 营业时间, 店主]、地点=皮埃尔杂货店、营业规则、商品报价 ≥3、零泄露。**7/7 绿**。
- 回归：builder 全量 229 passed、ruff 通过；App testDebugUnitTest + lintDebug 通过（上一轮已跑，本轮改动后再跑一次确认）。

## 4. 设备截图（`E:\github\valley-dex\.tmp\recovery-r3\`）

| # | 文件 | 检查 |
|---|---|---|
| 11 | `11-shops-list-v2.png` | 商店列表卡片：地点+营业摘要（Joja超市/万灵节/书摊/沙漠节…）；无图商店显示独立占位状态（Qwen-VL 复核） |
| 12/13 | `12-pierre-detail.png` / `13-pierre-detail-final.png` | 皮埃尔商店详情：立即行动（地点/营业时间（受星期、天气与节日影响）/店主/3 件商品报价）+ 数据说明 + 商品 submenu（Qwen-VL 复核零泄露） |
| 14/15 | `14-pierre-offers.png` / `15-pierre-offer-rows.png` | 商品报价 55 项展开：`防风草种子 受利润率设置影响 （季节：春季）`、`大蒜种子 …（季节：春季；年份：2）` —— 条件全中文、无原始 GameStateQuery |

## 5. 完成标准核对（商店部分）

- [x] 真实代表实体：皮埃尔杂货店（shop:SeedShop，黄金样本）
- [x] 中文卡片行动摘要：地点 + 营业规则（契约顺序）
- [x] 分类专属立即行动顺序：地点与营业规则 → 商品报价（前 3 件）与条件
- [x] 完整资料折叠结构：商品报价 55 项 submenu
- [x] 状态样本：dynamic_rule（随店主日程变化）、conditional（仅节日当天开放）、not_applicable（家具目录）、条件报价（季节/年份/利润率）
- [x] 列表、详情、搜索任务：分类浏览 + 详情全路径截图
- [x] 自动黄金输出：builder 投影级 + 真实候选级 + App 真实包门禁

## 6. 残余风险

- 「受利润率设置影响」动态报价不显示具体数字（游戏运行时按利润倍率计算），规则文案为诚实表述；完整价格由 R5 条件语义审计复核。
- 商店图片均为官方无图占位（decision 08 独立状态）；若后续要商店专属视觉需单独立项。
- 纯随机商品（RandomItemId-only）报价暂不展开，已记入 `shop-price-diagnostics.json`，R4 后续波次处理。
