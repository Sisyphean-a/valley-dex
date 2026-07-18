---
doc_type: feature-design
feature: 2026-07-18-wiki-refactor-safety-net
requirement: ""
roadmap: wiki-product-refactor
roadmap_item: wiki-refactor-safety-net
execution_lane: goal
status: approved
summary: 为现有数据包、内容查询、导航和用户数据建立可重复的特征测试安全网
tags: [android, test, datapackage, regression]
---

# wiki-refactor-safety-net 设计

## 0. 术语约定

- **合成测试包**：测试代码在设备临时目录构造的最小 schema 2 `.svdata`，只含虚构条目和图片；不含游戏资源，也不代表后续 schema 4 成功样本。
- **特征测试**：通过真实导入器、SQLite、Room 或 Compose 路径观察当前可见结果的测试；不把内部实现细节当断言目标。
- **稳定实体 ID**：`entity_type:game_id` 形式的跨页面、收藏和历史键；测试只依赖其连续性，不依赖原始类型名成为产品文案。
- **产品基线**：用户能导入、浏览、搜索、打开详情并保存个人数据的能力边界；当前 raw type、数字标题和“图”占位均不属于需要保留的基线。
- **包 A/B**：两个有效的合成 schema 2 包。B 保留一部分 A 的稳定 ID 并移除另一部分，用于分别验证跨包连续性与当前已有的缺失收藏/历史呈现。

术语检索未发现同名测试基础设施或既有约定；后续条目中的 `WikiCatalogue`、`WikiEntry` 与本条的测试术语不冲突。

## 1. 决策与约束

### 需求摘要

在改变数据包契约和 Wiki 浏览表达前，为现有导入、首页、类型列表、详情、搜索、收藏、历史与笔记建立可重复证据。成功标准是：每条核心路径可在无网络、无真实游戏资源的 Android 测试环境中复现；已知非法包有明确失败断言；测试失败能够定位到路径而非被跳过。

明确不做：

- 不把 schema 2 合成包当作 schema 4 发布包，也不宣称其验证了真实游戏数据。
- 不保留当前原始类型、数字标题、内部 ID、“图”占位等将由后续产品条目替换的视觉内容。
- 不向生产代码加入“测试时总是成功”的分支、后门或网络 mock。
- 不修改数据构建器、用户数据模式、导航契约或用户可见行为；为测试增加的 Gradle 配置和不可见语义标记不改变页面文案或交互。

### 关键决策

1. **以合成测试包替代被忽略的本地默认包。** 它可公开提交、可在 CI 重建，覆盖的是应用的导入与只读查询边界；真实 schema 4 包仍由 `schema4-package-contract` 的显式验收入口负责。
2. **核心链路优先走真实本地组件。** 导入测试使用现有解压、校验、安装与激活链；内容测试使用真实 SQLite；个人数据测试使用真实 Room。只替代 Android 文件选择等外部边界，不能伪造校验成功或查询结果。
3. **UI 断言锁定语义和交互，不锁死将被重构的文案。** 断言无包、内容入口、列表点击、详情、搜索结果、缺失实体与清理操作是否可达；不以原始 `entity_type`、`extra_json`、技术 ID 或当前占位文本为通过条件。
4. **测试夹具独立于生产默认资源。** 测试不会读取 `app/src/main/assets/default-data/`，因此本地未放真实包时，测试应仍可运行；旧 `RealDataPackageValidationTest` 必须改为显式本地输入验收或移出自动测试集，不能在 CI 隐式依赖忽略文件。
5. **schema 2 成功路径是过渡基线。** `schema4-package-contract` 必须把有效 schema 2 成功样本替换为合格 schema 4 样本，并把 schema 2 转为 `UnsupportedSchema` 拒绝 case；本条不将 schema 2 固化为长期消费契约。
6. **用显式生产对象图装配 Android 场景。** 不以 Hilt runner 或 `MainActivity` 的 Bootstrap 时序作为测试前置；测试在 `setContent` 前以现有构造函数创建真实导入器、校验器、内容库 manager、repository 和内存 Room 用户库，再把真实 ViewModel 显式传给各 Route。它不替换生产实现、不引入 test adapter，且可在每个 case 独立关闭和清理。
7. **为当前首页入口增加不可见测试选择器。** 仅在首页可点击分类行增加 `home-category:<type>` test tag，不改变用户看到的 raw type 文案、导航参数或产品契约；不向列表、详情或搜索增加 tag。`wiki-semantic-home` 交付语义目录时必须删除该过渡选择器与测试。

