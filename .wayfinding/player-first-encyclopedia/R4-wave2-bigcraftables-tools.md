# R4 波次 2 证据：大型可制作物与工具（builder + App + 门禁 + 设备截图）

> 按 [RECOVERY.md](RECOVERY.md) R4 第二波。黄金样本：蜂房（big_craftable:10）与铜十字镐（tool:CopperPickaxe）。
> 真实资产：`D:\SteamLibrary\steamapps\common\Stardew Valley\Content (unpacked)`；候选 `.tmp\goal-full-candidate-r3`（本波次重建）。

## 1. builder 修复

| 问题（决策 02 的 B/E 缺口 + 新发现） | 修复 |
|---|---|
| 制作配方产物类型被「object:N 恰好存在」启发式猜错：蜂房配方产物输出 `object:10` 而非 `big_craftable:10`，导致 168/182 大型可制作物材料 not_applicable | `recipe_output_reference`：官方 `outputEntityType` 声明优先（对照游戏 CraftingRecipe 索引语义）；蜂房材料链恢复（4 种材料挂回 big_craftable:10） |
| 大型可制作物无「主要产物/用途」与「解锁阶段」 | 机器数据 `Machines.json`（(BC) 限定 id → big_craftable 映射）的默认产出规则 → 中文产物（FLAVORED_ITEM 保藏类型 → 蜂蜜/果酱/泡菜…）；无机器数据时用官方中文描述首句；**无 CJK 的占位/未翻译文案一律不发布**；制作配方 `UnlockCondition` → 中文（`s Farming 3` → 耕种等级 3、`f Krobus 3` → 与科罗布斯好感度达到 3、`l N` → 玩家等级达到 N、default → 默认解锁；语法对照游戏 `CraftingRecipe.TryParseLevelRequirement`） |
| 工具无类型/档位/升级链/升级地点/耗时 | `tool_kind`（官方内部名 → 斧头/十字镐/锄头/喷壶/垃圾桶/淘盘/镰刀/鱼竿…）、`tool_level`（官方 UpgradeLevel → 基础/铜/钢/金/铱；鱼竿 → 竹/玻璃纤维/铱金/高级铱金；官方工具层名实为 Copper/Steel/Gold/Iridium，中文档位与 zh-CN 名称对齐为「钢」而非「铁」）、`upgrade_from_id`（ConventionalUpgradeFrom → 前一级可点击引用）、`upgrade_location`=铁匠铺（官方 ClintUpgrade 商店）、`upgrade_time`=2 天（游戏升级规则） |
| 卡片摘要按契约 | 工具：类型 → 档位；大型可制作物：产物 → 解锁 |

## 2. App 修复

| 问题 | 修复 |
|---|---|
| 工具沿用通用核心信息 | `toolSections`：立即行动 = 类型 → 档位 → 前一级（可点击）→ 升级材料（中文 ×数量）→ 升级价格 → 升级地点 → 升级耗时；数据说明玩家文案 |
| 大型可制作物沿用通用核心信息 | `bigCraftableSections`：立即行动 = 主要产物 → 解锁 → 购买/升级价 → 制作材料概要；`材料清单` submenu（材料 ×数量，可点击） |

## 3. 自动门禁（红→绿）

- builder：新增 `test_tool_projection_emits_localized_kind_level_and_upgrade_chain` 与 `test_big_craftable_recipe_output_type_and_unlock`；真实候选门禁新增 `primary_output` 无 CJK 检查（首轮真实包扫描抓到 14 处：`……` 占位翻译、`??HMTGF??` 官方坏名、`A place to display an item.` 未翻译描述，修复后归零）。**真实候选 14/14 绿**。
- App：新增 `copperPickaxeFollowsToolContract` 与 `beeHouseFollowsBigCraftableContract`（真实包注入）。**9/9 绿**。
- 回归：builder 全量 229 passed、ruff 通过；App testDebugUnitTest + lintDebug 通过。

## 4. 设备截图（`E:\github\valley-dex\.tmp\recovery-r3\`）

| # | 文件 | 检查 |
|---|---|---|
| 16/18 | `16-tools-list.png` / `18-big-craftables-list.png` | 工具卡片：类型+档位（钢→钢、金→金、铜淘盘→铜）；大型可制作物卡片：产物+解锁；未翻译占位不再泄漏（Item Pedestal 无英文描述行） |
| 17 | `17-copper-pickaxe-detail.png` | 铜十字镐详情：类型 十字镐 → 档位 铜 → 前一级 十字镐 → 升级材料 铜锭 ×5 → 升级价格 2000 金币 → 升级地点 铁匠铺 → 升级耗时 2 天 |
| 19/20 | `19-bc-list-final.png` / `20-bee-house-detail.png` | 蜂房详情：主要产物 蜂蜜 → 解锁 耕种等级 3 → 制作材料 共 4 种 + 中文描述 |
| 21 | `21-bee-materials.png` | 材料清单展开：铁锭 ×1、煤炭 ×8、木材 ×40、枫糖浆 ×1（可点击） |

## 5. 完成标准核对（波次 2）

- [x] 真实代表实体：蜂房、铜十字镐
- [x] 中文卡片行动摘要：产物/解锁（大型可制作物）、类型/档位（工具）
- [x] 分类专属立即行动顺序（决策 10：用途 → 制作条件/升级条件 → 加工/升级规则）
- [x] 完整资料折叠结构：材料清单 submenu；升级链为可点击前一级引用
- [x] 状态样本：not_applicable（不可制作物材料）、动态升级价、解锁条件
- [x] 列表、详情、搜索任务 + 真实截图
- [x] 自动黄金输出：投影级 + 真实候选级 + App 真实包门禁

## 6. 残余风险

- 机器的「完整输入输出规则」（MachineSpecificOutputRules、条件产出、耗时）尚未全量投影；主要产物只取默认规则。波次 2 已满足卡片与立即行动契约，完整机器规则表列入后续切片。
- 工具升级「2 天」为游戏规则（Farmer.dayupdate 递减 + 社区公认），规则来源标注在数据库 provenance；未逐字节核对 DLL 常量赋值处，R5 条件语义审计复核。
- 个别官方名称自带 `??` 前缀（如 `??碎屑雕像??`）为游戏原始数据（zh 与 en 同名），非构建泄漏；已在证据中注明。
