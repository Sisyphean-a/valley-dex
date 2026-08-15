# R3 证据：村民类别完成（34 村民零泄露 + 样本截图）

> 按 [RECOVERY.md](RECOVERY.md) R3 以乔迪黄金样本为固定输出，扩展全部村民。
> 生成时间：2026-08-15（会话运行日）。候选包：`.tmp\goal-full-candidate-r2\stardew-zh-cn.svdata`（真实资产重建）。

## 1. 全量门禁（34 村民）

- builder 真实候选门禁 `test_real_candidate_has_zero_player_ui_leaks`：扫描**全部实体全部槽**——生日未本地化 0、常住地未本地化 0、性别未本地化 0、未解析礼物引用 0、官方分类引用 0、日程内部地点 0、日程 Strings 令牌 0（R1 基线为 1546 处，现已归零）。
- `test_real_candidate_villager_cards_have_action_summaries`：34/34 卡片有「生日：…」「常住：…」。
- `test_real_candidate_villager_portraits_are_full_portraits`：34/34 肖像 ≥64px 完整（R1 基线 34 个 32×64 半脸）。
- 村民槽状态：200 fixed + 1 not_applicable（矮人性别）；release coverage 门槛在构建时通过。
- App 门禁（真实包注入）：5/5 绿，其中 `everyVillagerCardHasActionSummaries` 覆盖 34 卡片。

## 2. 决策 05 关键语义（文森特与贾斯 / LoveInterest）

- builder 保留官方边（friendship_unspecified + love_interest_pointer 双向），App 按页主人物可婚配性抑制：**本人不可婚配时，love_interest_pointer 行不进入普通页面**（儿童与已婚村民场景）。
- 新增 App 门禁 `vincentAndJasShowOnlyUnspecifiedFamilyAssociation`：
  - 文森特页 → 贾斯：仅「亲友关联（具体关系未注明）」（出向+反向各一行）；
  - 贾斯页 → 文森特：同上；
  - 两页均 0 条含「恋爱/角色资料关联」的行。
- 可婚配村民（阿比盖尔→塞巴斯蒂安）仍显示「角色资料关联（不是当前恋爱状态）」+ 说明行。
- 单向亲属不补反向称谓：贾斯页显示「侄女/外甥女 + 方向 玛妮 → 贾斯」，不虚构「姑妈」。
- 空标签不编造：文森特页「乔迪 → 文森特」显示「亲友关联（具体关系未注明）」——官方 Jodi 记录对 Vincent 标签为空，符合决策 05。

## 3. 固定截图样本（`E:\github\valley-dex\.tmp\recovery-r3\`）

| # | 文件 | 样本类型 | 结论 |
|---|---|---|---|
| 01 | `01-jas-relations.png` | 文森特/贾斯规则 | 仅亲友关联（具体关系未注明）；无恋爱行；侄女/外甥女+方向（不补反向称谓） |
| 02 | `02-vincent-relations.png` | 文森特/贾斯规则 + 空标签 | 同上；乔迪/肯特空标签边显示亲友关联 |
| 03 | `03-demetrius-search.png` | 长名称（德米特里厄斯 5 字） | 搜索卡片完整显示名称+生日+常住 |
| 04 | `04-abigail-schedule.png` | 条件化样本 | 日程展开：「需与塞巴斯蒂安好感度低于6」→ 10:00 塞巴斯蒂安的房间 → 17:00 皮埃尔杂货店 |
| 05 | `05-dwarf.png` | 无数据/不适用样本 | 性别 not_applicable → 不显示性别行；常住地「鹈鹕镇周边」（Other 区域）；无日程数据时立即行动区不伪造日程行 |
| — | R2 固定样本 | 列表/乔迪/礼物/关系/视口 | 03–14 全部通过（见 R2 证据） |
| — | 肖像抽检 | 异常图复核 | Krobus、矮人、雷欧、乔迪经 Qwen-VL 复核主体完整；34 幅经尺寸门禁 |

## 4. 状态样本矩阵（决策 10）

| 状态 | 样本 | 显示 |
|---|---|---|
| fixed | 34 村民全部槽 | 正常中文答案 |
| conditional | 阿比盖尔日程（NOT friendship 条件） | 「需与塞巴斯蒂安好感度低于6」+ 对应行程 |
| not_applicable | 矮人性别 | 不进入玩家界面 |
| unknown / not_collected | 真实数据中村民槽为 0 个 | 显示逻辑已有（`未知`/`暂未收录`），真实包无需样本；由合成 fixture 覆盖 |

## 5. R3 完成标准核对

- [x] 34 个村民中技术词和未解析引用为 0（builder + App 门禁全量扫描）
- [x] 中文生日、常住地、日程规则、礼物五档、婚配资格、有向关系（真实包全量）
- [x] 文森特和贾斯只显示「亲友关联（具体关系未注明）」
- [x] LoveInterest 不表示当前恋爱（不可婚配者抑制；可婚配者带说明）
- [x] 所有画像通过尺寸门禁 + 代表/异常图复核
- [x] 长名称、无数据、条件化、未知/未收录状态样本齐全
- [x] 固定截图样本全部通过（本表 + R2 样本）

## 6. 残余风险

- 未知/未收录状态在真实村民数据中不出现，其 App 显示路径由既有状态文案逻辑覆盖，缺少真实截图样本；若未来数据出现缺口，需补设备样本。
- 列表按 SQLite 二进制序（Unicode 码点）而非拼音排序；决策 11 的浏览排序语义由 R4 波次处理。
- 类别内搜索对拼音/英文名命中行为（如「wentesi」）需要与决策 11 的受控搜索语义对齐，R4 处理。
