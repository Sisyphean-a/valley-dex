---
处理方式: 裁决
状态: 关闭
认领者: "pi-session:019ff63a-1ae9-77a3-bb53-7f48431cced4"
硬依赖:
  - decisions/03-player-semantics.md
  - decisions/04-category-information-contracts.md
  - decisions/05-character-relationship-model.md
  - decisions/06-condition-and-provenance.md
  - decisions/07-critical-fact-gap-policy.md
  - decisions/08-image-quality-gate.md
---

# 跨仓库数据契约

## 问题

builder 应通过哪一版发布契约向应用提供面向玩家的结构化事实、关系、条件、来源和质量覆盖，同时如何迁移现有 schema 4 与 `officialDerived` 消费边界？

## 答案

### 版本与切换

采用破坏性新契约：

- SQLite 为 **schema 5**；
- 包清单为 **manifestVersion 2**；
- schema 5 的唯一内容语义标识为 **`player-facts-v1`**；
- 新版 app 只安装该版本组合；schema 4 是旧读取协议，不在设备上原地转换为 schema 5；
- builder 默认只生成新契约，不长期双写 `officialDerived`；不兼容语义变化必须升级数据库 schema。

manifest 版本只表示包封装与清单结构；数据库 schema 同时写入 `PRAGMA user_version=5` 和构建元数据。`player-facts-v1` 固定映射 schema 5 语义，不成为可自由组合的第三套版本。

### 规范数据边界

schema 5 至少包含以下职责明确的表或等价只读投影：

- `entities`：规范实体 ID、类型和基础展示字段，不承载玩家事实；
- `fact_slots`：分类契约核心问题的状态与单值；
- `fact_items` 或类型化领域子表：报价、兑换、材料、时间窗、地点、升级和加工等多值结构；
- `relation_groups`：某实体某关系族整体的已知、未知、暂未收录或不适用状态；
- `relations`：有方向、有目标的关系边；
- `condition_sets` / `condition_terms`：去重后的规范条件、原文、玩家解释及完整性；
- `source_documents` / `source_locators`：官方文件、补充页面修订及记录定位；
- `evidence` / `claim_evidence`：事实或关系与证据、输入事实及转换规则的关联；
- `visuals`：实体视觉角色、状态、文件、来源、裁切、哈希、规则版本与复用；
- `entity_cards` / `browse_facets`：列表摘要和筛选的预计算热路径；
- `entity_aliases` / `id_aliases`：搜索别名与旧 ID 到规范 ID 的显式重定向。

原始官方字段只能进入构建诊断、报告或隔离的原始证据区；app 的公共 repository、DTO、`WikiCatalogue` 和页面不能读取它们。schema 5 不发布 `officialDerived` 读取路径。

### 事实与关系不变量

- 每个实体在其分类契约要求的每个核心问题上恰有一个事实槽；不为所有潜在字段生成笛卡尔积空行。
- 状态闭集为 `fixed`、`conditional`、`dynamic_rule`、`unknown`、`not_collected`、`not_applicable`；`rejected` 只存在构建诊断。
- 已知事实必须有类型化值和证据；条件事实必须引用条件集合。
- `unknown`、`not_collected`、`not_applicable` 不携带伪造值；缺少数据库记录不等价于任何状态。
- 多值事实由父槽表达整体状态，子项只承载真实存在的类型化值，不生成空目标。
- 条件规范化去重，保留 `complete`、`partial` 或 `opaque` 完整性以及全部原始子句。
- 关系族状态和关系边分开；每条边保存 subject、闭集 predicate、object、原始方向、条件和证据。反向展示只来自另一条有证据边或已验证互逆规则。
- 可婚配资格仍是事实，不是人物关系。

### 稳定身份与来源

- 实体继续使用 `<entity_type>:<official-id>`；实体类型与官方源 ID 适用时具有唯一约束。
- 事实、关系、报价、加工规则等公共记录使用确定性稳定 ID，不包含中文名、数组顺序、来源路径或当前值。
- 官方没有稳定 ID 时使用仓库中经审核的登记键；内容哈希只做校验摘要，不作为随内容变化的公共 ID。
- 来源文档与记录定位去重，事实和关系通过连接表引用；派生事实记录稳定转换规则版本和输入证据。
- 补充事实使用相同公共 Fact DTO，但来源类别为 `supplemental`；冲突或过期失效后，已调查者发布 `unknown`，尚未接入链路者发布 `not_collected`。
- 展示覆盖只能修改获准展示/搜索投影，不能写入事实、关系、证据或官方来源表。
- 所有引用启用 SQLite 外键，并在构建、独立打包和安装时执行 `foreign_key_check`。