### 方案深度 pre-pass

候选方案是“只测纯函数”与“构建最小真实数据包并跑端到端路径”。本条选择后者：导入、SQLite 读取、稳定 ID 和用户数据是后续重构的核心边界，纯 mock 会遗漏包结构、激活和查询的真实失败模式。合成包仅替代外部受版权约束的游戏资源；转正条件是下一条取得发布级 schema 4 包后，额外运行真实包验收，不以本条夹具替代。

### 风险、依赖与证据

- 风险 1：测试无意断言旧 UI 缺陷。缓解：每个 UI case 标明“保留的能力”并禁止断言 raw type、数字标题、内部 ID 和占位文案。
- 风险 2：夹具与真实导入链脱节。缓解：夹具必须被压缩成 `.svdata` 并经现有导入器、校验器和数据库 factory 使用；不直接塞入已激活目录作为导入成功证据。
- 风险 3：设备测试缺设备或 Gradle 基线耗时。缓解：单元测试与设备测试分开报告；设备不可用时明确阻塞，不跳过核心 Android 证据。
- 风险 4：真实 App UI 的偏好、Room 或异步搜索状态残留。缓解：Android 场景装配器在每个 case 前后初始化和清理应用文件、偏好及用户库；Compose 仅等待语义结果，不使用固定 sleep。
- 非显然依赖：Android SDK/AVD、Java 17；真实 schema 4 包不在本条范围。
- 关键假设：当前 schema 2 最小数据库可由测试在本地构建，且现有 SQLite 查询、Room 和 Compose 可在 instrumentation 环境中被真实调用。

复杂度走 Android 测试场景默认档位，无对外 SDK、并发协议或数据迁移偏离。

## 2. 名词与编排

### 2.1 名词层

**现状：** `DataPackageManager`、`ContentRepository` 和 `UserDataRepository` 已分别承载数据包、内容和个人数据；测试仅有少量底层单测、单个 `EntityListItem` Compose 测试，以及错误依赖本地默认包的 `RealDataPackageValidationTest`。

**变化：** 新增仅位于测试源码集的三类测试资产：

- 合成测试包工厂：生成有效包及 schema、manifest、哈希、数据库等受控失败变体。有效包必须含 `manifest.json`、`stardew.db` 和一份可解析的合成图片；数据库至少含 `build_meta`、`entities`、`entity_search`、`entity_aliases` 及查询所需索引。A 包至少有 object、crop、fish、villager 四类的可读条目、一个别名和一条关系；B 包保留至少一个 A 的 ID 并移除一个被收藏/浏览的 ID。
- Android 场景装配器：用测试应用 context 与现有构造函数创建真实 manager、内容库、repository 和内存 Room 用户库；它在 `createComposeRule().setContent` 前准备活动包，再向各 Route 传入真实 ViewModel 与导航回调。
- 特征场景矩阵：按“包生命周期、内容浏览、搜索、个人连续性”给出稳定的 case 名、输入和能力级断言。

夹具采用**运行时生成并压缩**，不提交二进制游戏资源。有效 A/B 包的精确构造契约为：

- manifest 写入 `format`、`schemaVersion`、`builderVersion`、`gameVersion`、`language`、`generatedAt`、database file/SHA-256 和 `content.entities`；SHA 必须由生成后的数据库计算。
- `build_meta` 必须有 `schema_version`、`builder_version`、`locale`、`generated_at`、`entity_count`、`game_version`、`source_hash` 七个 TEXT 键，值与 manifest 和实体数一致。
- `entities` 必须提供当前详情读取的 `id`、`entity_type`、`game_id`、`internal_name`、中英文名、双语描述、`category`、`translation_status`、`image_path`、非空 JSON 对象 `extra_json`、`source_file` 与 `created_at`；并创建 type/name/game ID 索引。
- `entity_aliases` 至少含 `id`、`entity_id`、`alias`、`alias_type`；`entity_search` 使用 FTS4，含 `entity_id`、`name_zh`、`name_en`、`pinyin`、`pinyin_initials`、`aliases`、`keywords`。每个实体恰有一条搜索行，使搜索计数与实体数的比例为 1。
- A 包固定为 `object:1`（萝卜 / Turnip / `luo bo` / `lb` / 别名“根菜”）、`crop:1`（萝卜种子 / Turnip Seeds / `luo bo zhong zi` / `lbzz`）、`fish:1`（测试鱼 / Test Fish / `ce shi yu` / `csy`）和 `villager:Alice`（测试村民 / Alice / `ce shi cun min` / `cscm`）。每行的 FTS 文档复制这些值；fish 的 `keywords` 额外写“水域专用词”，它只能通过 FTS 命中。A 含一张合成 1×1 WebP，crop 的 `officialDerived.harvestItemId` 指向 `object:1`；B 保留 object/crop/fish，移除被收藏和浏览的 `villager:Alice`。

