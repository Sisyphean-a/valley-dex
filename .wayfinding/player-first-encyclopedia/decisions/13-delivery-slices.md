---
处理方式: 前置
状态: 关闭
认领者: "pi-session:019ff63a-1ae9-77a3-bb53-7f48431cced4"
硬依赖:
  - decisions/09-cross-repo-data-contract.md
  - decisions/10-information-hierarchy-prototype.md
  - decisions/11-search-and-browse-model.md
  - decisions/12-release-quality-and-validation.md
---

# 实施交付图

## 问题

应如何把 builder、数据包迁移、应用消费、分类体验和真实验收拆成可独立验证的实施切片，并明确每个切片的仓库归属、依赖、回滚点和完成证据？

## 答案

### 总体结果与边界

实施完成时，builder 从真实官方资产生成唯一、可追溯且通过门禁的 manifest 2 / schema 5 / `player-facts-v1` 包，App 只通过类型化 `WikiCatalogue` 安装、查询和呈现该包，八类玩家任务、搜索浏览、图片、无障碍、性能和回滚均由真实候选证据证明。

本交付图不引入联网、存档读取、schema 4/5 双语义消费、设备端 v4 转换、运行时 `officialDerived` 回退或第二套 Wiki 数据源。每个切片按可观察结果拆分，不按文件或团队拆分；“审查”“最终集成”不作为独立伪切片。

### 当前检查点（2026-08-14）

本轮以两仓库各一个可回退提交收束当前阶段，不把合成 fixture、编译通过或候选路径接线误报为真实发布完成。

**已封存且有本地验证的结果**

- D0、B1、B2：真实 v4 基线仍在工作区外保留；schema 5 SQLite、manifest 2、`player-facts-v1`、typed facts/conditions/evidence/claim 闭包、视觉/关系/卡片/facet 的 writer 与 conformance fixture 已形成。
- A1：App 普通安装、活动包校验和兼容回滚严格面向 schema 5；schema 4 仅显式 recovery pin，保留 `active_v5`、`previous_compatible_v5`、`pinned_legacy_v4` 三个生命周期边界。
- B3/A2/B4：App 已有租约式 typed schema-5 数据库、批量详情读取、来源摘要、正反关系、数据库侧 facet/搜索游标和 Schema5 `WikiCatalogue`；builder 已有真实投影的 staging/candidate、原子 writer、报告/conformance 哈希绑定和独立 package 复验路径。
- 当前检查点验证：builder `python -m pytest -q` 为 `209 passed`，`python -m ruff check .` 与两仓库 `git diff --check` 通过；App `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin`、`:app:lintDebug` 在 `--rerun-tasks` 下通过。

**仍未完成、不得在本检查点后隐含关闭的结果**

- C1–C4 的真实官方逐分类核心槽覆盖、全部玩家语义和完整黄金输出仍未完成；尤其 C1 还必须区分金币购买价、兑换成本、条件报价、动态报价和 `not_applicable`。
- builder 真实官方候选尚未形成可发布 `.svdata`；失败必须继续隔离并保留 `.release-blocked` 诊断。合成 candidate/staging 只证明 writer 与门禁形状，不替代 R1。
- App 尚无真实 v5 包安装/切换/回滚、connected Android、TalkBack、性能 SLO 和玩家任务回执；R2/R3 未开始。

**下一独立目标：C1 报价语义闭合**

只处理 builder 的官方商店报价到 typed player-facts-v1 的规范映射，并同步必要的 App/fixture 契约：按 `ShopBuilder.GetBasePrice` 对 `Price=-1`、对象价格、`UseObjectDataPrice`、`IgnoreShopPriceModifiers` 和 shop/item modifier 做可审计计算；将金币报价与兑换成本分开，保留商店/offer/条件/source locator 和稳定 scope；crop key 到 seed object 的关联必须有回归；无普通报价的作物只能进入明确的 `not_applicable`/`not_collected`，不得用对象出售价冒充购买价。完成证据为 focused regression、builder 全套门禁、真实报价覆盖诊断和失败输出隔离；不要求在该目标内完成 R1–R3。

### D0：冻结迁移基线

**归属**：两仓库共同准备，各自保留证据；发布集成人记录内容绑定。

**结果**：在工作区外冻结真实 schema 4 包、数据库、输入与包 hash，记录实体 ID、类型、核心旧字段、图片和分类覆盖，并建立两仓当前测试基线和 v4→v5 差异模板。

**依赖**：无，是全部实现切片的根节点。

**回滚点**：冻结包不可覆盖；真实资产不进入 Git。

