---
doc_type: roadmap
slug: wiki-product-refactor
status: draft
created: 2026-07-18
last_reviewed: 2026-07-18
tags: [android, offline, wiki, product-refactor, data-package]
related_requirements: [stardew-offline-android, database-reference]
related_architecture: []
---

# 将数据查询工具重构为离线 Wiki 式图鉴

## 1. 背景

当前应用能安全导入并查询数据，但它把 `entities.entity_type` 直接当作首页分类，把数据库记录直接当作列表内容。用户看到的是 `achievement`、数字名称和“图”占位，而不是“我现在想查什么”的游戏知识入口。数据包、导入与校验被放到了用户感知最强的位置，产品因此呈现为数据库阅读器。

本次重构把产品定义改为：**完全离线、以玩家问题组织内容的星露谷 Wiki 式图鉴**。数据包仍是唯一游戏事实来源，但只作为内容供应链；首页、浏览和详情必须以中文语义分类、可读内容卡片与关联阅读组织，不向普通用户暴露原始类型名、技术 ID、原始 JSON 或解析残片。

这不是复刻其他图鉴的视觉风格，也不是把现有列表换成卡片。它重建“内容如何进入产品”和“玩家如何找到内容”的边界。首期是“可离线阅读的图鉴/百科”，不是完整官方 Wiki：构建器没有提供攻略结论、事件摘要或游戏条件求值，应用不得虚构这些内容。

### 当前规范冲突与本路线图优先级

`.codestable/requirements/stardew-offline-android.md` 和 README 仍把项目描述为 schema 2 的“数据查询工具”，这与当前 schema 4 数据参考和本次产品重构冲突。**本路线图经用户确认后，第 1 至 4 节是后续 feature-design 对产品定位、schema 和导航的硬约束；不得因为旧文档仍写 schema 2 而继续实现旧路径。** `schema4-package-contract` 的完成条件包含同步更新该需求文档和 README，使仓库不再保留两套相互矛盾的实施指令。

### 目标完成信号

1. 用户导入合格的 schema 4 数据包后，首页展示中文语义入口和可读分类，不显示数据库原始 `entity_type` 作为主导航。
2. 从首页进入任一已有分类时，列表展示中文名称、有效图片或明确的产品级占位、用户可理解的分类和计数；不显示纯数字名称、`achievement` 等原始类型标签或原始字段串。
3. 详情页以“概览—类型信息—关系阅读”的结构展示已支持的官方数据；关系可进入对应实体，无法解析的关系以可读文本呈现，不能抛出技术 ID 或 JSON。
4. 搜索、收藏和最近浏览继续基于稳定实体 ID 工作；更换合格数据包后仍保留用户数据，已删除实体被明确标记而非崩溃。
5. 数据包必须同时满足 schema 4、`publishable = true`、`quality.status = passed`、元数据一致性和现有完整性校验；不合格包不激活且给出可理解的拒绝原因。
6. 全流程不联网、不写内容库、不读取存档；在无合格数据包、空分类、无图片和未知未来字段下均有明确页面状态。

## 2. 范围与明确不做

### 本 roadmap 覆盖

- 从 schema 2 消费契约迁移到 `database-reference.md` 定义的 schema 4 发布契约，并以真实合格数据包作为验收输入。
- 建立应用拥有的“语义目录”：把数据实体类型映射为玩家可理解的浏览分区与分类，数据包只提供可用类型、计数和类型显示名。
- 重做首页、分类浏览、实体详情、搜索结果和个人收藏/历史的内容表达，使其成为连贯的离线图鉴体验。
- 把数据包管理收纳为支持能力；它仍可见、可诊断、可安全更新，但不再充当首页的信息架构。
- 为上述迁移建立回归测试、导入校验、可访问性与真机验收证据。

### 明确不做

- 不复制示例应用或官方 Wiki 的界面、版式、素材或文案；只借鉴“按玩家主题组织信息”的产品原则。
- 不抓取 Wiki、不联网、不新增账号、云同步、遥测、广告或存档读取。
- 不把攻略、事件摘要、当前日期/天气可否完成等结论伪装成数据包事实。后续若要加入人工编写专题，须另立内容来源、审核和版权边界的 roadmap。
- 不修改 `stardew.db`，不把收藏、笔记、历史写入内容包，不提交真实游戏资源。
- 不在本 roadmap 内改变数据构建器的采集逻辑；应用侧只消费构建器发布的 schema 4 包。构建器实际产物更新是本 roadmap 的外部前置。
- 不以“兼容并继续展示不合格 schema 2 包”掩盖质量问题。旧包应被明确拒绝并提示重新生成，不允许回退到原始数据库阅读器体验。