受控失败样本及预期分类为：schema 版本不受支持 → `UnsupportedSchema`；格式不符或 JSON/必填字段错误 → `InvalidPackageFormat`/`InvalidManifest`；manifest 必须固定指向 `stardew.db`，其不存在 → `InvalidManifest`；数据库哈希不一致 → `HashMismatch`；缺失 `build_meta`、缺失 `entity_search` 表或计数不一致 → `DatabaseCorrupted`。不为“任意损坏 SQLite”承诺稳定的导入错误分类：它只作为 `ContentDatabaseFactory` 的独立失败边界观察。若 `entity_search` 是普通表而非 FTS4，则保留相同七列和四行数据，安装可通过但以“水域专用词”调用 `SearchRepository` 必须得到 `DatabaseQueryFailed`。每个失败样本只破坏一个验证面。

生产 API、Room schema、路由参数和数据包格式均不新增或修改。本条不新增跨模块接口、adapter 或 seam；测试直接观察现有的生产边界。唯一生产源码改动是在首页分类行加不可见 test tag，供当前测试驱动入口；它不创建新调用链、不显示给用户，后续语义首页重构时按新目录 selector 替换。

### 2.2 编排层

**现状：** 启动从 `BootstrapViewModel` 打开或导入包，`AppNavHost` 在首页、搜索、类型、详情和个人页面间传递实体 ID；各页面读取 `ContentRepository` 和 `UserDataRepository`。这些路径尚没有共同的回归编排。

**变化：** 测试执行为简单线性流程，无需流程图：

1. 构造合成 `.svdata` 或一个指定的损坏变体。
2. 经真实导入/校验/激活链验证成功或准确失败，并在失败后检查旧活动包仍可读取。
3. 用同一活动包查询类型、列表、详情、别名和搜索；通过 UI 路径验证可到达的内容能力。
4. 在真实用户库写入收藏、历史、笔记和搜索历史，先从 A 成功切至 B，再验证 `rollback()` 回到 A；最后观察保留 ID、当前已有的缺失收藏/历史状态、笔记持久化和搜索历史的独立连续性。

Android 场景装配器使用 `InstrumentationRegistry.context`（androidTest APK context），并在无包 case 前断言其 assets 不含 `default-data/*.svdata`，不使用 target APK context。顺序固定为：创建独立内存 `UserDatabase` 与真实 `AppPreferencesRepository`/内容对象图 → 清空偏好并删除上次 `content` 目录 → 经真实 `DataPackageManager` 导入 A/B → 创建每 case 唯一 `ViewModelStoreOwner` 和自定义 `ViewModelProvider.Factory`；**所有**传给 Home、Search、Favorites、History、TypeList、Detail、Bootstrap Route 的 ViewModel 都由该 provider 取得，factory 为 TypeList/Detail 注入 `SavedStateHandle(mapOf("type" to "crop"))`/`SavedStateHandle(mapOf("id" to "crop:1"))` → 用 `createComposeRule().setContent` 将目标 Route 包在 `CompositionLocalProvider(LocalAppPreferences)` 与 `StardewOfflineTheme` 内，捕获导航回调 → 以语义节点/回调状态等待结果 → 清空 composition 并 `waitForIdle` → `owner.viewModelStore.clear()` 取消所有收集协程 → 关闭 `ContentDatabaseManager`、关闭内存 `UserDatabase`、清空偏好并删除 `content` 目录。测试不启动 `MainActivity`，因此不受 Bootstrap 的导入页时序影响；路由能力以各 Route 的真实回调参数验证。