**完成证据**：可复核 hash、基线测试结果、差异报告模板和保留位置。

### B1 / A1：契约内核与消费者拒绝边界

#### B1 builder schema 5 内核

**结果**：生成非发布型 schema 5 SQLite 和 manifest 2，固定 `player-facts-v1`、`PRAGMA user_version=5`、实体/别名/ID 重定向、能力、schema 指纹和基础索引；不双写公共 `officialDerived`。

**依赖**：D0。

**回滚点**：只写独立 staging；失败回到 schema 4 构建提交，不做数据库降级迁移。

**完成证据**：schema 指纹、表/约束/索引清单、版本/能力拒绝测试、`publishable=false` conformance fixture。

#### A1 App 契约准入与回滚模型

**结果**：App 建立 manifest/schema/capability 模型、staging 语义验证框架、schema 4/半迁移/未知必需能力/损坏包拒绝矩阵和原子激活；包保留位分为：

- `active_v5`：当前业务读取包；
- `previous_compatible_v5`：新版 App 可直接完整验证后回滚的上一 v5；
- `pinned_legacy_v4`：只供旧版 App 整版恢复，不进入新版普通回滚入口，也不被普通清理删除。

首个 v5 完整发布周期和旧 App 回退窗口结束前不得清理 `pinned_legacy_v4`；清理需要显式发布决定。

**依赖**：B1 冻结的协议和 conformance fixture，不依赖真实 v5 数据。

**回滚点**：A1 尚未接入业务查询；失败保留当前 v4 App 行为和数据目录。

**完成证据**：规范成功 fixture、逐项坏包 fixture、活动指针原子性、三保留位生命周期和失败保持原包测试。

B1 的 DDL/索引、manifest/能力模型和拒绝 fixture 可在公共字段名、枚举和版本常量冻结后并行；builder 是协议唯一所有者。

### B2 / B3 / A2：规范事实骨架

#### B2 事实、状态、条件、来源与证据

**结果**：建立核心事实槽、类型化事实项、六种发布状态、条件集合和完整性、来源文档/定位器、证据和转换链；稳定 claim ID 不依赖中文名、数组顺序、路径或当前值。

**依赖**：B1。

**回滚点**：可删除本切片表和 writer，保留绿色的 B1 空内核。

**完成证据**：状态合法/非法组合、条件不丢失、来源去重、派生输入、重复槽、断裂证据和外键测试。

状态/事实、条件、来源/转换和语义 validator 可按冻结模型并行。

#### B3 关系、视觉与补充事实

**结果**：建立关系组和有向关系、人物关系闭集、实体视觉状态及内容绑定、补充事实准入/过期/冲突；查询索引支持正反浏览，但不生成无证据反向事实。

**依赖**：B2；视觉表结构只硬依赖 B1，可提前并行，语义闭环依赖 B2。

**回滚点**：关系、视觉和补充 lane 各自可撤销，不改变 B2 事实。

**完成证据**：方向/目标/证据、空关系组、视觉状态/hash/复用、补充冲突/过期和非法推断拒绝测试。

#### A2 类型化内容 repository 与 DTO

**结果**：建立卡片、事实、条件、关系、视觉、来源摘要、搜索/facet 和审计的类型化 row/DTO；公共 API 不暴露 `JsonObject`、`extraJson`、`officialDerived` 或 `legacyFields`；查询不跨活动包生命周期持有句柄。

**依赖**：B1 的 DDL，以及 B2/B3 冻结的状态、predicate、视觉状态和值类型；不依赖真实包。

**回滚点**：在真实切换前可整体移除 v5 repositories，不触碰个人 Room 数据。

**完成证据**：规范小型 v5 fixture 的绑定、批量读取、方向、scope、游标、切包失效和公共 DTO 禁止 raw JSON 测试；规模 fixture 只证明查询形状和趋势，不冒充真实性能证据。

### C1–C4：分类事实纵向切片

每个分类切片必须同时交付 builder 规范事实、类型化 fixture、`WikiCatalogue` 黄金输出和该分类玩家任务，不能以“已解析字段”单独完成。