### Granularity Gate

| 判断项 | 结论 |
|---|---|
| 为什么不是 single feature | 涉及数据包发布契约、内容领域模型、导航信息架构、多个页面、用户数据连续性和验证体系；每项可独立验收且有严格依赖。 |
| 为什么不是 brainstorm | 产品偏差、数据边界和完成信号已由现状、用户截图和 schema 4 参考明确；本次不再讨论是否 Wiki 化，而是规划怎样落地。 |
| roadmap 边界 | 仅覆盖 Android 离线图鉴的产品和消费层重构；不改构建器、不创作额外攻略内容。 |
| 最小闭环 | `wiki-semantic-home` 完成后，在合格 schema 4 包上可从中文首页入口进入一个语义分类并打开一条可读详情。 |

## 3. 模块拆分（概设）

```text
离线 Wiki 式图鉴
├── 发布数据包边界：验证 schema 4 发布资格并原子激活内容包
├── 图鉴内容领域：把只读 SQLite 记录转换为可阅读的条目、分类和关系
├── 语义目录：维护玩家视角分区、分类、排序和展示元数据
├── Wiki 浏览体验：首页、分类浏览、搜索和详情阅读
├── 个人连续性：收藏、历史、笔记与数据包更新后的失效状态
└── 验证与质量：回归、包兼容、可访问性和设备验收
```

### 发布数据包边界

- **职责**：解析 manifest/build metadata，拒绝非发布级 schema 4 包，完成 SHA、SQLite、统计、图片和元数据一致性校验，再原子切换活动包。
- **不做**：不查询图鉴业务、不定义首页分类、不迁移内容数据库。
- **承载的子 feature**：`wiki-refactor-safety-net`、`schema4-package-contract`、`package-lifecycle-continuity`。
- **触碰的现有代码 / 模块**：`core.datapackage`、`DataManifest`、`BuildMeta`、`ContentDatabase`、当前 schema 2 测试。
- **Depth 判断**：这是 deep module。调用方只接收“当前包可读/不可读”和可展示的诊断，不得自己拼接 manifest、元数据或 SQLite 校验。

### 图鉴内容领域

- **职责**：从活动只读包查询实体、搜索结果和关系，并输出已清洗的图鉴展示模型。
- **不做**：不暴露 `extra_json` 原文，不让 UI 直接解释 SQLite 行或判断游戏实时条件。
- **承载的子 feature**：`encyclopedia-content-domain`、`wiki-entry-reading`、`wiki-search-experience`、`wiki-personal-continuity`。
- **触碰的现有代码 / 模块**：`core.database.content.ContentDatabase`、现有 detail parser、搜索与关系查询。
- **Depth 判断**：这是 deep module。解析、未来字段忽略、关系解析和可读降级集中在此处，UI 不知道数据包字段形状。

### 语义目录

- **职责**：定义“农场与生产、人物与社交、物品与收集、世界与玩法”等分区及其分类；依据活动包可用类型裁剪入口并提供排序。
- **不做**：不从数据库猜测玩家意图，不把数据包中的原始类型 ID直接渲染为首页文字。
- **承载的子 feature**：`encyclopedia-content-domain`、`wiki-semantic-home`、`wiki-catalogue-browsing`。
- **触碰的现有代码 / 模块**：新增应用内版本化目录配置，替换 `HomeViewModel` 对 `typeCounts()` 的直接依赖。
- **Depth 判断**：目录是纯进程内 deep module；分区规则、可见性和后备显示名只在此集中维护，页面只消费 `WikiSection` 与 `WikiCategory`。

#### 首版固定目录基线

下表是首版唯一允许的已知类型映射；每个当前 schema 4 `entity_type` 恰好出现一次。名称、顺序、封面语义和首页可见性都是产品决定，不由数据包的记录顺序决定。封面是应用自有的语义 asset key，不得借用其他图鉴的素材或界面。