### Manifest 2 与能力协商

manifest 2 至少声明：

- format、manifestVersion、数据库文件/schema/SHA-256、`player-facts-v1`；
- 必需与可选能力；
- builder、游戏、语言和发布策略版本；
- 图片清单文件、哈希与数量；
- 核心事实、条件完整性、来源、补充事实、关系和逐分类视觉覆盖摘要；
- 质量状态、发布资格和报告内容绑定。

未知可选能力可以忽略；未知必需能力必须拒绝。

### 安装拒绝矩阵

以下任一情况拒绝安装且不激活：

- 不支持的 manifest/schema/content contract 或必需能力；
- manifest、数据库元数据、图片清单和报告不一致；
- 数据库哈希、schema 指纹、`quick_check`、`foreign_key_check` 或必需索引失败；
- 核心事实槽缺失/重复、非法状态和值组合、断裂关系、缺少证据或方向无效；
- 条件不完整却标为固定事实；
- 补充事实过期、冲突或冒充官方来源；
- 图片清单、内容哈希、视觉状态或待复核门禁不一致；
- 任意半迁移或双语义数据库。

### App 迁移与回滚

- 删除 schema 5 公共路径中对 `extra_json`、`officialDerived` 和 `legacyFields` 的直接消费；如需保留 schema 4 代码，只能作为完全隔离且不被新版产品使用的旧协议 adapter。
- 页面只消费类型化 repository/DTO；`WikiCatalogue` 继续是页面内容边界，列表与搜索使用 `entity_cards` 和 `browse_facets`，不加载完整证据图。
- builder 在独立 staging 生成数据库、图片、报告与 manifest，全部门禁通过后从封存内容打包。
- app 解包到新版本目录，完成全部语义校验后原子切换活动指针；失败保持原活动包。
- 至少保留一个已验证旧包用于回滚；包目录键包含版本组合与数据库哈希，不覆盖原包。
- 首个 v5 发布前冻结真实 v4 包，并生成实体 ID、核心事实状态、关系、图片和分类覆盖迁移差异报告。
- 旧 app 只能重新激活保留的 v4 包，不能打开 v5；新版产品不双栈消费 v4/v5。
- v5 语义错误通过重新发布完整 v5 包或升级 schema 修复，不在运行时回退解析 `officialDerived`。

### 性能与查询

事实、关系、证据、条件、视觉、别名和筛选表为主外键与双向查询建立必要索引；具体索引集合由实现以列表、详情、反向关系和筛选查询计划验证。列表/搜索投影不得成为另一份可独立解释的事实源：它们由规范事实构建并通过质量检查保持一致。

### 语义所有权

- builder 的 `shared:artifact-contract` 拥有 schema 5、manifest 2、能力、质量摘要和内容绑定；`context:offline-official-data` 拥有事实、关系、条件、来源和视觉的产出语义。
- app 的 `context:content-package` 拥有版本准入、全语义校验、原子激活与回滚；`context:offline-encyclopedia` 拥有类型化契约到玩家阅读模型的表达，但不解释原始官方字段。

## 依据

- 用户在 2026-08-13 对破坏性 schema 5、规范表边界、事实/关系不变量、稳定 ID/来源、manifest 2、拒绝矩阵、迁移回滚及补充事实失效状态确认“全部接受”。
- 当前 schema 4 只有 `build_meta`、通用 `entities.extra_json`、别名与搜索表；事实、关系、条件、来源、补充事实和视觉状态无法用外键或状态约束校验。
- 当前 `officialDerived` 同时混合单值事实、数组、跨表关系和歧义字段，`_provenance` 只有实体级文件集合；app 又在 `DetailPresentationParser`、`WikiCatalogue` 和村民支援解析中直接解释 raw JSON。
- 当前包校验能验证数据库哈希、`quick_check`、基础元数据、数量和部分图片，但无法在激活前证明事实槽、关系、条件、证据和视觉契约完整。
- 当前 app 已采用 staging、备份恢复和活动包生命周期锁，当前 builder 已采用临时数据库和临时 ZIP 原子替换，可作为 v5 原子迁移基础。
- 当前态链接：builder 的 `architecture/shared/artifact-contract.md`、`requirements/contexts/offline-official-data.md` 及 ADR 004；app 的 `requirements/contexts/content-package.md`、`requirements/contexts/offline-encyclopedia.md` 及 ADR 005。
