# R2 证据：乔迪纵向黄金样本（builder + App + 自动测试 + 设备截图）

> 按 [RECOVERY.md](RECOVERY.md) R2 先打通一个真实村民（乔迪），修 builder 事实语义、
> App 分类展示投影、自动门禁与真实设备截图。生成时间：2026-08-15（会话运行日）。
> 真实资产：`D:\SteamLibrary\steamapps\common\Stardew Valley\Content (unpacked)`（游戏 1.6.15.24356）。

## 1. builder 侧修复（根因修复，非字符串遮盖）

| 根因 | 修复 | 文件 |
|---|---|---|
| 礼物解析读错字段：NPCGiftTastes 是「台词(偶)/物品(奇)」交错格式，旧代码读 0–4 | 按游戏 `NPC.getGiftTasteForThisItem` 的读取方式改读奇数位 1/3/5/7/9 | `parsers/official.py` |
| 礼物 token 未解析（Oh/you're 台词碎片、object:N、官方分类 -5/-79、category_trinket、Book_Void 等命名 ID） | 台词碎片不再进入解析；`object:N`/命名 ID 解析为类型化实体引用（schema 5 跨仓契约）；官方类别码 → 游戏 zh-CN 类别名（对照 DLL `GetCategoryDisplayName` + `StringsFromCSFiles.zh-CN.json` 逐项核对）；上下文标签 → 中文短语；剩余无法解析 token 丢弃并记入 `gift-reference-diagnostics.json`（进 reports，不进玩家事实） | `schema5_projection.py`、`models_schema5.py`、`schema5_candidate.py` |
| 生日/性别/常住地透传官方枚举（Fall 11 / Female / Town） | 中文规范值：`秋季 11 日`、`女性`/`男性`、`鹈鹕镇`/`沙漠`/`鹈鹕镇周边`；官方 `Undefined` 性别（矮人）输出 `not_applicable` 槽 | `schema5_projection.py` |
| 村民卡片无行动摘要 | `project_card_actions` 增加生日+常住派生：`生日：秋季 11 日`、`常住：鹈鹕镇` | `schema5_projection.py` |
| 肖像半脸裁切（`imageRect=[0,0,32,64]` 把 64×64 官方肖像切一半） | 改为 `[0,0,64,64]` 完整肖像；走路贴图回退路径不变 | `parsers/legacy_visuals.py` |
| 日程透传内部代号（SamHouse 6 5 0、Strings 令牌、GOTO Wed） | 全部本地化：`8:00 山姆家`、`与周三相同`、`留在皮埃尔杂货店`、`受邮件事件条件限制`、`需与山姆好感度低于6`；坐标/动画/引号令牌丢弃；地名对照全量真实词汇（38 个地点 + 规则） | `schema5_projection.py` |
| manifest `displayName` 直接输出英文内部 id | 复用 `ENTITY_TYPE_LABELS` 中文名（含 `npc_schedule`→`日程记录`） | `database/schema5.py`、`config.py` |

## 2. App 侧修复（展示投影，四层详情）

| 根因 | 修复 | 文件 |
|---|---|---|
| 所有事实堆进「核心信息」，无层级 | 村民专属四层：`立即行动`（常住地→日程规则→生日→最爱礼物→婚配资格，5 个答案按人物契约排序）、`更多资料`（性别）、`别名`、`数据说明`；礼物/日程进可展开 submenu | `Schema5WikiCatalogue.kt` |
| 每条事实拼接「来源：官方原始数据；Data/…；版本…；证据 direct；转换 …」 | 来源说明不再进事实行；`数据说明` 只保留玩家文案「依据游戏数据整理/计算」（去重）；路径/版本/证据/转换留在数据库与诊断层 | `Schema5WikiCatalogue.kt` |
| 未解析礼物/原始 ID 直出 UI | 礼物 submenu 标签用目标实体中文名（可点击 `RelationTarget.Entry`）；类别短语（动物制品/水果/采集品）为只读 chip | `Schema5WikiCatalogue.kt` |
| 未知槽名经 `replace('_',' ')` 进 UI | `factLabel` 未知槽返回 null 并跳过，不再兜底显示 | `Schema5WikiCatalogue.kt` |
| 引用类事实值（制作材料 object:395 等）直出 | 条目级目标解析：所有引用型 fact 值与 item 值经 `targets` 解析为中文名 | `Schema5WikiCatalogue.kt` |
| 关系分组显示英文 family（friendship/kinship）与「方向：villager:Kent → villager:Jodi」 | family→中文（亲属关系/朋友关系/角色资料）；方向用双方中文名（肯特 → 乔迪） | `Schema5WikiCatalogue.kt` |
| 村民卡片英文名占空间 | 村民列表卡 `englishTitle=null`（英文名仍可用于搜索索引） | `Schema5WikiCatalogue.kt` |
| 小屏/大字体下网格不降列 | `GridCells.Adaptive` 最小宽随 fontScale 提升（1.3→160dp、2.0→200dp），320dp/2.0 字体降为单列 | `TypeListFeature.kt` |

## 3. 自动测试（红→绿）

- builder 门禁 `tests/test_player_ui_gates.py`：**10/10 绿**（6 个投影级 + 4 个真实候选级）。
  - 真实候选扫描（`PLAYER_UI_REAL_CANDIDATE_DB=.tmp\goal-full-candidate-r2\stardew.db`）：全部玩家事实 0 未解析、0 官方分类引用、0 内部地点、0 未本地化枚举；34 村民卡片全有摘要；34 肖像全部 ≥64px 完整；25 个可浏览类型全中文名。
  - 回归：全量 builder pytest **229 passed**、ruff 通过。