| 分区 | 分类 | `entity_type` 集合 | 首页 | 分类封面 |
|---|---|---|---|---|
| 农场与生产 | 作物 | `crop` | 是，排序 1 | `farm-crop` |
| 农场与生产 | 鱼类 | `fish` | 是，排序 2 | `farm-fish` |
| 农场与生产 | 料理 | `cooking_recipe` | 是，排序 3 | `farm-cooking` |
| 农场与生产 | 制作 | `crafting_recipe` | 是，排序 4 | `farm-crafting` |
| 农场与生产 | 商店 | `shop` | 否 | `farm-shop` |
| 农场与生产 | 农场设施与家具 | `big_craftable`, `furniture` | 否 | `farm-furniture` |
| 人物与互动 | 村民 | `villager` | 是，排序 1 | `social-villager` |
| 人物与互动 | 日程与送礼 | `npc_schedule`, `villager_gift` | 否 | `social-schedule` |
| 人物与互动 | 任务与特别订单 | `quest`, `special_order` | 是，排序 2 | `social-quest` |
| 物品与收集 | 物品、矿物与戒指 | `object`, `mineral`, `ring` | 是，排序 1 | `collection-item` |
| 物品与收集 | 工具与装备 | `tool`, `weapon`, `footwear`, `trinket` | 是，排序 2 | `collection-gear` |
| 物品与收集 | 收集包 | `bundle` | 是，排序 3 | `collection-bundle` |
| 物品与收集 | 裁缝 | `tailoring_recipe` | 否 | `collection-tailoring` |
| 世界与挑战 | 怪物与掉落 | `monster`, `drop` | 是，排序 1 | `world-monster` |
| 世界与挑战 | 成就 | `achievement` | 是，排序 2 | `world-achievement` |
| 世界与挑战 | 姜岛 | `ginger_island` | 是，排序 3 | `world-island` |

可见性规则固定如下：

- 首页按分区顺序展示 `首页=是` 且聚合计数大于 0 的分类；同一分区内按上表排序。首页不展示计数为 0 的入口。
- “全部分类”按上表分区和行顺序展示所有聚合计数大于 0 的已知分类；它是任何已知类型的唯一完整发现入口。
- manifest `content.entityTypes` 出现未在上表映射的类型时，“全部分类”在末尾增加“本次数据新增”分区，按包提供的 `displayName` 排序并可进入通用条目页；首页不展示。`displayName` 是唯一可见类型名，绝不回退显示原始 ID。
- 合格 schema 4 包要求每个类型均有可读 `displayName`；缺失/空白标签视为发布契约错误，拒绝激活。未知类型在通用页仅使用标准列和已认识的 `officialDerived` 字段。

### Wiki 浏览体验

- **职责**：提供首页、分类页、搜索结果和详情页的可读导航与状态呈现。
- **不做**：不承担数据校验、不读取原始包文件、不复制其他产品视觉。
- **承载的子 feature**：`wiki-semantic-home`、`wiki-catalogue-browsing`、`wiki-entry-reading`、`wiki-search-experience`、`package-lifecycle-continuity`。
- **触碰的现有代码 / 模块**：`HomeFeature`、`AppNavHost`、类型列表、`DetailScreen`、搜索、收藏和更多页。
- **Depth 判断**：按页面职责组织，页面只依赖领域用例和语义目录；不新增一层只转发 Navigation 或 ContentDatabase 的 adapter。

### 个人连续性

- **职责**：以稳定实体 ID 保留收藏、浏览历史和笔记；在更新后处理缺失实体、当前包版本与恢复状态。
- **不做**：不把用户数据并入 `.svdata`，不做账号同步。
- **承载的子 feature**：`wiki-personal-continuity`、`package-lifecycle-continuity`。
- **触碰的现有代码 / 模块**：现有 Room 用户库、DataStore 和数据包切换链路。
- **Depth 判断**：继续以稳定 ID 为唯一跨包键；页面不直接进行“实体是否仍存在”的数据库拼接判断。

### 验证与质量

- **职责**：把当前 schema 2、原始首页和数字条目问题固化为可回归的失败/通过场景，并在重构后用 v4 包验证。
- **不做**：不制造 Mock 成功，不因没有真实合格包而跳过发布契约验收。
- **承载的子 feature**：`wiki-refactor-safety-net`、`wiki-polish-and-device-acceptance`。
- **触碰的现有代码 / 模块**：unit test、Compose UI test、androidTest、构建/Lint 命令和真机手工路径。
- **Depth 判断**：测试穿过真实数据包边界与内容领域接口；不为生产链路增加仅用于测试的中转层。

## 4. 模块间接口契约 / 共享协议（架构层详设）

### 4.1 schema 4 发布包契约