| 切片 | 结果 | 主要证据 |
|---|---|---|
| C1 商店与作物 | 地点/营业或出现规则、购买价/兑换成本/条件；季节、成熟、种子来源、售价；首条完整搜索/facet/scope 链 | 报价不成为实体固有价、作物来源与价格解析、同 scope 条件、列表和详情黄金输出 |
| C2 大型可制作物与工具 | 制作物子类、用途、材料、制作/加工规则与材料反查；工具有序升级链、价格、材料、耗时和效果 | 正反向制作关系、不可制作项状态、完整升级路径与缺口状态 |
| C3 村民 | 常住地、生日、礼物、日程规则和有向人物关系；玩家当前关系保持动态/不适用 | 文森特和贾斯只显示未注明亲友关联，住所/LoveInterest/CanBeRomanced 不升级成当前关系 |
| C4 怪物、鱼与武器 | 出现区域/矿井层段、掉落；水域、地点、季节、时间、天气；武器伤害、价格和获得方式 | 同捕获 scope 多条件、掉落/获得反查、MineBaseLevel 不冒充获得层数 |

**依赖**：B2/B3；每个切片只使用已冻结枚举和核心槽注册表。

**并行**：C1–C4 可在独立实现 lane 并行调查和编码；schema、公共枚举和注册表由 builder 单一契约所有者串行合并。App 的分类表现可在对应完整 fixture 交接后开始，不等全部真实数据。

**回滚点**：单一分类映射和 fixture 可撤回为 `unknown` 或 `not_collected`，但不得恢复公共 raw JSON 消费或伪造值。

### B4 / B5：查询投影与发布门禁

#### B4 卡片、搜索、facet 与反向浏览投影

**结果**：由规范事实生成唯一 `entity_cards`、可追溯搜索词/命中原因、facet group、同 scope `browse_facets`、正反关系索引、稳定排序键和游标；投影不是第二事实源且可逐行重建。

**依赖**：B2、B3、C1–C4；不硬依赖图片物化，可按视觉 ID join。

**回滚点**：投影表可整体删除和重建，不改变规范事实。

**完成证据**：逐行重建相等，同 family OR/跨 family AND/同 scope 联合，未知不生成具体 facet，稳定分页无重复遗漏，查询计划无未批准大表扫描或大型临时排序。

搜索词、卡片、facet/scope 和索引/分页数据集可并行实现，最终重建审计串行。

#### B5 质量报告与独立打包复验

**结果**：绝对门禁、逐分类逐核心槽覆盖、回归预算、补充审计、视觉联系表/人工复核、查询计划和内容绑定进入独立 package 验证；构建成功不能绕过发布门槛。

**依赖**：B2–B4、全部分类和视觉链路。

**回滚点**：失败包隔离为不可发布产物，不替换旧获准包。

**完成证据**：决策 12 的全部报告、失败矩阵、`pending_review=0` 和 fixture 不可发布证明。

### A3–A5：玩家消费体验

#### A3 类型化 `WikiCatalogue` 阅读模型

**结果**：`WikiCatalogue` 只组合类型化事实，输出身份、立即行动、完整资料和数据说明四层；明确事实状态、条件完整性、来源摘要和五类视觉/错误状态；新详情路径不调用旧 JSON parser。

**依赖**：A2 和至少一个完整 C 类 fixture。

**回滚点**：真实 v5 激活前可撤销新入口；不得把旧 parser 接成 v5 回退。

**完成证据**：八类逐步补齐的黄金输出、状态/条件/关系/图片错误边界和公共调用图测试。

#### A4 响应式分类体验

**结果**：分类入口保持四列，实体列表按宽度/字体响应为一至多列，卡片最多两条行动摘要，详情按四层和分类次序渐进展开，覆盖大集合、长名称、无图、大字体、深浅色和 TalkBack。

**依赖**：A3；可按 C1–C4 fixture 分波次交付。

**回滚点**：每个分类 UI 可在 v5 开关尚未进入生产前独立撤回，不影响契约和数据。

**完成证据**：决策 10/12 的 viewport/font/device、可见首答、语义树和分类玩家任务。

#### A5 搜索、facet 与反向浏览

**结果**：数据库侧候选过滤和游标分页、可读命中原因、分类 facet、有向反查、查询快照和空结果恢复完整；不再用固定候选截断或详情 JSON 拼筛选。

**依赖**：B4、A2、A4。B4 契约稳定前不得接生产投影。

**回滚点**：新 v5 尚未激活时可撤回查询入口；失败不回到内存全量筛选作为隐式降级。

**完成证据**：同 scope 组合、全遍历、游标包绑定、切包失效、异步快照、无结果恢复、查询计划和 SLO 测试。

### R1–R3：真实候选与原子切换

#### R1 builder 真实 v5 候选

**结果**：使用真实本机官方资产生成候选，完成 v4→v5 ID/槽/关系/图片/覆盖差异、图片人工复核和 builder `release-attestation`。

**依赖**：B5、C1–C4、D0 和具名图片审核者。