- App 门禁 `RealV5PlayerGateTest`：真实包注入（模拟器 `emulator-5554`）**4/4 绿**。
  - 23 个分类标题全中文；乔迪条目零泄露（类别标签、生日 `秋季 11 日`、立即行动顺序、礼物/日程不混入普通事实、数据说明仅玩家文案、关系分组与方向全中文）；34 村民卡片全有摘要；玩家事实无原始实体引用。
  - App `testDebugUnitTest` + `lintDebug` 通过。
- 两仓 `git diff --check` 干净。

## 4. 真实候选重建（真实资产）

- 命令：`python -m builder build-schema5 --game-dir "D:\SteamLibrary\steamapps\common\Stardew Valley" --output .tmp\goal-full-candidate-r2 --unpacked-dir "…\Content (unpacked)"`（53.5s）
- 产出：`stardew.db`（含 release coverage 全绿）、`stardew-zh-cn.svdata`（6,915,530 字节）、reports（含 `gift-reference-diagnostics.json`）
- 乔迪新数据（真实库抽验）：卡片 `生日：秋季 11 日`+`常住：鹈鹕镇`；birthday=`秋季 11 日`、gender=`女性`、residence=`鹈鹕镇`；最爱 8 项 = object:72,200,211,214,220,222,225,231（钻石、粉红蛋糕…）；喜欢含 动物制品×2、水果；0 未解析；日程 13 条全中文（`8:00 山姆家；…`、`与周三相同`、`留在皮埃尔杂货店`）；肖像 64×64 全脸（Qwen-VL 复核）。

## 5. 设备截图验收（`E:\github\valley-dex\.tmp\recovery-r2\`）

| # | 文件 | 检查 | 结论 |
|---|---|---|---|
| 02 | `02-home.png` | 首页全中文，23 类 · 3165 条 | ✓ |
| 03 | `03-villagers.png` | 村民列表：完整肖像、仅中文名、两条摘要（生日/常住） | ✓（Qwen-VL 复核无半脸/无技术词） |
| 04 | `04-jodi-detail.png` | 乔迪详情：身份头部 + 立即行动 5 答案（常住地/日程/生日/最爱礼物/婚配资格）+ 更多资料（性别）+ 数据说明（仅玩家文案） | ✓（Qwen-VL 复核） |
| 05/06 | `05-jodi-scrolled.png`/`06-jodi-relations.png` | 礼物偏好（21 条）与日程（13 条）可展开；关系区全中文：朋友关系（亲友关联（具体关系未注明）→山姆/文森特）、亲属关系（丈夫→肯特）、角色资料（角色资料关联（不是当前恋爱状态）→肯特）、反向关系·亲属关系（妻子/母亲 + 方向 肯特 → 乔迪） | ✓ 文森特/贾斯规则（decision 05）在本样本的亲友关联上已生效 |
| 07 | `07-gifts-expanded.png` | 礼物 chip：最爱 8 项全中文可点击；喜欢 6 项含类别短语 | ✓ |
| 09/14 | `09-360x800-jodi.png` / `14-360x800-jodi-v2.png` | **360×800dp**：首屏可见身份 + 全部 5 个核心答案 | ✓ |
| 13 | `13-320x568-font2-jodi-v2.png` | **320×568dp + fontScale 2.0**：无需滚动可见第一个核心答案（常住地 鹈鹕镇） | ✓ |
| 12 | `12-320x568-font2-villagers-v2.png` | 320dp/2.0 字体下列表降为单列，卡片完整 | ✓ |
| — | TalkBack 语义 dump | 卡片 contentDescription=「打开 乔迪」、名称与两条摘要作为子文本朗读、返回按钮中文 | ✓ |

## 6. R2 完成标准核对

- [x] 列表卡：完整可辨认肖像、仅中文名、生日+常住两条中文摘要
- [x] `Fall 11`→`秋季 11 日`；`Female`→`女性`（矮人 not_applicable）
- [x] 详情立即行动区 5 个答案按契约排序
- [x] 最爱礼物 = 可点击中文实体；无 raw token/ID/未解析文本
- [x] 完整礼物与日程进入可展开资料
- [x] 数据说明仅玩家文案；provenance 保留在数据库/诊断入口
- [x] 360×800 首屏：身份 + ≥3 核心答案（实测 5 个）
- [x] 320×568 + fontScale 2.0：无需滚动可见第一个核心答案
- [x] TalkBack 朗读中文名称、状态与行动摘要
- [x] builder、fixture（投影级门禁）、WikiCatalogue、Compose、自动测试、真实设备截图同一轮完成

## 7. 残余风险

- 设备验证使用本机 Android 模拟器（无实体手机）；实体机路径待有设备时按本文命令复核。
- 详细来源/转换链的诊断入口（独立界面）尚未实现：当前证据保留在 reports 与数据库，普通页面已零泄露；诊断 UI 属后续切片。
- 其他类别（作物/商店等）的 fact 值已无来源拼接与原始引用，但四层详情专属投影目前只覆盖村民；其余类别按 R4 波次逐类替换。
- 320dp/2.0 字体下首页快捷入口需滚动（首页布局非 R2 范围，R3+ 处理）。