**方向**：发布数据包边界 → 图鉴内容领域 / 数据管理 UI
**形式**：进程内 Kotlin API；底层是 schema 4 `.svdata` 文件协议。

```kotlin
data class PackageInfo(
    val packageId: String,              // database.sha256
    val gameVersion: String,
    val generatedAt: String,
    val entityTypes: List<PackageEntityType>,
)

data class PackageEntityType(
    val id: String,
    val displayName: String,
    val count: Int,
)

sealed interface PackageRejection {
    data class UnsupportedSchema(val actual: Int, val supported: Set<Int>) : PackageRejection
    data object NotPublishable : PackageRejection
    data class QualityFailed(val status: String, val dataErrors: Int) : PackageRejection
    data class MetadataMismatch(val field: String) : PackageRejection
    data class DeclaredImageMissing(val path: String) : PackageRejection
    data class InvalidEntityTypeCatalog(val reason: String) : PackageRejection
    data class InvalidManifest(val reason: String) : PackageRejection
}

data class PackageState(
    val active: PackageInfo?,
    val lastImportFailure: PackageImportFailure? = null,
)

sealed interface PackageImportFailure {
    data class Rejected(val reason: PackageRejection) : PackageImportFailure
    data class Archive(val reason: String) : PackageImportFailure
    data object HashMismatch : PackageImportFailure
    data class DatabaseValidation(val reason: String) : PackageImportFailure
}

interface DataPackageService {
    val state: StateFlow<PackageState>
    suspend fun importAndActivate(archive: Uri): AppResult<PackageInfo>
    suspend fun verifyActive(): AppResult<PackageInfo>
    suspend fun rollback(): AppResult<PackageInfo>
    suspend fun deletePreviousPackage(): AppResult<Unit>
}

internal interface ContentPackageAccess {
    suspend fun <T> withReadableContent(
        read: suspend (ContentDatabase) -> AppResult<T>,
    ): AppResult<T>
}
```

**约束**：同一个 `ContentPackageCoordinator` 实现 `DataPackageService` 与 `ContentPackageAccess` 两个角色接口；只支持 `{4}`，`publishable` 必须为 `true`，`quality.status` 必须为 `passed`，`quality.dataErrors`、`quality.translations.missing` 与 `quality.translations.invalid` 必须为 `0`。协调器独占活动 SQLite 句柄：切换前关闭旧 `ContentDatabase`，成功后才公开新状态；失败绝不覆盖已激活包，也不允许查询继续使用已关闭句柄。导入失败时 `PackageState.active` 必须保留原值，仅更新 `lastImportFailure`；成功激活新包后才替换 `active` 并清空失败。`PackageImportFailure` 统一承载发布资格、ZIP/路径、SHA、SQLite、元数据和图片错误。`PackageInfo` 不能泄露包根目录、数据库 `File` 或 manifest 原文给页面。

**Interface 设计检查**：

- **Module / interface**：页面仅依赖 `DataPackageService` 的活动状态和管理操作；图鉴内容领域仅依赖 `ContentPackageAccess` 的受控读取。两者由同一个协调器实现，页面既不得读取 manifest 文件，也不得自行打开数据库。
- **Seam placement**：seam 位于“已验证并激活的包”之后，任何内容查询与测试都从这里取得受控数据库句柄，确保不会绕过发布资格或句柄生命周期。
- **Depth / locality**：新增 manifest 字段、错误文案、一致性校验和切包顺序集中在协调器；下游只感知 `PackageInfo` 或受控读取，不感知目录与句柄。
- **Dependency strategy**：`local-substitutable`，生产和测试均使用本地目录/临时数据库；不引入网络 port。
- **Adapter**：不新增单一 pass-through adapter。现有导入器、`DataPackageManager`、`ContentDatabaseManager` 和数据库 factory 在协调器内组合；测试使用临时真实包，而非伪造“校验成功”。

#### schema 4 字段级验收矩阵

