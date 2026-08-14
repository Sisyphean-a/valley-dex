---
scope: package:app
code-paths:
  - app/src/main/java/com/example/stardewoffline
  - app/src/test
  - app/src/androidTest
---

# `:app` 应用包

## 职责

`:app` 是唯一生产业务包，负责离线图鉴的启动、内容包消费、语义浏览、搜索、条目详情、收藏、历史、设置和数据管理。它不生成游戏数据，也不提供网络或账号能力。

## 公开边界

- `StardewOfflineRoot` 先加载活动包；没有可用包时只显示导入/错误状态，成功后才挂载 `AppNavHost`。
- `DataPackageRepository` 向启动页和数据管理页提供导入、默认包安装、验证、回滚和旧包删除；v5 manifest 还必须绑定 conformance 与 reports 内容哈希，旧 v4 只经显式 recovery pin 保留。
- `WikiCatalogue` 向首页、分类页、搜索页、详情页、收藏页和历史页提供 `WikiSection`、`WikiCategory`、`WikiEntry`、分页 `CataloguePage` 与 `WikiSearchHit`/`WikiSearchPage`；页面不消费原始 SQLite 行或 `extra_json`。事实项的 `scope_id` 与条件摘要在类型化目录边界内保留。
- `UserDataRepository` 向页面提供收藏、浏览历史和搜索历史；实体记录只保存稳定实体 ID。
- `AppPreferencesRepository` 保存活动/上一数据包、验证标识、显示偏好和列表偏好。

这些是应用内边界，不对外发布为 SDK。新增页面应优先消费现有边界，而不是绕过它们访问底层数据库。

## 结构与代码锚点

| 区域 | 责任 | 代表代码 |
|---|---|---|
| 启动与导航 | 单 Activity、Bootstrap 状态切换、主导航和 URI 编码详情路由 | `MainActivity.kt`、`navigation/StardewOfflineRoot.kt`、`navigation/AppNavHost.kt` |
| 数据包 | 安全解压、manifest 2 / schema 5 / player-facts-v1 全语义校验、暂存提交、激活、回滚和清理 | `core/datapackage/SafeZipExtractor.kt`、`DataPackageValidator.kt`、`DataPackageInstaller.kt`、`DataPackageManager.kt` |
| 内容数据库 | 只读 schema 5 SQLite、类型化事实/关系/条件/视觉、卡片/筛选、别名和 FTS 查询；批量详情读取、稳定游标和有方向反向关系均在 typed path；schema 4 `ContentDatabase` 仅供恢复校验 | `core/database/content/ContentDatabaseFactory.kt`、`Schema5ContentDatabase.kt`、`ContentDatabaseManager.kt` |
| 内容领域 | schema 5 仓储、数据库侧游标搜索/浏览、关系目标批量解析和语义图鉴模型 | `data/Schema5ContentRepository.kt`、`data/EntityRelationResolver.kt`、`data/wiki/WikiCatalogue.kt` |
| 详情表达 | 类型化 repository/DTO 向 `WikiCatalogue` 提供玩家事实和关系；schema 5 公共路径不读取 `extra_json`、`officialDerived` 或 `legacyFields` | `data/wiki/WikiCatalogue.kt`、`core/model/WikiCatalogueModels.kt`、`core/formatter/DetailFormatters.kt` |
| 个人数据 | Room 实体/DAO 与稳定 ID 软引用 | `core/database/user/UserDatabase.kt`、`UserDataDao.kt`、`data/UserDataRepository.kt` |
| 偏好与依赖注入 | DataStore 偏好、数据库和应用单例装配 | `core/datastore/AppPreferencesRepository.kt`、`di/DatabaseModule.kt`、`di/AppModule.kt` |
| 页面 | 首页、分类、搜索、详情、收藏、历史、设置、数据管理、关于 | `feature/*` |

## 当前不变量

- 产品内容库只能通过 `ContentDatabaseManager.useActiveSchema5` 的租约式 API 读取；`ContentDatabaseFactory` 的 schema 4 打开入口仅供 `DataPackageValidator.validateLegacyRecovery`，`DataPackageManager` 的活动包生命周期锁覆盖仓储查询和切包，切包前关闭旧库，因此读取不会落在目录替换的中间状态。
- 内容库与 Room 用户库分离；用户记录不写入 `stardew.db`，内容更新不会自动删除稳定 ID 记录。`user.db` 当前为 v3，历史、最近搜索和收藏的排序查询有对应复合索引，v1 用户库经显式迁移保留剩余记录，旧用户表由后续迁移清理。
- `WikiCatalogue` 从活动包的 `entityTypes` 取得可读类型名；首页的“全部分类”以四列网格展示所有有数据类型，并用只读小分组标题组织它们，不保留可点击的主题大类；类型页的细分筛选只消费已确认的摘要字段。
- 页面只消费 `player-facts-v1` 类型化 DTO；列表/搜索使用经一致性校验的卡片与 facet 投影及包绑定游标，详情批量读取规范事实、事实项条件、证据转换和关系，反向关系保留原方向，不把 raw JSON 当回退事实源。搜索命中原因来自名称、英文名、别名、分类/用途或行动摘要列。
- 图片路径必须在活动包根目录内；官方无图和代理视觉与包缺图/损坏分开。未知事实和无法解析关系不制造假实体；村民日程/礼物在 builder 聚合为 villager 的 typed fact items，支持记录不作为独立目录条目。
- 详情、收藏和历史均以稳定实体 ID 为键，搜索历史以标准化查询为键；详情路由使用 `Uri.encode`，不能把原始 ID 直接拼入路径。
- `AppNavHost` 是边到边状态栏的唯一 inset 所有者：按当前路由提供与顶部一致的状态栏底色和图标明暗；嵌套页面的 `TopAppBar` 不重复消费状态栏 inset，页面内容色固定使用主题正文色。

## 构建与测试

根工程使用 Java/Kotlin 17、Compose、Material 3、Hilt、Navigation Compose、Room、Preferences DataStore 和 kotlinx.serialization。`app/src/test` 覆盖纯逻辑与数据边界；`app/src/androidTest` 覆盖真实 Android 数据包、Room、导航和 Compose 场景。
