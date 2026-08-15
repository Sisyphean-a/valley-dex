# R4 第 3 波：怪物、鱼类、武器（证据记录）

日期：2026-08（会话内真实构建 + 设备截图）
候选目录：`E:\github\stardew-offline-data-builder\.tmp\goal-full-candidate-r4\`
（前代 r3 保留在 `.tmp\goal-full-candidate-r3\`；本波因矿井钓鱼 bait 提示改变稳定键而改在全新目录构建。）

## 契约（按 RECOVERY.md R4-3）

- 怪物：出现区域 → 生命/伤害 → 掉落（概率条件）；
- 鱼类：捕捞地点（逐地点条件）→ 季节/时间/天气 → 难度 → 行为/尺寸/售价；
- 武器：类型（DLL 常量）→ 伤害区间 → 获得方式 → 售价。

## Builder 投影改动（`src/builder/pipeline/schema5_projection.py`）

- 新表：`FISHING_LOCATION_ZH`（24 个真实地图 id）、`FISH_BEHAVIOR_ZH`、`FISH_WEATHER_ZH`、
  `WEAPON_TYPE_ZH`（0/3=剑、1=匕首、2=棍棒、4=弹弓，依据 MeleeWeapon 常量：
  stabbingSword=0、dagger=1、club=2、defenseSword=3，运行时 0→3）、`WEAPON_SCYTHE_IDS`（47/53/66→镰刀）。
- `localized_fishing_time`：`1200 1600`→`12:00–16:00`；跨夜 `2600`→`次日 2:00`；分时段四段拼接。
- 鱼类 typed_facts：`behavior`/`weather` 查表本地化（未知值不放行），`fishing_time` 本地化。
- `fishing_locations` 事实项文本按 `FISHING_LOCATION_ZH` 本地化；未知地图 id 输出空（不泄露原文）。
- 矿井钓鱼 bait 提示：参考键改为实体 id（`fish:158`，稳定、不随翻译变化），
  玩家摘要按包内实体名解析为「针对性鱼饵：石鱼」。
- 怪物 typed_facts：`health`/`damage` 来自 Monsters.json `fields[0]/fields[1]`（DLL/官方数据双确认）。
- 武器 typed_facts：`weapon_type` 事实（Type 查表 + 镰刀特判）。
- 卡片行动摘要：
  - monster：`("locations","drops")` →「地点：矿井」「掉落：史莱姆泥、绿藻、紫水晶」（掉落解析为中文实体名）；
  - weapon：`("weapon_type","damage_min")` →「类型：剑」「伤害：2–5」；
  - 鱼类维持 `("fishing_locations","seasons")`。
- `add_inline_drop_projections` 的怪物 `Locations` 列表同样按地图表本地化（生产路径当前为空，防御性）。
- 顺带修复：`fish_condition` 的 bait 提示解析改为按 by_id；`localization_keys` 紧凑名去除
  空格与标点（`Ms. Angler`→`MsAngler_Name`，官方字符串键规范）。

## 显示名英文泄露修复（本波发现）

- `fish:899` Ms. Angler → 雌鮟鱇鱼、`fish:902` Glacierfish Jr. → 小冰川鱼（官方 zh-CN 字符串键修正后命中）。
- `monster:Iridium-Golem` → 铱石魔、`monster:Truffle-Crab` → 松露蟹：官方 zh-CN Monsters.json
  对这两个变体仍保留英文显示名（游戏自身如此），在 `normalize_titles.py` 增加
  `MONSTER_NAME_FALLBACK_ZH`（仅当官方中文名不含中文时生效，未来官方翻译出现即自动让位）。
- 门禁：`test_real_candidate_wave3_entity_names_are_chinese`——鱼类/怪物/武器实体名必须含中文。

## 门禁与测试

- builder：`pytest` 245 通过 + 7 跳过（真实候选门禁按环境变量启用）；
  设置 `PLAYER_UI_REAL_CANDIDATE_DB=.tmp\goal-full-candidate-r4\stardew.db` 后 **24 个门禁全过**，
  新增：怪物健康/伤害存在性、武器类型存在性、怪物/武器卡片行动摘要、波次实体名中文、
  鱼类行为/天气/时间/地点槽泄露规则（`floater|sunny|1200 1600|Beach` 等一律禁止）。
- 投影门禁新增：鱼类槽本地化、跨夜时间、鱼类卡片契约、怪物健康/伤害、怪物卡片
  （运行时地点规则绑定）、武器类型与卡片（含弹弓 Type 4、镰刀特判）。
- App：`testDebugUnitTest` 通过；`lintDebug` 通过（见当轮日志）；
  `assembleDebug`/`assembleDebugAndroidTest` 通过。
- 仪器化 `RealV5PlayerGateTest`：**13 个测试全过**（新增河豚、绿色史莱姆、生锈的剑三个契约测试 +
  传奇鱼/怪物中文名测试），运行参数 `-e realV5Required true -e realV5PackagePath /data/local/tmp/stardew-v5-r4.svdata`。

## 设备截图验收（模拟器 emulator-5554，1080x2340 / 440dpi）

截图目录：`E:\github\valley-dex\.tmp\recovery-r4-wave3\`

- `s3-pufferfish.png`：河豚详情。立即行动 = 捕捞地点（海滩、姜岛东南洞穴、姜岛东南、姜岛南部、姜岛西部）
  → 季节 夏季 → 捕捞时间 12:00–16:00 → 天气 晴天 → 难度 80/110；更多资料 = 行为 漂浮型 / 尺寸 1–36 厘米 /
  出售价格 200 金币；捕捞地点详情带逐地点条件。零英文泄露。
- `s4-green-slime.png`：绿色史莱姆详情。立即行动 = 出现地点 矿井 → 生命值 24 → 伤害 5 →
  掉落（史莱姆泥、绿藻、紫水晶、树液、矮人卷轴 I/IV）；掉落详情带概率条件（0.75/0.05/0.1/0.015/0.15）。零英文泄露。
- `s7-rusty-sword.png`：生锈的剑详情。立即行动 = 武器类型 剑 → 伤害 2–5 → 获得方式 商店购买；
  更多资料 = 出售价格 100 金币；数据说明 = 依据游戏数据计算/整理。零英文泄露。
- 列表页复核：鱼类卡片「地点：海滩」「季节：夏季」、怪物卡片「地点：矿井」「掉落：…」、
  武器卡片「类型：剑」「伤害：2–5」；`s2-categories.png`/`s5-rusty-search.png` 见搜索与分类流。

## 数据抽查（r4 真实库）

- fish:128：漂浮型 / 12:00–16:00 / 晴天 / 夏季 / 5 个地点（海滩、姜岛东南洞穴、姜岛东南、姜岛南部、姜岛西部）。
- monster:Green-Slime：health=24、damage=5；卡片「地点：矿井」「掉落：史莱姆泥、绿藻、紫水晶」。
- 武器类型分布：剑 29、匕首 16、棍棒 16、弹弓 3、镰刀 3（合计 67）。
- 跨夜时间样例：fish:129 `6:00–次日 2:00`、fish:132 `18:00–次日 2:00`。
- 波次内（fish/monster/weapon）纯拉丁实体名 = 0。

## 残余风险（转入后续波次/记档）

- 机器规则条件摘要仍含 `requiredCount`/`输入标签：!seedmaker_banned`（R4-4 机器规则整表时处理）。
- 鱼类地点条件摘要里的 `离岸最大距离：-1` 语义应显示为「不限」（文案优化，非泄露）。
- 蟹笼鱼（如小龙虾）卡片目前只有「售价：75」一组摘要；「蟹笼捕获」语义未投影（可选增强）。
- 商店随机上架、工具升级时间常量复核等按原计划延后。