| 验证项 | 比较对象 / 规则 | 拒绝原因 | 验证样本 |
|---|---|---|---|
| 发布资格 | manifest `publishable=true` | `NotPublishable` | 构建器 fixture 包 |
| schema 与语言 | manifest、`build_meta`、`artifact_metadata` 的 schema=4、language/locale=`zh-CN` 一致 | `UnsupportedSchema` 或 `MetadataMismatch(field)` | 合格包、schema 2 包、篡改元数据包 |
| 版本与来源 | `generatedAt`、`sourceHash`、`gameVersion` 在 manifest 与 `artifact_metadata` 一致 | `MetadataMismatch(field)` | 篡改元数据包 |
| 内容统计 | `entities`、基础计数、`extraCounts`、`entityTypes` 在 manifest 与 `artifact_metadata` 一致；数据库实体总数与内容总数一致 | `MetadataMismatch(field)` 或 `InvalidEntityTypeCatalog` | 合格包、计数篡改包 |
| 质量 | `status=passed`、`dataErrors=0`、翻译 `missing=0`、`invalid=0` | `QualityFailed` | 质量失败包 |
| 类型目录 | 每个 `entityTypes` 元素的 id 非空、displayName 非空、count 大于 0；总和与数据库类型统计一致 | `InvalidEntityTypeCatalog` | 合格包、类型目录篡改包 |
| 数据库与图片 | 路径在包内、SHA-256、`quick_check`、搜索/实体计数通过；非空 `image_path` 必须存在且在包根内 | `PackageImportFailure.Archive`、`HashMismatch`、`DatabaseValidation` 或 `DeclaredImageMissing(path)` | 缺图/越界路径包 |

`image_path=null` 表示官方没有可物化图片，允许 `WikiEntry` 选择产品级占位；`image_path` 非空但文件缺失是数据包损坏，必须拒绝，而不是只计数后继续激活。

#### 真实包验收样本策略

真实成功样本不提交到 Git，也不能拿 `publishable=false` fixture 冒充。`schema4-package-contract` 必须约定以下三类样本与入口：

1. **合格 v4 包**：由数据构建器用用户拥有的游戏资源生成；保存在工作区外或忽略路径，由显式的 `STARDEW_SVDATA` 输入提供。新增 `verifyRealV4Package` 验收入口在缺少该输入时明确失败；发布/真机验收必须运行它，不能静默跳过。
2. **不可发布 fixture 包**：由构建器的 fixture 输出提供，`publishable=false`；进入自动化测试并断言 `NotPublishable`。
3. **质量或元数据失败包**：由构建器测试输出或受控篡改副本产生；进入自动化测试并断言上表中的准确拒绝原因。

普通单元测试只覆盖解析、拒绝分类和目录计算；“成功导入真实游戏数据”的证据以第 1 类包的显式验收结果为准。

### 4.2 语义目录与图鉴查询契约

**方向**：语义目录 + 图鉴内容领域 → Wiki 浏览体验
**形式**：进程内 Kotlin API。

```kotlin
data class WikiSection(
    val id: String,
    val title: String,
    val categories: List<WikiCategory>,
)

data class WikiCategory(
    val id: String,
    val title: String,
    val entityTypes: Set<String>,
    val entryCount: Int,
    val cover: CategoryCover,           // 应用自有 asset key，不含包路径
)

data class CatalogueQuery(
    val categoryId: String,
    val keyword: String? = null,
    val filter: CatalogueFilter = CatalogueFilter.None,
    val displayMode: CatalogueDisplayMode,
)

interface WikiCatalogue {
    suspend fun sections(): AppResult<List<WikiSection>>
    suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage>
    suspend fun entry(id: String): AppResult<WikiEntry>
    suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>>
}
```

**约束**：`WikiCategory.title` 是玩家可读文本；`entityTypes` 只作内部筛选，UI 不展示其值。目录以应用内版本化配置决定分区、顺序和封面，包的 `content.entityTypes(id, displayName, count)` 用于验证类型存在、提供未映射类型的诊断和确定可见分类。任意有数据的类型必须可从“全部分类”到达；配置未知类型不得在首页泄露原始 ID，应显示包提供的 `displayName` 或在数据管理页提示“此版本新增内容，等待应用适配”。

**Interface 设计检查**：

- **Module / interface**：页面只使用 `WikiCatalogue` 输出的展示模型，不能调用 `ContentDatabase.typeCounts()` 或读取 `extra_json`。
- **Seam placement**：seam 位于内容领域已经应用包状态、目录映射和条目规范化之后；Compose UI 与测试都走这个入口。
- **Depth / locality**：原始字段变化、关系数据缺失、图片缺失和类型映射集中在内容领域/目录内，避免每个页面各自判断。
- **Dependency strategy**：`in-process`。不引入 repository 的生产/测试双 adapter；用真实临时 SQLite 包及纯目录配置测试行为。
- **Adapter**：无。`WikiCatalogue` 本身是领域边界，不得退化为对 `ContentDatabase` 的逐方法转发。

### 4.3 条目与关系阅读契约

