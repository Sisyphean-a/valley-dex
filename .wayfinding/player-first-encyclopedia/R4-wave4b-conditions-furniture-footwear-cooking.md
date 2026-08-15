# R4 第 4 波第 2 片：全量条件中文化 + 家具/鞋类/料理契约（证据记录）

候选目录：`E:\github\stardew-offline-data-builder\.tmp\goal-full-candidate-r6\`
（本片无事实项移除；条件摘要与卡片值变化在原目录重建通过回归预算。）

## 契约

- 条件摘要（全部）：GameStateQuery 谓词与参数全部中文；除品牌词「Joja」外零拉丁字母。
- 家具：购买价格（固定价或目录/动态规则）→ 兑换 → 用途。
- 鞋类：防御/免疫 → 购买/兑换途径。
- 料理：材料（含「任意鱼类/蛋类/奶类」类别材料）→ 产物。

## Builder 改动

- `schema5_projection.py`：
  - 新表：`MAIL_FLAG_ZH`（22 个邮件标志）、`WORLD_STATE_FIELD_ZH`、`PLAYER_STAT_ZH`、
    `WEATHER_VALUE_ZH`、`PASSIVE_FESTIVAL_ZH`、`SPECIAL_ORDER_RULE_ZH`、`MUSEUM_DONATION_TYPE_ZH`、
    `RELATIONSHIP_STATUS_ZH`、`SYNCED_DAY_KEY_ZH`（旅行货车/书籍/星币等 15 键）、
    `CONVERSATION_TOPIC_ZH`、`PRICE_MODIFIER_SCOPE_ZH/MODE_ZH`、`RECIPE_CATEGORY_INGREDIENT_ZH`（-4/-5/-6/-777）。
  - `game_state_query_display` 全覆盖：DAY_OF_MONTH（偶数日/奇数日）、TIME（6:00–18:00）、
    WEATHER（雨/雷雨/雪/绿雨…）、MUSEUM_DONATIONS（文物 20 件）、IS_PASSIVE_FESTIVAL_OPEN、
    PLAYER_SPECIAL_ORDER_RULE_ACTIVE、PLAYER_BASE_*_LEVEL（N 级）、PLAYER_FARMHOUSE_UPGRADE、
    PLAYER_HAS_ACHIEVEMENT（按 achievement 实体名解析）、PLAYER_HAS_ALL_ACHIEVEMENTS、
    PLAYER_HAS_TOWN_KEY、PLAYER_HAS_MAIL、PLAYER_HAS_CONVERSATION_TOPIC、PLAYER_HAS_SEEN_EVENT、
    PLAYER_HEARTS（阿比盖尔 14 心）、PLAYER_NPC_RELATIONSHIP（任意村民（订婚、已婚））、
    PLAYER_HAS_ITEM/CRAFTING_RECIPE（按实体名解析，支持带空格配方名 Explosive Ammo）、
    PLAYER_STAT（精通/击杀数/书籍按实体名）、WORLD_STATE_FIELD（true/false→是/否）、
    SYNCED_RANDOM/SYNCED_CHOICE（当天同步随机/选择）。
  - 标签修正：`PLAYER_NPC_RELATIONSHIP` 标签「玩家与 NPC 关系」→「玩家与村民关系」；
    新增 PLAYER_HAS_ACHIEVEMENT/ALL_ACHIEVEMENTS/TOWN_KEY、IS_JOJA_MART_COMPLETE 的标签与参数形状。
  - 价格修正摘要：`priceModifiers价格修正：Set` →「商品价格修正：固定为 X」等。
  - 鱼类条件文案：`离岸最大距离：-1`→「不限」、`最低钓鱼等级：0`→「无等级要求」。
  - 鞋类 typed_facts：`defense`/`immunity`（parser 字段位修正：Boots 旧格式
    名称/描述/价格/防御/免疫/贴图/显示名——原实现把价格读成了防御，已按游戏数据与
    Boots.json 实际记录修正，运动鞋防御 1/免疫 0）。
  - 料理材料：负类别材料（-4 鱼类等）以中文文案（任意鱼类）进入事实项。
  - 卡片行动摘要：furniture `("purchase_price","purchase_exchange_item_id")`（动态规则显示
    「购买：基础价由游戏运行时数据决定」）、footwear `("defense","purchase_price")`、
    cooking/crafting recipe `("crafting_material_id",)`；材料/用途/加工分支支持中文类别材料。

## 门禁与测试

- builder：`pytest` 254 通过；设置 `PLAYER_UI_REAL_CANDIDATE_DB=.tmp\goal-full-candidate-r6\stardew.db`
  后 **37 个门禁全过**。新增关键门禁：`test_real_candidate_all_condition_summaries_are_chinese`
  —— 全部条件摘要除品牌词 Joja 外零拉丁字母（修复前有 110 条泄露）。
- 新增投影门禁：商店条件本地化（好感/邮件/天气）、鱼类条件本地化（天气/特别订单规则/时间）、
  家具/鞋类/料理卡片契约、类别材料、种子生产器行收窄。
- App：`testDebugUnitTest`、`lintDebug`、`assembleDebug(AndroidTest)` 通过；
  `RealV5PlayerGateTest` **19 个测试全过**（新增运动鞋/水晶椅/烤鱼契约测试）。

## 设备截图验收（emulator-5554，`.tmp\recovery-r4-wave3\`）

- `w4b-sneakers.png`：运动鞋——防御 1 / 免疫 0 / 购买价格 500 金币。
- `w4b-baked-fish.png`：烤鱼——材料 鲷鱼×1、太阳鱼×1、大麦粉×1；产物 烤鱼。
- `w4b-legend-ii.png`：传说之鱼二代——捕捞地点详情条件「出现概率：0.1；
  特别订单规则状态：传说之鱼家族任务；离岸最大距离：不限；离岸最小距离：4；
  最低钓鱼等级：10」全中文（修复前为 Current LEGENDARY_FAMILY / Here Rain Storm GreenRain）。
- `w4b-crystal-chair.png`：水晶椅——购买价格 2500 金币；用途 海鱼、海之菜肴。

## 残余风险（转入后续波次）

- 家具购买价数据个别为 0（如橡木椅，商店数据本身如此），未在 UI 层掩盖。
- 任务/收集包/成就/饰品等类型尚无专属契约（数据支撑有限，按 RECOVERY 逐类定义）。
- 机器输出产物表（熔炉→铜锭等产出与概率）未投影；商店随机上架语义待 R5 前审计。
- 种子生产器输出概率（混合种子 2%/上古种子 0.5%）未投影。