无包场景单独创建真实 `BootstrapViewModel` 并挂载 `BootstrapRoute(onReady)`：断言状态变为 `NeedDataPackage` 且“选择数据包”按钮可达。系统文件选择器是 Android 外部边界，本条不点击、不伪造导入成功；导入成功仍由前述真实 manager 场景覆盖。

Compose 搜索保留现有 150 ms 防抖，但测试以结果节点出现、加载状态收束或导航回调为等待条件，不以固定等待时间作为通过条件。用户库始终为内存 Room，禁止删除已打开的 `user.db` 文件。

流程级约束：所有临时包、数据库和偏好必须在每个 case 后清理；失败 case 必须断言 `AppError` 分类与活动包连续性；涉及 UI 的断言仅针对语义节点、回调和导航结果。

### 2.3 挂载点清单

- `app/src/test`：新增 JVM 单元测试和共享断言，覆盖纯格式化/契约的基础边界。
- `app/src/androidTest`：新增合成包、显式生产对象图场景装配器、真实 Android 组件和 Compose/导航特征场景。
- Gradle：保留现有 AndroidX/Compose 测试 runner，并以 `testDebugUnitTest` 与 `connectedDebugAndroidTest` 作为安全网执行入口。
- 首页分类行：新增不可见 test tag，作为当前首页→类型列表的稳定测试入口。

本 feature 不引入新的生产运行时能力；test tag 只增加测试语义树可观测性。

### 2.4 推进策略

1. **测试基线与夹具骨架**：建立按 §2.1 DDL 契约生成的 A/B 合成包、受控失败样本、显式生产对象图、内存 Room、临时目录清理和 Compose 场景装配。
   - 退出信号：测试在 `setContent` 前完成真实包导入；A/B 包可被现有校验器读取，且每个 case 关闭内容库、关闭内存 Room、清空偏好并删除内容目录。
2. **包生命周期证据**：覆盖首次导入、非法 schema/manifest/哈希、失败导入保留当前活动包，以及有效 A→B→rollback(A) 的读取。
   - 退出信号：每个失败样本断言准确错误分类和旧活动包仍可查询；回滚后 A 的包 ID 和实体查询恢复。
3. **内容与搜索证据**：覆盖首页类型来源、类型列表、稳定 ID 详情、别名/前缀搜索和不存在条目。
   - 退出信号：每条查询路径在合成包上有正常和一个边界 case。
4. **Compose 与导航证据**：仅为首页分类入口增加 `home-category:<type>` test tag，覆盖 Bootstrap 无包状态、内容入口、列表到详情、搜索命中点击及不锁死旧展示文案的语义断言。
   - 退出信号：每个 Route 都使用已定义的 SavedStateHandle 和 case 独立 ViewModelStore；设备测试经首页 tag 和用户可读条目节点到达目标状态，验证导航回调；无包场景只断言 Bootstrap 的状态与选择按钮可达，不驱动系统文件选择器。
5. **个人连续性证据**：覆盖收藏、浏览历史、笔记和搜索历史；收藏/历史覆盖当前已有的缺失与删除能力，笔记覆盖稳定 ID 的持久化、读取和删除，搜索历史覆盖切包后不受实体可用性影响。
   - 退出信号：保留 ID 的记录跨 A/B 可读；缺失收藏/历史仍可见并可删除；笔记和搜索历史按各自现有接口读写正确。
6. **基线治理**：移除自动化对忽略本地包的隐式依赖，记录设备测试条件与当前 Gradle 基线结果。
   - 退出信号：全套命令不会因缺少本地真实 `.svdata` 而跳过或误报成功。

### 2.5 结构健康度与微重构

#### 评估

- 文件级：仅在 `HomeFeature.kt` 的分类行增加一个 test tag；该文件 79 行、职责单一，改动密度为 1。`SearchFeature.kt`、`DataPackageManager.kt` 均在 140 行以内，测试仍经现有公开/路由边界观察行为。
- 目录级：`app/src/test` 现有 7 个测试文件、`app/src/androidTest` 现有 2 个测试文件；新增测试辅助代码按 `testsupport` 与 feature 场景分组，不向生产目录堆叠文件。
- compound 检索无已有目录、命名或 Compose 约定。

#### 结论：不做