**方向**：图鉴内容领域 → 详情、搜索、收藏/历史 UI
**形式**：进程内 Kotlin 模型。

```kotlin
data class WikiEntry(
    val id: String,
    val title: String,
    val englishTitle: String?,
    val categoryLabel: String,
    val image: EntryImage,
    val summary: String?,
    val sections: List<EntrySection>,
    val relations: List<EntryRelation>,
)

data class EntryRelation(
    val label: String,
    val target: RelationTarget,
)

sealed interface RelationTarget {
    data class Entry(val id: String) : RelationTarget
    data class ReadableText(val value: String) : RelationTarget
    data class Unavailable(val message: String) : RelationTarget
}
```

**约束**：`title` 必须来自合格数据的可读中文名；不把 `translation_status=missing|invalid` 的内容渲染为实体标题。`EntrySection` 仅包含已认识、已验证可读的 `officialDerived` 字段；未知字段忽略。关系能唯一解析为实体时返回 `RelationTarget.Entry`；有官方可读文字但不可跳转时返回 `ReadableText`；只有关联存在而没有可读目标时返回 `Unavailable("关联内容暂未收录")`。三种状态均不得显示内部 ID 或条件表达式；技术 ID 仅可在数据管理诊断层查看。图片不存在时使用本地语义化占位，不能显示“图”字占位或尝试现场裁剪游戏资源。

**Interface 设计检查**：

- **Module / interface**：`WikiEntry` 是详情、搜索命中和收藏恢复的共同阅读模型。
- **Seam placement**：类型专属解析和关系解析都在内容领域完成，详情页面只渲染 section/relations。
- **Depth / locality**：新增类型 renderer 或字段版本差异只修改内容领域，不扩散到路由和 Compose 页面。
- **Dependency strategy**：`in-process`；解析使用真实 schema 4 实体测试，不以字符串分割或“未知即成功”替代结构化读取。
- **Adapter**：无，避免为单个 renderer 额外加透传层。

### 4.4 路由、状态和个人数据约束

**方向**：Wiki 浏览体验 ↔ 个人连续性
**形式**：Navigation Compose 路由与现有本地用户库。

```text
Home
Catalogue/{categoryId}
Search
Entry/{encodedStableEntityId}
Favorites
History
More
DataManagement
```

**约束**：路由只携带 `categoryId` 或 URI 编码后的稳定 `entities.id`；不得携带原始 JSON、数据库路径或类型索引。收藏、历史和笔记只保存稳定 ID；打开时由 `WikiCatalogue.entry(id)` 解析，失败时返回“当前数据包已不存在”的可清理状态。数据管理只能经 `More` 进入，导入成功后回到重新计算过的 Home。

**Interface 设计检查**：路由是本地 UI seam，不增加 router adapter。用户库已是独立持久化边界；内容包切换不改变其模式。

### 4.5 方案深度 pre-pass

本次不采用“只给 Home 加图标、继续把 raw type 列出来”的简化方案：它没有改变信息架构，无法解决截图中的数字成就、`achievement` 标签和数据库阅读器定位。也不采用“先用假 Wiki 文章填满首页”的方案：攻略内容是本场景的核心事实，构建器不提供时不能用占位文本伪装完成。最窄端到端路径是：**真实合格包 → 发布校验 → 语义首页 → 分类 → 可读实体详情**；它覆盖用户最直接的价值且没有替掉核心逻辑。

## 5. 子 feature 清单

1. **wiki-refactor-safety-net** — 为现有导入、首页、列表、详情、搜索和个人数据行为建立可重复的特征测试与已知失败样本。
   - 所属模块：验证与质量
   - 依赖：无
   - 状态：planned
   - 对应 feature：未启动
   - 备注：保存重构前行为边界；不得把现有错误 UI 当成目标验收。

2. **schema4-package-contract** — 让数据包导入链仅接受并字段级一致性校验发布级 schema 4 包，且保留旧活动包的原子回滚语义与同步契约文档。
   - 所属模块：发布数据包边界
   - 依赖：`wiki-refactor-safety-net`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：按第 4.1 节矩阵和三类样本验收；失败导入必须在状态中保留旧活动包；需要构建器提供真实 `publishable=true`、`quality.status=passed` 的 schema 4 包；旧 schema 2 包应给出明确拒绝提示，并更新过期需求/README。

