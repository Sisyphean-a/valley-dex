# R1 证据：红色产品门禁已建立（当前 schema 5 实现全红）

> 按 [RECOVERY.md](RECOVERY.md) R1 建立能稳定复现当前故障的自动测试。
> 生成时间：2026-08-15（会话运行日）。
> 原则：这些测试在当前实现上**必须失败**；R2+ 修复实现使其转绿，不得放宽断言。

## 1. 门禁清单与当前结果

### builder 侧（`E:\github\stardew-offline-data-builder\tests\test_player_ui_gates.py`）

**投影级门禁（合成 fixture，快速红）—— 6 个全部 RED：**

| 测试 | 复现的截图故障 | 当前失败信息示例 |
|---|---|---|
| `test_villager_birthday_is_localized_chinese` | Fall 11 未翻译枚举 | `villager:Jodi / birthday 泄露未本地化枚举：'Fall 11'，应为「秋季 11 日」` |
| `test_villager_residence_region_is_localized_chinese` | 常住地 Town 未翻译 | `'Town'，应为「鹈鹕镇」` |
| `test_villager_gender_is_localized_chinese` | Female 未翻译 | `'Female'，应为「女性」` |
| `test_gift_items_have_zero_unresolved_or_raw_references` | Oh/you're 未解析 token | `未解析礼物引用：Oh,`、原始 `object:72` |
| `test_villager_card_has_birthday_and_residence_action_summaries` | 卡片无行动摘要 | `(None, None)` |
| `test_schedule_items_do_not_leak_internal_codes` | 日程内部代号 | `'800：SamHouse 6 5 0 jodi_dishes；…'` |

**真实候选门禁（`PLAYER_UI_REAL_CANDIDATE_DB` 指向真实 schema 5 候选 `stardew.db`）—— 4 个全部 RED：**

| 测试 | 实测结果 |
|---|---|
| `test_real_candidate_has_zero_player_ui_leaks` | 真实候选 1546 处玩家界面泄露：生日未本地化 34、常住地 34、性别 33、日程内部地点 214、日程 Strings 令牌 30、未解析礼物引用 746、礼物原始引用 410、官方分类引用 44、类别引用 1 |
| `test_real_candidate_villager_cards_have_action_summaries` | 34 个村民卡片全部缺失摘要（列出 12 个实体名） |
| `test_real_candidate_villager_portraits_are_full_portraits` | 34 个村民肖像全部 32×64 过窄（半脸裁切），列出实体与路径 |
| `test_real_candidate_manifest_has_chinese_display_names` | 25 个可浏览类型 displayName 全部为英文内部名（achievement、big_craftable、crop…） |

### App 侧（`app/src/androidTest/.../RealV5PlayerGateTest.kt`，真实包注入式）

在模拟器 `emulator-5554` 上用真实候选 `stardew-zh-cn.svdata`（2026-08-15 构建，6,964,068 字节）运行 —— **4 个全部 RED：**

| 测试 | 当前失败信息摘要 |
|---|---|
| `everyBrowsableCategoryTitleIsApprovedChinese` | 23 个分类标题全为内部名（type:object -> object、type:crop -> crop…） |
| `jodiEntryHasNoTechnicalLeaksAndLocalizedCoreAnswers` | 类别标签 `villager`；生日 `Fall 11；来源：官方原始数据；Data/Characters.json；版本…证据 direct`；性别 `Female`；礼物偏好 `未解析礼物引用：Oh,`；转换规则名进入事实；来源重复拼进每条事实；礼物/日程混入核心信息；常住地排在生日之后 |
| `everyVillagerCardHasActionSummaries` | 34 个村民卡片缺少摘要（乔治、乔迪、亚历克斯…） |
| `noPlayerFactValueIsARawEntityReference` | `cooking_recipe:Triple-Shot-Espresso / 制作材料 原始实体引用：object:395…` |

### 故障 → 断言映射（RECOVERY.md 九类故障全覆盖）

1. 内部类型名上首页/分类 → manifest 中文名门禁 + App 分类标题门禁 ✔
2. 村民卡片无生日/常住摘要 → builder + App 卡片摘要门禁 ✔
3. 头像半脸裁切 → 肖像宽度门禁（32×64 全量命中）✔
4. Fall 11 / Female / direct / derived → 本地化门禁 + 证据类型门禁 ✔
5. 来源/版本/证据/转换重复拼进每条事实 → “把来源重复拼进玩家事实”断言 ✔
6. 礼物未解析 token → 礼物门禁（builder + App）✔
7. 同类事实大量重复行 → “礼物/日程混入核心信息”断言 ✔
8. 详情无契约顺序 → “常住地应排在生日之前”断言 ✔
9. 验收只查安装/非空 → 新门禁逐实体执行玩家任务（生日值、分类名、卡片摘要、礼物、图片尺寸），不再断言非空 ✔

## 2. 运行方式

```powershell
# builder 投影级（红）
cd E:\github\stardew-offline-data-builder
python -m pytest tests\test_player_ui_gates.py -q

# builder 真实候选（红；需真实候选目录）
$env:PLAYER_UI_REAL_CANDIDATE_DB = "E:\github\stardew-offline-data-builder\.tmp\goal-full-candidate\stardew.db"
python -m pytest tests\test_player_ui_gates.py -q -k real_candidate

# App 真实包门禁（红；模拟器/真机）
.\gradlew.bat :app:assembleDebugAndroidTest
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb push <真实v5包> /data/local/tmp/stardew-v5.svdata
adb shell am instrument -w -r -e realV5Required true -e realV5PackagePath /data/local/tmp/stardew-v5.svdata `
  -e class com.example.stardewoffline.core.datapackage.RealV5PlayerGateTest `
  com.example.stardewoffline.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## 3. 无回归证据

- builder 全量 pytest（不含新门禁）：**229 passed**（6.75s）
- App `:app:testDebugUnitTest` + `:app:lintDebug`：BUILD SUCCESSFUL
- 门禁文件 `ruff check` 通过

## 4. 关键事实记录（为 R2 修复做锚点）

- 真实候选位置与 hash：`builder\.tmp\goal-full-candidate\stardew-zh-cn.svdata`（SHA-256 待 R2 重建时更新）；游戏版本 1.6.15.24356
- `villager:Jodi` 当前事实槽：birthday=`Fall 11`、gender=`Female`、residence_region=`Town`、can_be_romanced=0；卡片摘要 `(None, None)`；肖像 `images/villager-Jodi.webp` 32×64（Qwen-VL 复核确认半脸裁切）
- 礼物槽：43 个 item，其中 35 个 `未解析礼物引用：<英文token>`、若干 `object:N` 与 `官方分类引用：-N`
- 日程槽：13 个 item，含 `SamHouse 6 5 0 jodi_dishes` 类内部坐标与 `Strings\\…` 令牌
- manifest `entityTypes[].displayName` 全部等于英文内部 id（25 个）

## 5. R1 完成标准核对

- [x] 每类截图故障都有至少一个失败断言（9 类全覆盖，见映射表）
- [x] 失败信息指出实体、槽和泄露值（示例见上表，均为真实断言输出）
- [x] 先红后修：门禁在当前实现上稳定失败，未做任何放水