**回滚点**：候选按版本组合和 hash 独立保存，任一失败保持未获准，不覆盖冻结 v4 或前一获准 v5。

**完成证据**：决策 12 的真实构建、覆盖、差异、视觉、查询与审批证据。

#### R2 App 真实包验收

**结果**：真实 v5 完成安装、全验证、查询、切换和兼容 v5 回滚；通过设备矩阵、TalkBack、十组玩家任务和性能 SLO。

**依赖**：R1、A4、A5。

**回滚点**：失败保持当前活动包；`pinned_legacy_v4` 仍只供旧 App 整版恢复。

**完成证据**：内容 hash 绑定的自动、设备、任务、无障碍、性能和回滚回执。

#### R3 联合发布候选

**结果**：一名具名发布集成人锁定两仓 commit、包 hash 和契约版本，汇总 builder 与 App attestation；任一门禁未结清不签发。

**依赖**：R1、R2。

**回滚点**：否决候选，不恢复 v4 解析、`officialDerived` 或运行时猜测；修复后重发完整包或升级 schema。

**完成证据**：唯一联合发布证明、两仓版本、包 hash、审批者、门禁摘要与长期留存记录。

### 依赖图

```text
D0
└─ B1 ─┬─ B2 ─ B3 ─┬─ C1 ─┐
       │            ├─ C2 ─┤
       │            ├─ C3 ─┼─ B4 ─ B5 ─ R1 ─┐
       │            └─ C4 ─┘                 │
       └─ A1                                 │
          └─ A2 ─ A3 ─ A4 ──────────────────┼─ R2 ─ R3
                    └──────── B4 ─ A5 ───────┘
```

A2 实际还依赖 B2/B3 的冻结枚举；A5 依赖 B4。图中省略交叉线以保持可读。依赖图无环，D0 是唯一根节点。

### 跨仓库交接与启动门槛

| 边界 | 唯一所有者 | 交接物 |
|---|---|---|
| schema、manifest、能力、稳定 ID | builder `shared:artifact-contract` | conformance bundle、schema 指纹、fixture hash |
| 事实、条件、证据、关系、视觉 | builder `context:offline-official-data` | immutable `.svdata`、覆盖和差异报告 |
| 包准入、激活、兼容 v5 回滚 | App `context:content-package` | 消费者验收回执和拒绝矩阵 |
| 玩家层级、文案和交互 | App `context:offline-encyclopedia` | typed `WikiCatalogue` 黄金输出及设备任务 |
| 联合发布候选 | 单一具名发布集成人 | 两仓 commit、包 hash 与联合 attestation |

- B1 的公共枚举、schema 指纹和 conformance fixture 冻结前，不开始生产 UI 数据绑定；
- A1 可与 B2 并行，A2 可基于规范 fixture 开始；
- A3/A4 在首个完整分类纵切 fixture 后开始，不等待全部真实数据；
- A5 等待 B4 稳定；真实包、生产切换和 legacy v4 清理只在 R1/R2 门禁完成后进行；
- 每一阶段只回滚自己的独立产物，不恢复双语义源。

## 依据

- 用户于 2026-08-13 接受完整实施 DAG、三类包保留位和各阶段启动门槛。
- 当前 builder 仍是 schema 4：`config.py` 声明 `SCHEMA_VERSION=4`，`database/schema.sql` 仍以 `entities.extra_json` 承载内容，`NormalizedEntity` 没有类型化 claim，官方派生继续写入 `officialDerived`；现有临时数据库和临时 ZIP 原子替换可作为 staging/回滚基础。
- 当前 App `DataPackageContract`、synthetic fixture 和真实包任务仍是 schema 4；`WikiCatalogue`/`EntryFact`/`EntryImage` 无法表达新状态和层级，搜索固定截断后内存合并，列表固定四列，图片加载失败会退化成普通无图。
- 当前 `DataPackageManager` 只有 active/previous，既不能安全区分新版兼容回滚与旧 App 整版恢复，也可能在后续清理中删除 v4 基线，因此必须建立独立 pinned legacy 概念。
- 三路独立只读审查分别覆盖 builder、App 和跨仓库迁移，结论一致：先冻结契约与可执行 fixture，再纵向交付事实和玩家体验，最后用真实包切换；不能采用同时改完所有层的大爆炸迁移。
- 决策 09 提供协议和迁移边界，决策 10 提供 UI 完成形状，决策 11 提供查询投影，决策 12 提供发布证据；本图只组织实现依赖，不重开其已接受语义。
- 本图是唯一实施拆解面；两仓库 `.codestable` 只保存稳定当前态和协议/回滚规则，不复制任务流水。