3. **encyclopedia-content-domain** — 建立语义目录和 `WikiCatalogue`，将合格包中的类型、摘要、关系和图片转换为统一的图鉴展示模型。
   - 所属模块：图鉴内容领域、语义目录
   - 依赖：`schema4-package-contract`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：目录配置必须覆盖所有可展示类型的可发现路径，并通过包的 `entityTypes` 做差异诊断。

4. **wiki-entry-reading** — 重做详情阅读层，按类型呈现可读核心信息、关系卡片、图片缺失状态与未知字段的安全忽略。
   - 所属模块：图鉴内容领域、Wiki 浏览体验
   - 依赖：`encyclopedia-content-domain`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：使用 `WikiEntry`/`EntryRelation` 契约；不显示纯数字标题、内部 ID、`extra_json` 或未解析分隔串。

5. **wiki-semantic-home** — 用首版固定目录重做首页，并完成“首页分类 → 固定列表 → 详情”的最窄真实数据闭环。
   - 所属模块：Wiki 浏览体验
   - 依赖：`encyclopedia-content-domain`、`wiki-entry-reading`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：首页不再直接显示 `typeCounts()` 的原始类型；本条只交付固定呈现的最小分类列表，不含网格/列表切换、分类内搜索或筛选；最近浏览、无包和空分类需有独立状态。

6. **wiki-catalogue-browsing** — 在最小分类页上增量提供玩家可读卡片/列表切换、分类内搜索和基于已支持字段的筛选。
   - 所属模块：Wiki 浏览体验
   - 依赖：`wiki-semantic-home`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：筛选项只能来自明确支持的结构化字段；不得把数据库列或 JSON key 直接当用户筛选器。

7. **wiki-search-experience** — 让全局搜索以统一图鉴阅读模型展示命中结果、分类限定和无结果状态。
   - 所属模块：图鉴内容领域、Wiki 浏览体验
   - 依赖：`wiki-catalogue-browsing`、`wiki-entry-reading`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：搜索命中不能退化成原始类型、技术 ID 或数据库字段列表。

8. **package-lifecycle-continuity** — 将数据包版本、导入、更新、拒绝原因和内容更新提示收纳到“更多/数据管理”，并覆盖更新后的首页刷新。
   - 所属模块：发布数据包边界、Wiki 浏览体验
   - 依赖：`schema4-package-contract`、`wiki-semantic-home`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：包管理不再作为图鉴首页的核心导航；导入错误需可读但保留技术详情入口。

9. **wiki-personal-continuity** — 让收藏、历史和笔记消费统一图鉴阅读模型，并验证跨数据包的稳定 ID 连续性。
   - 所属模块：图鉴内容领域、个人连续性、Wiki 浏览体验
   - 依赖：`wiki-entry-reading`、`package-lifecycle-continuity`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：缺失实体必须可见且可清理，不能静默丢弃用户数据。

10. **wiki-polish-and-device-acceptance** — 完成离线、空态、错误态、可访问性、性能回归和真实设备上的端到端验收，并归并产品与开发文档。
   - 所属模块：验证与质量
   - 依赖：`wiki-search-experience`、`wiki-personal-continuity`
   - 状态：planned
   - 对应 feature：未启动
   - 备注：这是一条收口型 feature；不在缺少真实 schema 4 合格包时宣称验收通过。

**最小闭环**：第 5 条 `wiki-semantic-home` 完成且其依赖完成后，用户可导入合格 schema 4 包，在中文语义首页选择实际存在的分类，看到固定的可读条目列表并打开可读详情；该路径不经过数据管理页，也不显示原始类型名。

### Goal Coverage Matrix

| Goal / completion signal | Covered by item(s) | Verification entry | Evidence type | Core? |
|---|---|---|---|---|
| 合格 schema 4 包可导入，质量失败/旧 schema 不能激活 | `schema4-package-contract` | 自动化拒绝路径 + 显式 `verifyRealV4Package` 的工作区外真实包验收 | 测试结果、校验报告、真机记录 | yes |
| 首页以中文语义分类而非 raw type 组织 | `encyclopedia-content-domain`, `wiki-semantic-home` | Compose UI 路径：Home → Catalogue | UI test、截图 | yes |
| 条目名称、图片、关系均为可读呈现 | `wiki-entry-reading` | 代表性作物、村民、成就等实体详情 | unit/Compose test、截图 | yes |
| 所有有数据的类型仍可被发现 | `encyclopedia-content-domain`, `wiki-catalogue-browsing` | 包 `entityTypes` 与目录输出的覆盖核验 | unit test、差异报告 | yes |
| 搜索以可读图鉴模型展示 | `wiki-search-experience` | 全局搜索、分类限定、无结果状态 | unit/Compose test、截图 | yes |
| 个人数据跨包连续 | `wiki-personal-continuity`, `package-lifecycle-continuity` | 更新前收藏/历史/笔记，更新后重新打开 | Room/集成测试 | yes |
| 数据管理退居支持位置且错误可理解 | `package-lifecycle-continuity` | More → DataManagement、失败导入和成功更新 | Compose test、手工验收 | no |
| 离线、可访问且可安装 | `wiki-polish-and-device-acceptance` | `assembleDebug`、`testDebugUnitTest`、`lintDebug`、可用设备 `connectedDebugAndroidTest` | 命令输出、真机记录 | yes |