不在本条拆分或移动生产文件。允许实施 §2.1 已定义的测试源码对象图与首页 test tag；若还需改变生产接口，必须先证明无法经真实现有边界观察，再回到 design 补充范围，不用测试后门替代。

## 3. 验收契约

### 关键场景清单

1. 有效合成包 A 导入 → 活动包、数据库元数据与至少一个稳定实体 ID 可读取；导入 B 后执行回滚 → A 的包 ID 和实体查询恢复。
2. schema、manifest、哈希或数据库无效的包导入 → 返回对应失败；已有活动包和查询仍可用。
3. 活动包有多个类型和条目 → 首页内容入口、类型列表和详情路径均可达；详情加载会记录浏览历史。
4. 中文/英文/别名/拼音首字母/FTS 专用词查询与空输入、不存在 ID → 返回预期结果或可理解的空/缺失状态，不崩溃；普通 `entity_search` 表的查询失败被准确归类。
5. 收藏、历史、笔记和搜索历史写入后切换包 → 保留 ID 的记录连续；缺失收藏/历史保持当前的可见可删除状态；笔记仍可按存在 ID 读取和删除；搜索历史与实体存在性无关并保留。
6. 无包启动、搜索命中点击、列表点击 → UI 保留对应的导入、导航与详情能力；断言不包含任何将淘汰的 raw type/内部 ID/占位文案要求。

### 明确不做的反向核对

- 自动化测试中不读取 `app/src/main/assets/default-data/*.svdata`，也不引用真实游戏资源路径。
- 测试生产路径中不新增网络请求、测试成功开关或 schema 4 成功断言。
- 断言中不把 `extra_json`、原始类型、纯数字标题或“图”占位当作产品验收条件。

### Acceptance Coverage Matrix

| Scenario | Covered By Step | Evidence Type | Command / Action | Core? |
|---|---|---|---|---|
| 有效 A→B 切换与回滚 A | S1, S2 | instrumentation test | `connectedDebugAndroidTest` | yes |
| 损坏包不替换活动包 | S2 | instrumentation test | `connectedDebugAndroidTest` | yes |
| 浏览、详情、拼音与 FTS 搜索 | S3, S4 | Compose/instrumentation test | `connectedDebugAndroidTest` | yes |
| 个人数据跨包连续 | S5 | Room/instrumentation test | `connectedDebugAndroidTest` | yes |
| 纯契约与格式化回归 | S1, S3 | unit test | `testDebugUnitTest` | supporting |
| 本地真实包不再是隐式依赖 | S6 | diff review + command | `testDebugUnitTest` | yes |

### DoD Contract

| ID | 要求 | 证据 | 阻塞级别 |
|---|---|---|---|
| DOD-DESIGN-001 | 设计、清单和独立审查一致 | design review | blocking |
| DOD-IMPL-001 | 六个步骤均有可复跑证据 | checklist / 测试日志 | blocking |
| DOD-REVIEW-001 | 代码审查无未处理高优先级问题 | review report | blocking |
| DOD-QA-001 | 核心 Android 场景通过或明确记录设备阻塞 | QA report | blocking |
| DOD-ACCEPT-001 | 路线图状态回写且不把合成包误称真实 schema 4 验收 | acceptance report | blocking |

Validation Commands:

| ID | 命令 | 目的 | 核心性 | 失败处理 |
|---|---|---|---|---|
| CMD-001 | `.\gradlew.bat testDebugUnitTest` | 纯逻辑与契约回归 | core | fix-or-block |
| CMD-002 | `.\gradlew.bat connectedDebugAndroidTest` | 真实导入、SQLite、Room、Compose 路径 | core | fix-or-block |
| CMD-003 | `.\gradlew.bat lintDebug` | Android 静态检查 | supporting | document-baseline |
| CMD-004 | `.\gradlew.bat assembleDebug` | 编译 Android 安装包 | supporting | fix-or-block |

Required Artifacts: 测试代码、合成夹具说明、命令日志、design review、代码 review、QA 和 acceptance 报告。清洁度规则：禁止临时日志、TODO/FIXME、注释掉的测试或无用 import。

## 4. 与项目级架构文档的关系

本条只新增测试资产，不改变长期生产名词、流程或架构决策。验收时只需记录“合成测试包不能替代真实 schema 4 包”的持续约束；若后续测试夹具成为跨 feature 的稳定协议，再由后续 feature 决定是否沉淀到项目规范。
