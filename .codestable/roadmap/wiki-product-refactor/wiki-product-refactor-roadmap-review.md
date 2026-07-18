---
doc_type: roadmap-review
roadmap: wiki-product-refactor
status: passed
review_state: passed
review_reason: ""
reviewer_id: "/root/roadmap_review"
reviewed: 2026-07-18
round: 3
---

# wiki-product-refactor roadmap 审查报告

## 1. Scope And Inputs

- Roadmap: `.codestable/roadmap/wiki-product-refactor/wiki-product-refactor-roadmap.md`
- Items: `.codestable/roadmap/wiki-product-refactor/wiki-product-refactor-items.yaml`
- Related docs: `.codestable/requirements/stardew-offline-android.md`、`.codestable/requirements/database-reference.md`、`.codestable/goals/2026-07-17-stardew-offline-android/state.yaml`、README
- Compound / drafts: none
- Code facts checked: `core/datapackage/DataPackageContract.kt`、`DataPackageValidator.kt`、`DataPackageManager.kt`、`core/database/content/ContentDatabase.kt`、`ContentDatabaseManager.kt`、`feature/home/HomeFeature.kt`、`feature/detail/DetailScreen.kt`、`core/ui/component/EntityImage.kt`、`EntityListItem.kt`

### Independent Review

- Status: completed
- Detection: independent-agent
- Provider / agent: `/root/roadmap_review`
- Raw output: 初审提出固定目录、数据包生命周期、关系降级、样本策略、最小闭环边界和旧 schema 文档冲突等问题；本地逐条核验后修订。第 3 轮复审确认当前 roadmap/items 无 blocking 或 important。
- Merge policy: 独立 finding 均以当前 roadmap、schema 4 数据参考和已索引 Android 模块事实复核；已修订项未保留为未解决 finding。
- Gate effect: none

## 2. Roadmap Summary

- Goal completion signal: 合格 schema 4 包进入中文语义首页，可在不暴露 raw type 的路径中浏览、阅读、搜索和保留个人数据。
- Module split: 发布数据包边界、图鉴内容领域、语义目录、Wiki 浏览体验、个人连续性、验证与质量六个模块。
- Interface contracts: `DataPackageService`/`ContentPackageAccess` 的双角色协调器、`WikiCatalogue`、`WikiEntry`/三态关系模型以及稳定 ID 路由。
- Items: 10 条，`wiki-semantic-home` 为唯一最小闭环；其前置保证使用 schema 4 合格包和已重构详情。
- Dependency shape: DAG；无未知依赖、无自指、无循环。

## 3. Findings

### blocking

- none

### important

- none

### nit

- none

### suggestion

- none

### learning

- 把详情阅读置于语义首页最小闭环的前置条件，可避免“首页像图鉴、详情仍是数据库字段”的假完成。
- 真实游戏数据不能提交时，应将“拒绝路径自动化”和“真实包显式验收”分开，不能用不可发布 fixture 伪造成功导入。

### praise

- 发布资格、稳定 ID、只读内容库和不虚构攻略内容的边界与 schema 4 数据参考一致。
- 首版固定目录表让产品信息架构、类型覆盖与未知类型处理可以被用户直接审阅。

## 4. User Review Focus

- 用户需要重点拍板：首版四个分区及其分类映射是否符合想要的“Wiki 式图鉴”；是否接受从此只接受发布级 schema 4、明确拒绝旧 schema 2 包。
- 后续 feature-design 需要重点复核：目录配置与真实 `content.entityTypes` 的覆盖、`ContentPackageCoordinator` 的切包句柄顺序、类型详情的字段/关系呈现。
- 不能靠 roadmap review 完全确认的点：真实发布级 schema 4 `.svdata` 尚未放入本工作区；必须由数据构建器生成后通过 `STARDEW_SVDATA` 的显式验收入口验证。

## 5. Evidence Confidence Ledger

| Check | Verdict | Evidence Class | Basis | Follow-up |
|---|---|---|---|---|
| Granularity Gate | pass | E | roadmap 第 2、3、5 节列出跨数据、领域、导航和个人数据的独立交付 | none |
| Goal Coverage Matrix | pass | E | roadmap 第 5 节每个核心完成信号均有 item、验证入口和证据类型 | 真实 v4 包验收按计划执行 |
| DAG and minimal loop | pass | E | items.yaml 10 条；校验结果为无未知依赖、无环且仅一条 `minimal_loop=true` | none |
| Interface contract usability | pass | E/C | roadmap 第 4 节定义字段、状态、错误、路由和生命周期；现有 `DataPackageManager`/`ContentDatabaseManager` 事实支撑协调器边界 | feature-design 复核实际签名 |
| Module interface depth | pass | C | 当前 `HomeFeature` 直接使用 raw type，`DataPackageValidator` 与内容数据库分别持有校验/查询职责；roadmap 将复杂度收束到协调器、目录和图鉴领域 | feature-design 防止退化为 pass-through |

Summary: E=3, C=2, H=0, H-only core checks=none。

## 6. Residual Risk

- 真实发布级 schema 4 包是外部前置；没有它不得把成功导入或真机验收标为通过。
- 构建器不求值游戏条件，也不提供攻略文章。详情只能展示已知的官方条件信息或明确未收录状态，不能把内部条件表达式当作玩家结论。

## 7. Verdict

- Status: passed
- Next: 交给用户 review；用户确认后将 roadmap 从 `draft` 标为 `active`，新对话可从 `wiki-refactor-safety-net` 开始。