## 6. 排期思路

顺序由风险和依赖决定，不替用户给功能价值排序。先固定当前行为的安全网，再验证最弱前置——schema 4 的发布包契约；没有它，任何漂亮首页都只能继续读取旧的失败数据。随后一次建立内容领域与语义目录，使首页、列表、详情和搜索共享同一条内容边界，而不是各页面各写一套映射。

最小闭环选择第 5 条，是因为它首次让用户体验到路线已改变：从语义首页进入真实内容并读到已重构详情。第 6 至 9 条分别扩展浏览深度、搜索、数据包生命周期和个人连续性；最后一条专门收口，防止仅在开发机展示正常。

### Top 3 风险与缓解

1. **没有真实发布级 schema 4 包，应用只能围绕旧 schema 2 继续验证。** 缓解：`schema4-package-contract` 以构建器产出的合格包为前置，失败则停止后续真实验收，不伪造成功。
2. **语义目录漏掉新类型或把原始类型重新泄露给用户。** 缓解：目录与 manifest `content.entityTypes` 做覆盖比对；未知类型进入明确的更新提示/全部分类可发现路径，不直接渲染 ID。
3. **重构详情时丢失关系、收藏或更新后的历史。** 缓解：先建特征测试；以稳定 ID 和 `WikiEntry` 为共享边界；最终在两份不同内容包的更新场景中验收。

### 非显然依赖与关键假设

- 外部前置：`E:\github\stardew-offline-data-builder` 必须交付实际 schema 4、发布资格通过的 `.svdata`；当前仓库中的 schema 2 资产不能作为成功样本。
- 假设：应用内可维护一份版本化语义目录，作为产品信息架构；数据包不承担首页分组和视觉设计职责。
- 假设：schema 4 的 `content.entityTypes.displayName` 是类型的中文事实标签；未映射类型可以在诊断/全部分类中以该标签出现，但不能把 ID 放在普通首页。
- 假设：首期不创作新的攻略文章；“Wiki 式”指阅读与关联的产品结构，不能被误解为已经拥有完整攻略知识库。

### 基线与验证入口

- 构建：`./gradlew.bat assembleDebug`
- 单元测试：`./gradlew.bat testDebugUnitTest`
- 静态检查：`./gradlew.bat lintDebug`
- 真机/AVD：`./gradlew.bat connectedDebugAndroidTest`
- 人工主路径：无包导入页 → schema 4 合格包 → 首页分类 → 列表 → 详情 → 收藏/历史 → 数据包更新。

## 7. 观察项

- `.codestable/requirements/stardew-offline-android.md` 仍声明 `database_schema_version: 2`，README 也声明“数据查询工具”。本 roadmap 不修改现状文档；在 `schema4-package-contract` 验收时必须以实际代码/包契约为事实回写需求和用户文档。
- 现有 `EntityImage` 的“图”字占位、`HomeFeature` 的原始 `typeCounts()` 和 `DetailScreen` 的原始类型标签是产品偏差的可定位证据，后续 feature 不得仅换色或藏起来。
- 构建器 schema 4 不提供攻略结论、事件摘要和条件求值。若产品要新增“新手指南、季节规划、专题文章”等页面，应在本 roadmap 完成后先建立独立的内容治理方案，不能在图鉴 feature 中顺手加假内容。
- 当数据包新增实体类型时，必须先通过目录覆盖测试再决定首页入口；不要因为“兼容未知字段”而无提示地隐藏整类内容。
- 稳定接口在 feature acceptance 验证后，才考虑用 `cs-domain` 将发布包/图鉴展示模型的长期决策固化为 ADR；在此之前本 roadmap 是唯一前瞻性约束。
