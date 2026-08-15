# R4 第 4 波第 1 片：机器规则、物品/矿物/戒指契约（证据记录）

候选目录：`E:\github\stardew-offline-data-builder\.tmp\goal-full-candidate-r6\`
（本片移除了戒指的机器行与非作物收获物的种子生产器行 → 事实项减少，按回归预算规则改在全新目录构建。）

## 契约（RECOVERY.md R4-4 的部分切片）

- 物品/矿物：出售价格 → 用途（料理/制作/收集包）→ 加工（机器规则）；
- 戒指：出售价格 + 购买/兑换途径（戒指不进机器）；
- 机器规则条件：所需数量/输入标签全部中文，不泄露 `requiredCount`/标签原文。

## Builder 改动

- `schema5_projection.py`：
  - `ITEM_TAG_ZH`（机器 RequiredTags 全集 + 家具目录 ITEM_CONTEXT_TAG 全集，19+5 个标签）
    与 `ITEM_EDIBILITY_ZH`；`game_state_query_display` 支持 ITEM_CONTEXT_TAG/ITEM_EDIBILITY/
    RANDOM（百分比）/LOCATION_SEASON（去掉上下文 token）。
  - `opaque_rule_condition`：`requiredCount`→「所需数量」，`requiredTags`→「输入须为：…」「排除：…」，
    未知标签→「另有未识别标签要求」且 complete=False；min/maxDepth 等标签中文化。
  - 机器条件 id 纳入 machine id（修复 BaitMaker/种子生产器共用 `Default:ItemPlacedInMachine`
    导致条件串用的碰撞：鱼饵制造器此前误显示种子生产器的条件）。
  - 种子生产器行只保留给作物收获物（DLL `Object.OutputSeedMaker` 复核：仅接受
    `cropData` 的 HarvestItemId，其余输入无产物）；戒指完全排除机器投影。
  - 卡片行动摘要：object/mineral `("sell_price","used_in","machine_uses")`，
    ring `("sell_price","purchase_price")`；`purchase_price` 支持槽-事实项两种取值形态。
- `normalize_titles.py`：`OFFICIAL_ZH_GAP_NAMES` 统一表补齐官方 zh-CN 未翻译的显示名
  （果干/干蘑菇/熏鱼/海莉丢失的手镯/社区中心鱼缸/Joja 标志画/UFO 摆件/物品展示台/
  大型可制作物（未命名，编号 155））；官方补翻译后自动让位。
- `localization.py`：字符串键紧凑名去除空格与标点（Ms. Angler→MsAngler_Name）。

## 门禁与测试

- builder：`pytest` 245 通过；设置 `PLAYER_UI_REAL_CANDIDATE_DB=.tmp\goal-full-candidate-r6\stardew.db`
  后 **31 个门禁全过**。新增：机器条件摘要中文、全可浏览实体名中文（白名单仅游戏故意的
  「???」「……」）、物品/矿物/戒指卡片售价摘要、种子生产器非作物行剔除、机器条件 id 不合并。
- App：`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通过。
- 仪器化 `RealV5PlayerGateTest`：**16 个测试全过**（新增铜矿石/石英/光辉戒指契约测试），
  运行参数 `-e realV5PackagePath /data/local/tmp/stardew-v5-r6.svdata`。

## 设备截图验收（emulator-5554，`.tmp\recovery-r4-wave3\`）

- `w4a-copper-ore.png`：铜矿石。立即行动 = 出售价格 5 金币 → 用途（樱桃炸弹、鼓块、长笛块、熔炉）
  → 加工（熔炉、重型熔炉）；更多资料 = 购买价格 150 金币 / 兑换 卡利科三花蛋 ×2；
  加工用途详情 = 熔炉；每次 5 个，耗时 30 分钟；条件：所需数量：5。零英文泄露。
- `w4a-fire-quartz.png`：火水晶。加工用途详情含 宝石复制机；每次 1 个，耗时 83 小时 20 分钟；
  条件：所需数量：1；输入须为：宝石；排除：水晶复制器禁用物品。零英文泄露。
- `w4a-glow-ring.png`：光辉戒指。立即行动 = 出售价格 200 金币；无任何机器噪声。

## 数据抽查（r6 真实库）

- 机器条件摘要样例全部中文：所需数量/输入须为：鱼类/宝石/矿物/蛋类/绿叶蔬菜/排除：种子制造器禁用物品等。
- fish:128 机器行：种子生产器（排除：种子制造器禁用物品）、鱼饵制造器（输入须为：鱼类）、
  熏鱼机（输入须为：鱼类）——条件不再串用。
- object:72 钻石：机器行只有 宝石复制机；种子生产器行总数从 806 降到 48（仅作物收获物）。
- object:24 防风草：酒桶（输入须为：蔬菜）、罐头瓶（输入须为：蔬菜）、种子生产器 ✓。

## 残余风险（转入后续波次）

- 商店/鱼类地点的 GameStateQuery 条件摘要仍含 `Current/Here/Set/WEATHER/WORLD_STATE_FIELD/RANDOM day …`
  等原文参数（购买报价、特殊订单、世界状态类条件）→ R4 第 4 波第 2 片。
- 家具/鞋类/饰品/料理/任务/收集包/成就等类型尚无专属契约 → 后续切片。
- 种子生产器输出概率（混合种子 2%、上古种子 0.5%）未投影；输出产物表也未进机器行 → 后续。
