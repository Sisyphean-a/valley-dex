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
- `DataPackageRepository` 向启动页和数据管理页提供导入、默认包安装、验证、回滚和旧包删除。
- `WikiCatalogue` 向首页、分类页、搜索页、详情页、收藏页和历史页提供 `WikiSection`、`WikiCategory`、`WikiEntry` 与 `WikiSearchHit`；页面不消费原始 SQLite 行或 `extra_json`。
- `UserDataRepository` 向页面提供收藏、浏览历史和搜索历史；实体记录只保存稳定实体 ID。
- `AppPreferencesRepository` 保存活动/上一数据包、验证标识、显示偏好和列表偏好。

这些是应用内边界，不对外发布为 SDK。新增页面应优先消费现有边界，而不是绕过它们访问底层数据库。

## 结构与代码锚点

| 区域 | 责任 | 代表代码 |
|---|---|---|
| 启动与导航 | 单 Activity、Bootstrap 状态切换、主导航和 URI 编码详情路由 | `MainActivity.kt`、`navigation/StardewOfflineRoot.kt`、`navigation/AppNavHost.kt` |
| 数据包 | 安全解压、schema 4 发布校验、暂存提交、激活、回滚和清理 | `core/datapackage/SafeZipExtractor.kt`、`DataPackageValidator.kt`、`DataPackageInstaller.kt`、`DataPackageManager.kt` |
| 内容数据库 | 只读 SQLite、元数据、实体摘要/详情、类型、别名和 FTS 查询 | `core/database/content/ContentDatabaseFactory.kt`、`ContentDatabase.kt`、`ContentDatabaseManager.kt` |
| 内容领域 | 仓储、搜索分层评分、关系候选批量解析和语义图鉴模型 | `data/ContentRepository.kt`、`data/SearchRepository.kt`、`data/EntityRelationResolver.kt`、`data/wiki/WikiCatalogue.kt` |
| 详情表达 | `DetailPresentationParser` 保持单一公开入口，事实、关系和共享 JSON 读取分别由内部解析模块拥有 | `core/json/DetailPresentationParser.kt`、`DetailFactParser.kt`、`DetailRelationParser.kt`、`DetailPresentationParsingSupport.kt`、`core/formatter/DetailFormatters.kt` |
| 个人数据 | Room 实体/DAO 与稳定 ID 软引用 | `core/database/user/UserDatabase.kt`、`UserDataDao.kt`、`data/UserDataRepository.kt` |
| 偏好与依赖注入 | DataStore 偏好、数据库和应用单例装配 | `core/datastore/AppPreferencesRepository.kt`、`di/DatabaseModule.kt`、`di/AppModule.kt` |
| 页面 | 首页、分类、搜索、详情、收藏、历史、设置、数据管理、关于 | `feature/*` |

## 当前不变量

- 内容库通过 `ContentDatabaseFactory.open()` 以只读方式打开；`DataPackageManager` 的活动包生命周期锁覆盖仓储查询和切包，`ContentDatabaseManager` 再独占当前句柄，切包前关闭旧库，因此读取不会落在目录替换的中间状态。
- 内容库与 Room 用户库分离；用户记录不写入 `stardew.db`，内容更新不会自动删除稳定 ID 记录。`user.db` 当前为 v3，历史、最近搜索和收藏的排序查询有对应复合索引，v1 用户库经显式迁移保留剩余记录，旧用户表由后续迁移清理。
- `WikiCatalogue` 从活动包的 `entityTypes` 取得可读类型名；首页的“全部分类”以四列网格展示所有有数据类型，并用只读小分组标题组织它们，不保留可点击的主题大类；类型页的细分筛选只消费已确认的摘要字段。
- 条目缺失图片时使用产品占位；图片路径必须在活动包根目录内。未知字段和无法解析关系不制造假实体。
- 详情、收藏和历史均以稳定实体 ID 为键，搜索历史以标准化查询为键；详情路由使用 `Uri.encode`，不能把原始 ID 直接拼入路径。
- `AppNavHost` 是边到边状态栏的唯一 inset 所有者：按当前路由提供与顶部一致的状态栏底色和图标明暗；嵌套页面的 `TopAppBar` 不重复消费状态栏 inset，页面内容色固定使用主题正文色。

## 构建与测试

根工程使用 Java/Kotlin 17、Compose、Material 3、Hilt、Navigation Compose、Room、Preferences DataStore 和 kotlinx.serialization。`app/src/test` 覆盖纯逻辑与数据边界；`app/src/androidTest` 覆盖真实 Android 数据包、Room、导航和 Compose 场景。
