---
doc_type: project-spec
slug: stardew-offline-android
status: ready-for-implementation
language: zh-CN
database_schema_version: 2
last_reviewed: 2026-07-17
---

# 星露谷离线图鉴 Android 项目开发规范

> 本文是完整的 Vibe Coding 项目规范。AI 应根据本文生成一个可编译、可测试、可安装、可离线使用的 Android 项目，而不是只生成演示页面或局部原型。
>
> 数据库结构、字段含义和升级边界以 `database-reference.md` 为唯一事实来源。本文负责规定 Android App 如何消费该数据。

---

## 1. 项目定位

开发一个 Android 原生离线查询 App，用于查看《星露谷物语》的中文游戏数据。

项目暂定名称：

```text
星露谷离线图鉴
```

英文工程名：

```text
Stardew Offline
```

默认包名：

```text
com.example.stardewoffline
```

正式发布前必须替换为开发者自己的唯一包名。

### 1.1 最终体验

用户安装 App 后可以：

- 完全离线浏览数据；
- 使用中文、英文、拼音、拼音首字母和别名搜索；
- 按类型和常用条件筛选；
- 查看实体图片、名称、描述、数值和关联关系；
- 收藏常用条目；
- 查看最近浏览；
- 为实体添加个人笔记；
- 查看当前游戏数据版本；
- 导入新的 `.svdata` 数据包更新数据库；
- 数据更新后继续保留收藏、历史和笔记。

### 1.2 产品原则

按优先级排序：

1. 数据正确；
2. 搜索快；
3. 完全离线；
4. 占用低；
5. 界面清晰；
6. 数据更新安全；
7. 功能丰富；
8. 动画和装饰效果。

### 1.3 明确不做

第一版以及默认最终项目不实现：

- Wiki 网页爬虫；
- 在线账号；
- 云同步；
- 广告；
- 数据统计和遥测；
- 后台常驻服务；
- 本地大模型；
- 读取玩家存档；
- 自动判断玩家当前日期、天气和任务进度；
- 自动解释游戏内部条件表达式；
- 在线攻略社区；
- 修改游戏文件；
- 从手机直接读取电脑 Steam 目录。

---

## 2. 最终交付物

完整项目至少包含：

```text
stardew-offline-android/
├── ANDROID-PROJECT.md
├── README.md
├── LICENSE
├── NOTICE.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── docs/
│   ├── database-reference.md
│   ├── architecture.md
│   ├── data-package.md
│   └── release.md
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       ├── test/
│       └── androidTest/
└── baselineprofile/
    ├── build.gradle.kts
    └── src/
```

项目必须能够执行：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

Windows PowerShell 下对应：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

最终应生成可安装 APK。

---

## 3. 固定技术栈

除非本文明确允许，否则不要替换以下技术栈。

```text
语言                 Kotlin
UI                   Jetpack Compose + Material 3
架构                 单 Activity + UDF + ViewModel
导航                 Navigation Compose
依赖注入             Hilt
异步                 Kotlin Coroutines + Flow
JSON                 kotlinx.serialization
内容数据库           Android SQLiteDatabase，只读打开
用户数据库           Room
设置                 Preferences DataStore
图片                 Coil，仅加载本地 File
测试                 JUnit + AndroidX Test + Compose UI Test
性能                 Macrobenchmark + Baseline Profile
构建                 Gradle Kotlin DSL + Version Catalog
JVM                  Java 17
```

### 3.1 SDK 规则

```text
minSdk       26
compileSdk   实现时可用的最新稳定版本
targetSdk    实现时可用的最新稳定版本
```

依赖版本统一写入：

```text
gradle/libs.versions.toml
```

只允许使用稳定版依赖。没有必要时不得使用 alpha、beta、RC 或 snapshot。

Compose 依赖使用 Compose BOM 管理。

### 3.2 为什么内容库不用 Room

`stardew.db` 是外部生成、只读、可整体替换的数据包成员。

App 不拥有它的模式迁移，也不能向其中写入用户数据。为了保持数据包目录结构、避免额外复制，并能安全切换整个数据包，内容库使用 Android 原生 `SQLiteDatabase` 只读打开。

Room 只负责 App 自己拥有并可迁移的用户数据：

- 收藏；
- 历史；
- 笔记；
- 搜索历史。

---

## 4. 总体架构

采用单向数据流：

```text
Compose Screen
    ↓ UI Event
ViewModel
    ↓
Repository / UseCase
    ↓
Data Source
    ├── 只读内容 SQLite
    ├── 用户 Room 数据库
    ├── DataStore
    └── 本地数据包文件
```

状态返回方向：

```text
Data Source
    ↓
Repository
    ↓
StateFlow<UiState>
    ↓
Compose Screen
```

### 4.1 分层规则

#### UI 层

负责：

- 渲染状态；
- 收集用户事件；
- 页面导航；
- 格式化后的展示。

不负责：

- 拼 SQL；
- 直接操作 Cursor；
- 解压数据包；
- 计算 SHA-256；
- 解析复杂 `extra_json`；
- 直接操作 Room DAO。

#### 数据层

负责：

- 内容数据库查询；
- 用户数据库读写；
- 数据包安装和切换；
- 设置保存；
- JSON 解析；
- 关系解析；
- 搜索合并与排序。

#### Domain 层

只在逻辑复杂时添加 UseCase，不要为每个简单 DAO 方法创建 UseCase。

必须存在的 UseCase：

```text
InstallDataPackageUseCase
ActivateDataPackageUseCase
SearchEntitiesUseCase
GetEntityDetailUseCase
ResolveEntityRelationsUseCase
```

简单的收藏、历史和设置操作可以由 Repository 直接提供。

---

## 5. 工程目录

项目只使用一个业务模块 `:app`，避免过早多模块化；性能测试单独使用 `:baselineprofile`。

```text
app/src/main/java/com/example/stardewoffline/
├── StardewOfflineApp.kt
├── MainActivity.kt
│
├── core/
│   ├── common/
│   │   ├── AppDispatchers.kt
│   │   ├── AppResult.kt
│   │   ├── ClockProvider.kt
│   │   ├── FileUtils.kt
│   │   └── HashUtils.kt
│   ├── model/
│   │   ├── EntityId.kt
│   │   ├── EntityType.kt
│   │   ├── EntitySummary.kt
│   │   ├── EntityDetail.kt
│   │   ├── BuildMeta.kt
│   │   ├── DataManifest.kt
│   │   └── SearchResult.kt
│   ├── database/
│   │   ├── content/
│   │   │   ├── ContentDatabase.kt
│   │   │   ├── ContentDatabaseFactory.kt
│   │   │   ├── ContentQueryDataSource.kt
│   │   │   ├── CursorMappers.kt
│   │   │   └── SqlQueries.kt
│   │   └── user/
│   │       ├── UserDatabase.kt
│   │       ├── dao/
│   │       └── entity/
│   ├── datastore/
│   │   ├── AppPreferences.kt
│   │   └── AppPreferencesRepository.kt
│   ├── datapackage/
│   │   ├── DataPackageManager.kt
│   │   ├── DataPackageInstaller.kt
│   │   ├── DataPackageValidator.kt
│   │   ├── DataPackageState.kt
│   │   └── SafeZipExtractor.kt
│   ├── json/
│   │   ├── ExtraJsonParser.kt
│   │   ├── DerivedModels.kt
│   │   └── RelationModels.kt
│   ├── formatter/
│   │   ├── SeasonFormatter.kt
│   │   ├── GameTimeFormatter.kt
│   │   ├── PriceFormatter.kt
│   │   ├── ChanceFormatter.kt
│   │   └── RawValueFormatter.kt
│   └── ui/
│       ├── theme/
│       ├── component/
│       └── icon/
│
├── data/
│   ├── ContentRepository.kt
│   ├── UserDataRepository.kt
│   ├── SearchRepository.kt
│   ├── SettingsRepository.kt
│   └── DataPackageRepository.kt
│
├── domain/
│   └── usecase/
│
├── feature/
│   ├── bootstrap/
│   ├── home/
│   ├── search/
│   ├── typelist/
│   ├── detail/
│   ├── favorites/
│   ├── history/
│   ├── notes/
│   ├── settings/
│   └── data/
│
└── navigation/
    ├── AppNavHost.kt
    ├── Destinations.kt
    └── BottomNavigation.kt
```

每个 feature 包包含：

```text
FeatureScreen.kt
FeatureRoute.kt
FeatureViewModel.kt
FeatureUiState.kt
FeatureUiEvent.kt
components/
```

---

## 6. 双数据库设计

### 6.1 内容数据库

文件：

```text
stardew.db
```

规则：

- 只读；
- 不通过 Room 管理；
- 不执行迁移；
- 不写收藏、历史和笔记；
- 不修改 `entities`；
- 不创建额外索引；
- 不执行 `VACUUM`；
- 不把整库读取到内存。

打开方式封装在 `ContentDatabaseFactory`：

```kotlin
SQLiteDatabase.openDatabase(
    databaseFile.absolutePath,
    null,
    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
)
```

打开后执行：

```sql
PRAGMA query_only = ON;
```

所有查询运行在 `Dispatchers.IO`。

`ContentDatabase` 必须可关闭，因为更换数据包前必须释放旧数据库文件句柄。

### 6.2 用户数据库

文件：

```text
user.db
```

使用 Room，版本从 `1` 开始。

#### favorites

```sql
CREATE TABLE favorites (
    entity_id TEXT PRIMARY KEY NOT NULL,
    created_at INTEGER NOT NULL
);
```

#### view_history

```sql
CREATE TABLE view_history (
    entity_id TEXT PRIMARY KEY NOT NULL,
    last_viewed_at INTEGER NOT NULL,
    view_count INTEGER NOT NULL DEFAULT 1
);
```

#### notes

```sql
CREATE TABLE notes (
    entity_id TEXT PRIMARY KEY NOT NULL,
    content TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
```

#### recent_searches

```sql
CREATE TABLE recent_searches (
    normalized_query TEXT PRIMARY KEY NOT NULL,
    display_query TEXT NOT NULL,
    last_used_at INTEGER NOT NULL,
    use_count INTEGER NOT NULL DEFAULT 1
);
```

### 6.3 软引用

`user.db` 与 `stardew.db` 是两个不同数据库，不能建立真正的 SQLite 外键。

用户数据库中的 `entity_id` 是软引用。

数据包升级后：

- 找得到实体：正常展示；
- 找不到实体：保留用户记录；
- 收藏页显示“当前数据包中已不存在”；
- 用户可手动删除；
- 不自动清理笔记或收藏。

---

## 7. 数据包目录

运行时目录：

```text
filesDir/
├── content/
│   ├── packages/
│   │   ├── <database-sha256>/
│   │   │   ├── manifest.json
│   │   │   ├── stardew.db
│   │   │   ├── images/
│   │   │   └── reports/
│   │   └── ...
│   └── staging/
├── databases/
│   └── user.db
└── datastore/
```

当前启用包不通过固定 `current/` 目录判断，而是由 DataStore 保存：

```text
active_package_id = <database-sha256>
```

这样更新数据时可以保留旧包并支持回滚。

### 7.1 可选内置数据包

开发者可以把个人生成的数据包放入：

```text
app/src/main/assets/default-data/stardew-zh-cn.svdata
```

该文件必须加入 `.gitignore`，不得默认提交。

启动时：

- 没有已启用包；
- assets 中存在默认包；

则自动复制并安装默认包。

如果 assets 中没有数据包，App 仍应正常编译，并显示“导入数据包”页面。

### 7.2 手动导入

使用 Storage Access Framework 的文件选择器：

```text
OpenDocument
```

接受：

```text
.svdata
.zip
application/octet-stream
application/zip
```

选择文件后立刻复制到 App 临时目录，不长期依赖外部 URI 权限。

不要请求：

```text
READ_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
```

---

## 8. 数据包安装流程

安装必须是可取消、可恢复、可诊断的完整流程。

```text
选择文件
→ 复制到 cache
→ 创建 staging 目录
→ 安全解压
→ 读取 manifest
→ 校验格式和 schema
→ 校验数据库 SHA-256
→ PRAGMA quick_check
→ 校验 build_meta
→ 校验实体数量
→ 校验图片路径
→ 移入 packages/<sha256>
→ 关闭旧内容数据库
→ 更新 active_package_id
→ 打开新数据库
→ 成功后保留旧包作为一次回滚
```

### 8.1 安全解压

`SafeZipExtractor` 必须防止 Zip Slip。

每个条目解压前：

```text
resolvedPath.normalize() 必须位于 stagingRoot 内
```

拒绝：

- 绝对路径；
- `../` 越界；
- 符号链接；
- 文件名为空；
- 条目数量异常；
- 解压后体积异常。

默认上限：

```text
最大压缩包大小       512 MiB
最大解压总大小       1 GiB
最大文件数量         10000
```

这些值集中定义为常量，不散落在代码中。

### 8.2 Manifest 校验

必须满足：

```text
format == "stardew-offline-data"
schemaVersion == 2
language == "zh-CN"
database.file 非空
database.sha256 为合法 SHA-256
```

当前 App 只支持：

```kotlin
SUPPORTED_SCHEMA_VERSIONS = setOf(2)
```

遇到更高版本必须拒绝启用，并提示更新 App。

### 8.3 数据库校验

必须执行：

```sql
PRAGMA quick_check;
```

结果必须仅包含：

```text
ok
```

读取：

```text
build_meta.schema_version
build_meta.locale
build_meta.entity_count
```

必须满足：

```text
build_meta.schema_version == manifest.schemaVersion
build_meta.locale == manifest.language
entities.count == build_meta.entity_count
entities.count 与 manifest.content.entities 一致
```

同时检查：

```text
entity_search 数量应与 entities 数量合理接近
```

不要要求数据库级外键约束，因为参考文档没有保证该约束存在。

### 8.4 激活与回滚

激活新包前关闭旧的 `ContentDatabase`。

把 `active_package_id` 更新为新包 ID 后，立即尝试重新打开并执行一个轻量查询。

如果失败：

- 恢复旧 `active_package_id`；
- 重新打开旧包；
- 保留失败包到诊断目录；
- 向用户显示错误；
- 不影响收藏和笔记。

最多保留：

```text
当前包 + 上一个可回滚包
```

其余旧包可以在安装成功后清理。

---

## 9. 启动流程

启动页只处理数据准备，不展示假数据。

```text
App 启动
→ 读取 active_package_id
→ 检查目录与数据库文件
→ 打开只读数据库
→ 读取 build_meta
→ 进入主界面
```

如果没有数据包：

```text
检查 assets 默认包
→ 有：自动安装
→ 无：进入数据导入页
```

状态：

```kotlin
sealed interface BootstrapUiState {
    data object Loading
    data class Installing(val progress: Float?, val message: String)
    data object NeedDataPackage
    data class Ready(val packageInfo: DataPackageInfo)
    data class Error(val error: AppError)
}
```

不要在每次启动时重新计算整个数据库 SHA-256。

完整 SHA-256 只在安装和显式“验证数据”时执行。正常启动只进行轻量文件存在检查、打开数据库和读取元数据。

---

## 10. 内容数据库接口

### 10.1 ContentQueryDataSource

必须提供：

```kotlin
suspend fun getBuildMeta(): BuildMeta
suspend fun getEntityTypeCounts(): List<EntityTypeCount>
suspend fun getEntitySummary(id: String): EntitySummary?
suspend fun getEntityDetail(id: String): EntityDetail?
suspend fun getEntitiesByType(type: String): List<EntitySummary>
suspend fun getEntitiesByIds(ids: List<String>): Map<String, EntitySummary>
suspend fun getAliases(entityId: String): List<String>
suspend fun searchExact(query: SearchQuery, limit: Int): List<ScoredEntity>
suspend fun searchPrefix(query: SearchQuery, limit: Int): List<ScoredEntity>
suspend fun searchFts(ftsQuery: String, limit: Int): List<ScoredEntity>
suspend fun getCategories(type: String): List<String>
```

### 10.2 列表查询

列表只读取轻字段，不读取 `extra_json`：

```sql
SELECT
    e.id,
    e.entity_type,
    e.name_zh,
    e.name_en,
    e.category,
    e.image_path,
    s.pinyin AS sort_key
FROM entities AS e
LEFT JOIN entity_search AS s ON s.entity_id = e.id
WHERE e.entity_type = ?
ORDER BY
    CASE WHEN s.pinyin IS NULL OR s.pinyin = '' THEN 1 ELSE 0 END,
    s.pinyin COLLATE NOCASE,
    e.name_zh COLLATE NOCASE;
```

这样中文列表优先按已有拼音排序，避免只依赖 `NOCASE`。

### 10.3 详情查询

```sql
SELECT *
FROM entities
WHERE id = ?
LIMIT 1;
```

所有参数都必须绑定，不允许字符串拼接用户输入。

### 10.4 Cursor 规则

- 每个 Cursor 必须使用 `use {}`；
- 通过列名获取索引；
- 可空列必须安全读取；
- 未知列不影响运行；
- 映射函数不得执行 UI 格式化；
- 单条映射错误不能导致整个列表崩溃，记录后跳过异常行；
- Debug 构建保留详细日志；
- Release 构建不得记录完整私人文件路径。

---

## 11. 核心数据模型

### 11.1 EntitySummary

```kotlin
data class EntitySummary(
    val id: String,
    val entityType: String,
    val nameZh: String,
    val nameEn: String?,
    val category: String?,
    val imagePath: String?,
    val sortKey: String?
)
```

### 11.2 EntityDetail

```kotlin
data class EntityDetail(
    val id: String,
    val entityType: String,
    val gameId: String?,
    val internalName: String?,
    val nameZh: String,
    val nameEn: String?,
    val descriptionZh: String?,
    val descriptionEn: String?,
    val category: String?,
    val translationStatus: TranslationStatus,
    val imagePath: String?,
    val extraJson: JsonObject,
    val sourceFile: String?,
    val createdAt: String
)
```

### 11.3 EntityId

不要把 ID 当普通数字。

```kotlin
@JvmInline
value class EntityId(val value: String)
```

提供：

```kotlin
val typePrefix: String?
val sourceId: String?
```

但不能只使用 `sourceId` 做唯一标识。

---

## 12. extra_json 解析策略

`extra_json` 是可演进字段，不能把整个根对象强制映射成一个巨大的固定 Kotlin 类。

使用：

```kotlin
Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}
```

先解析为：

```kotlin
JsonObject
```

读取层次：

```text
标准数据库列
→ officialDerived
→ 已明确支持的根字段
→ Debug 高级原始数据
```

### 12.1 OfficialDerived

为稳定字段创建类型模型：

```text
CropDerived
FishDerived
VillagerDerived
ItemDerived
RecipeDerived
ShopOffer
MachineUse
UsedInRelation
FishLocation
FishPondRule
```

字段全部可空或有安全默认空列表。

缺失字段表示“没有数据”，不得自动解释为 `false`、`0` 或“不可能”。

### 12.2 根层数据

根层官方字段只按存在性读取。

禁止：

- 依赖 JSON 字段顺序；
- 依赖未声明字段永远存在；
- 将未知数值自动转换为游戏结论；
- 使用 `legacyFields` 下标解释旧式记录。

### 12.3 旧式数据

以下类型可能主要包含 `legacyValue`：

```text
achievement
fish 的部分记录
furniture
footwear
monster
npc_schedule
villager_gift
ginger_island
部分配方
```

默认 UI：

- 展示标准名称、描述、图片；
- 展示已有 `officialDerived`；
- 不解释 `legacyFields` 下标；
- 高级模式可展开“原始记录”；
- 无可识别结构时显示“暂无可结构化展示的数据”。

---

## 13. 实体类型注册表

集中创建：

```kotlin
object EntityTypeRegistry
```

每种类型定义：

```kotlin
data class EntityTypeDefinition(
    val id: String,
    val labelRes: Int,
    val icon: ImageVector,
    val homePriority: Int?,
    val renderer: DetailRendererKind,
    val searchable: Boolean = true,
    val visibleInAllTypes: Boolean = true
)
```

至少支持：

```text
object
mineral
ring
crop
fish
villager
cooking_recipe
crafting_recipe
big_craftable
furniture
footwear
tool
trinket
weapon
bundle
quest
special_order
shop
tailoring_recipe
monster
drop
achievement
ginger_island
npc_schedule
villager_gift
```

未知的新类型：

- 不崩溃；
- 自动进入“其他”；
- 使用通用详情渲染；
- Debug 日志记录类型；
- 不删除或忽略实体。

---

## 14. 搜索系统

搜索是核心功能，不能只写一条简单 FTS SQL。

### 14.1 输入标准化

`SearchQueryNormalizer` 执行：

1. Unicode NFKC 标准化；
2. 去除首尾空白；
3. 连续空白合并为一个空格；
4. 英文使用 `Locale.ROOT` 转小写；
5. 最长 64 个字符；
6. 最多 8 个词；
7. 空输入不执行数据库搜索。

### 14.2 LIKE 转义

必须转义：

```text
%
_
\
```

查询使用：

```sql
LIKE ? ESCAPE '\'
```

### 14.3 FTS 输入安全

不能把用户原始文本直接传入 `MATCH`。

处理规则：

- 去掉 FTS 操作符；
- 引号、括号、冒号、减号转换为空格；
- 只保留中文、字母、数字和空格；
- 每个词安全生成前缀查询；
- 无有效 token 时跳过 FTS。

### 14.4 搜索分层

执行并合并以下结果：

```text
中文名完全匹配            1000
中文名前缀匹配             900
别名完全匹配               850
英文名完全匹配             800
英文名前缀匹配             750
拼音首字母完全匹配         700
拼音首字母前缀匹配         650
拼音前缀匹配               600
FTS 命中                   500
```

最终：

- 按实体 ID 去重；
- 同一实体保留最高分；
- 同分时按拼音 `sortKey`；
- 默认最多返回 60 条；
- 搜索结果显示命中原因，例如“中文名”“别名”“拼音”。

FTS4 不承担全部排序逻辑，相关度由 App 的分层评分控制。

### 14.5 搜索界面

输入框行为：

- 150ms debounce；
- 输入变化取消旧任务；
- 清空按钮；
- 空输入显示最近搜索；
- 支持删除单条和清空全部；
- 搜索成功后才写入 recent_searches；
- 点击结果进入详情；
- 返回后保留查询和滚动位置。

### 14.6 必测关键词

至少验证：

```text
防风草
防风
Parsnip
parsnip
fang feng cao
ffc
人工别名
包含空格
包含 %
包含 "
空字符串
```

---

## 15. 关系解析

数据库关系字段中的 ID 可能不是完整实体 ID。

创建：

```kotlin
class EntityReferenceResolver
```

### 15.1 解析规则

如果 ID 已包含 `:`：

```text
按完整 ID 精确查询
```

如果关系明确给出类型：

```text
outputEntityType + ":" + sourceId
```

未限定普通物品 ID 的候选顺序：

```text
object
mineral
ring
big_craftable
furniture
footwear
weapon
tool
trinket
```

机器 ID 候选顺序：

```text
big_craftable
object
```

NPC：

```text
villager
```

商店：

```text
shop
```

### 15.2 批量解析

关系卡片不得逐条查询产生 N+1。

流程：

```text
收集页面所有关系 ID
→ 生成候选完整 ID
→ 一次 SELECT ... WHERE id IN (...)
→ 在内存映射结果
```

### 15.3 无匹配降级

无法匹配时：

- 仍显示源 ID；
- 显示“未找到对应条目”；
- 不隐藏整条关系；
- 不猜测中文名称；
- 不创建假实体。

---

## 16. 页面和导航

使用单 Activity。

底部导航四项：

```text
首页
搜索
收藏
更多
```

路由：

```text
Bootstrap
Home
Search
TypeList/{entityType}
EntityDetail/{encodedEntityId}
Favorites
History
Notes
More
Settings
DataManagement
DataPackageInfo
About
```

实体 ID 进入路由前必须 URI 编码，禁止直接把 `:` 等字符拼入路径。

### 16.1 启动/数据导入页

无数据包时展示：

- App 简介；
- “选择 `.svdata` 数据包”按钮；
- 支持格式说明；
- 不要求联网；
- 导入进度；
- 错误详情；
- 重试按钮。

### 16.2 首页

结构：

```text
顶部标题
全局搜索入口
常用分类网格
最近浏览
收藏快捷入口
当前数据版本
全部分类
```

首页常用分类建议：

```text
作物
鱼类
村民
物品
料理
制作
商店
怪物
武器
收集包
任务
姜岛
```

分类是否存在以数据库实际数量为准。数量为零的分类不显示在首页。

### 16.3 类型列表页

展示：

- 类型名称；
- 条目数量；
- 分类筛选；
- 类型内搜索；
- 列表或紧凑网格切换；
- 图片、中文名、英文名、分类；
- 收藏标记。

类型内搜索优先在已加载的摘要中筛选；数据量过大时调用数据库查询。

不加载 `extra_json`。

### 16.4 搜索页

展示：

- 搜索输入；
- 最近搜索；
- 搜索结果；
- 类型筛选 Chip；
- 命中原因；
- 空结果建议。

输入时不得阻塞主线程。

### 16.5 通用详情页

顶部：

- 返回按钮；
- 收藏按钮；
- 图片；
- 中文名；
- 英文名；
- 类型和分类；
- 中文描述；
- 缺少中文时的提示。

下面按顺序：

```text
类型核心信息
获取或出现条件
商店来源
加工用途
配方与被使用关系
其他关系
个人笔记
数据来源与高级信息
```

空卡片不显示。

### 16.6 收藏页

功能：

- 显示所有收藏；
- 按收藏时间排序；
- 按类型筛选；
- 支持搜索；
- 支持取消收藏；
- 缺失实体显示占位项。

### 16.7 历史页

功能：

- 最近浏览优先；
- 最多默认保留 200 条；
- 清空历史；
- 单条删除；
- 缺失实体允许清理；
- 进入详情成功后记录浏览。

### 16.8 笔记

详情页可添加纯文本笔记：

- 自动保存或显式保存均可，但需有清晰状态；
- 最大长度 5000；
- 不支持富文本；
- 不支持图片；
- 删除前确认；
- 数据包更新后继续保留。

### 16.9 更多页

入口：

```text
浏览历史
数据管理
设置
关于
开源许可
```

### 16.10 数据管理页

展示：

- 游戏版本；
- 数据包生成时间；
- 数据库 schema；
- Builder 版本；
- 实体总数；
- 缺少翻译数量；
- 当前包 SHA-256 缩写；
- 导入新数据包；
- 验证当前数据；
- 回滚到上一个包；
- 删除旧包；
- 导出诊断信息。

诊断信息不得包含完整用户路径。

---

## 17. 类型详情渲染

详情页采用注册表分派，而不是巨大 `when` 塞在一个 Composable 中。

```text
DetailRenderer
├── ItemDetailRenderer
├── CropDetailRenderer
├── FishDetailRenderer
├── VillagerDetailRenderer
├── RecipeDetailRenderer
├── EquipmentDetailRenderer
├── ShopDetailRenderer
├── QuestDetailRenderer
├── MonsterDetailRenderer
└── GenericDetailRenderer
```

### 17.1 物品 / 矿物 / 戒指

展示：

- 售价；
- 食用值；
- 分类；
- contextTags；
- 商店报价；
- 机器用途；
- 被哪些配方或收集包使用。

`contextTags` 是游戏代号，默认折叠到“技术标签”。

### 17.2 作物

展示：

- 季节；
- 总生长天数；
- 分阶段生长时间；
- 是否再生；
- 再生天数；
- 是否需要浇水；
- 是否水稻；
- 是否棚架；
- 最小/最大收获量；
- 种子实体；
- 收获实体；
- 种子购买来源。

生长阶段可以用简洁时间轴，不需要复杂动画。

### 17.3 鱼类

展示：

- 难度；
- 行为代号；
- 尺寸；
- 季节；
- 天气；
- 时间段；
- 地点；
- 钓鱼等级要求；
- 离岸距离；
- 鱼塘规则；
- 机器用途。

时间值只有在符合合法游戏时间格式时才格式化，否则显示原值。

### 17.4 村民

展示：

- 生日；
- 居住区域；
- 性别；
- 是否可婚配；
- 恋爱对象；
- 已结构化的礼物或日程关系；
- 其他公开属性。

不要根据缺失字段推断“不可婚配”或“不收礼物”。

### 17.5 配方

展示：

- 原料列表；
- 数量；
- 产物；
- 产物类型；
- 关联实体跳转；
- 无法解析的原料 ID。

### 17.6 武器 / 工具 / 鞋 / 饰品

按字段存在性展示：

- 伤害；
- 防御；
- 速度；
- 暴击；
- 售价；
- 升级关系；
- 附件槽；
- 丢失规则；
- 商店来源。

### 17.7 商店

展示：

- 店铺名称；
- 货币；
- 所有者；
- 商品列表；
- 价格；
- 交易物品；
- 库存；
- 条件表达式。

条件表达式原样显示，并标注：

```text
此条件由游戏运行时判断，App 未自动解析。
```

### 17.8 怪物和掉落

怪物展示已结构化字段。

掉落展示：

- 怪物；
- 物品；
- 概率原值或合法百分比；
- 关系跳转。

### 17.9 通用渲染

未知类型或暂未结构化类型：

- 通用头部；
- 描述；
- 已知简单根字段；
- `officialDerived` 中可识别的键值；
- 高级模式原始 JSON；
- 不解释 `legacyFields`。

---

## 18. 格式化规则

所有格式化集中到 `core/formatter`。

### 18.1 季节

```text
spring → 春季
summer → 夏季
fall   → 秋季
winter → 冬季
```

未知值显示原文。

### 18.2 布尔值

只有字段明确存在时才展示：

```text
是 / 否
```

字段缺失时整行不显示。

### 18.3 概率

如果数值在 `0..1`：

```text
0.25 → 25%
```

其他情况显示原始值，不猜测单位。

### 18.4 游戏时间

支持常见 HHMM 数值：

```text
600  → 06:00
1830 → 18:30
2400 → 24:00
2600 → 26:00
```

分钟部分不合法时显示原值。

### 18.5 价格

存在 `currency` 时按原值显示货币类型。

不要默认把所有价格都解释为金币。

---

## 19. 图片加载

图片入口只能使用：

```text
entities.image_path
```

不能在手机端根据：

```text
imageSource
spriteIndex
```

现场裁图。

### 19.1 路径安全

```kotlin
packageRoot.resolve(imagePath).normalize()
```

结果必须仍在 `packageRoot` 中。

路径非法或文件不存在：

- 显示类型占位图；
- 不崩溃；
- Debug 日志记录。

### 19.2 Coil

Coil 只加载本地 `File`。

禁止配置网络图片加载器。

像素画使用最近邻或关闭平滑过滤，避免模糊。

列表图片限制尺寸，详情页按可见区域加载，不预解码所有图片。

---

## 20. UI 设计规范

风格：

```text
干净
轻量
原生
温和的像素游戏气质
不复刻游戏原界面
不过度拟物
```

使用 Material 3，但采用自定义静态主题色。

支持：

```text
跟随系统
浅色
深色
```

不要默认启用动态取色，避免不同设备上视觉风格失控；可在设置中作为可选项。

### 20.1 组件

统一组件：

```text
EntityListItem
EntityImage
InfoCard
KeyValueRow
TagChip
RelationItem
EmptyState
ErrorState
LoadingState
SearchField
SectionHeader
```

### 20.2 可访问性

必须：

- 触控目标至少 48dp；
- 图片提供内容描述或明确装饰性；
- 支持系统字体放大；
- 不用颜色作为唯一状态提示；
- TalkBack 顺序合理；
- 关键按钮有文字或语义；
- 横屏不遮挡内容；
- 软键盘弹出时搜索结果仍可操作。

---

## 21. ViewModel 规则

每个页面使用不可变 UiState。

示例：

```kotlin
data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val selectedTypes: Set<String> = emptySet(),
    val error: UiMessage? = null
)
```

规则：

- ViewModel 不持有 Activity 或 View；
- UI 不直接启动数据库查询；
- 所有长任务可取消；
- `stateIn` 使用合理的 SharingStarted；
- 页面一次性消息使用明确事件通道；
- 不把 `Context` 放入普通 ViewModel；
- SavedStateHandle 只保存轻量路由和 UI 状态；
- 不把完整实体 JSON 放入 SavedStateHandle。

---

## 22. Hilt 依赖图

至少提供：

```text
AppModule
DatabaseModule
RepositoryModule
DataPackageModule
DispatcherModule
```

作用域：

```text
@Singleton
ContentDatabaseManager
UserDatabase
Repositories
DataPackageManager
Json
AppPreferencesRepository
```

内容数据库实例由 `ContentDatabaseManager` 控制，而不是直接把 `SQLiteDatabase` 注入所有类。

切换数据包时：

```text
Manager.close()
→ 更新 active package
→ Manager.open()
```

---

## 23. 设置

使用 Preferences DataStore。

键：

```text
theme_mode
dynamic_color_enabled
show_english_name
show_technical_fields
search_history_enabled
active_package_id
previous_package_id
last_validated_package_id
list_layout_mode
```

`active_package_id` 属于运行状态，不放入 Room。

设置页：

- 主题；
- 是否显示英文名；
- 是否显示技术字段；
- 是否记录搜索历史；
- 列表布局；
- 数据管理；
- 清除浏览历史；
- 清除搜索历史。

---

## 24. 错误模型

统一：

```kotlin
sealed interface AppError
```

至少包含：

```text
NoDataPackage
InvalidPackageFormat
UnsupportedSchema
InvalidManifest
HashMismatch
DatabaseCorrupted
DatabaseOpenFailed
DatabaseQueryFailed
UnsafeArchiveEntry
PackageTooLarge
ImportCancelled
ImageMissing
JsonParseFailed
Unknown
```

错误分为：

- 用户可解决；
- 可重试；
- 数据损坏；
- 开发错误。

UI 显示中文短说明和下一步操作。

Debug 模式可显示技术详情；Release 默认折叠。

---

## 25. 日志与诊断

不引入在线日志服务。

创建轻量：

```kotlin
AppLogger
```

Debug：

- 输出 Logcat；
- 包含堆栈；
- 可记录数据库查询耗时；
- 可记录实体 ID。

Release：

- 不记录完整文件路径；
- 不记录笔记内容；
- 不记录搜索内容；
- 不记录用户目录；
- 只保留必要错误类别。

数据管理页可以导出一个诊断 JSON：

```json
{
  "appVersion": "",
  "schemaVersion": 2,
  "gameVersion": "",
  "builderVersion": "",
  "entityCount": 0,
  "databaseHashPrefix": "",
  "lastValidation": "",
  "errorCodes": []
}
```

不导出数据库、图片、收藏或笔记。

---

## 26. 隐私与权限

App 默认不声明：

```xml
android.permission.INTERNET
```

也不声明：

```text
位置
相机
通讯录
麦克风
存储管理
通知
后台运行
```

文件导入使用系统文件选择器。

App 不上传任何数据。

---

## 27. 备份规则

Android 自动备份中：

包含：

```text
user.db
DataStore 用户设置
```

排除：

```text
content/packages/
content/staging/
cache/
导入临时文件
```

内容数据可重新导入，不应浪费备份空间。

签名密钥、数据包和真实游戏资源不得进入 Git。

---

## 28. 法律与说明页面

关于页必须包含：

- 本项目为非官方工具；
- 与 ConcernedApe、发行商和 Wiki 无从属关系；
- 游戏名称、角色和素材权利归原权利人；
- 数据包由用户自己的游戏资源生成；
- App 不爬取 Wiki；
- 开源依赖许可证；
- 数据生成器与 App 的版本信息。

`NOTICE.md` 记录所有第三方依赖和许可证。

---

## 29. 性能目标

以下是项目验收目标，不是无条件承诺；必须通过真实设备测量。

```text
首次可交互冷启动          ≤ 1.5 秒
已打开后的普通页面切换    ≤ 150 ms
常见搜索 P95             ≤ 100 ms
详情查询 P95             ≤ 80 ms
列表滚动                  无明显卡顿
空闲内存                  目标 ≤ 120 MB
App 本体                  目标 ≤ 30 MB，不含数据包
后台服务                  0
网络请求                  0
```

### 29.1 性能规则

- 不在主线程访问数据库；
- 不在主线程解压和哈希；
- 列表只读取摘要列；
- 详情才读取 `extra_json`；
- 关系批量查询；
- 图片按需加载；
- 搜索任务可取消；
- 不预加载所有实体详情；
- Release 开启 R8 和资源压缩；
- 为启动、搜索、滚动和详情打开生成 Baseline Profile。

---

## 30. 测试策略

### 30.1 测试夹具

仓库中放置自行构造的小型 `.svdata`：

```text
app/src/androidTest/assets/fixtures/test-data.svdata
```

不得复制完整游戏数据。

夹具至少包含：

```text
object:24
crop:24
fish:128
villager:Abigail
一条别名
一条商店关系
一条机器关系
一条 usedIn
一张测试 WebP
```

### 30.2 单元测试

必须覆盖：

```text
Manifest 解析
Schema 拒绝
SHA-256
Zip Slip 防护
大小限制
SearchQueryNormalizer
LIKE 转义
FTS 转义
搜索结果合并和评分
EntityId 解析
关系候选生成
officialDerived 解析
季节格式化
时间格式化
概率格式化
未知字段兼容
ViewModel 状态变化
```

### 30.3 数据库测试

必须覆盖：

```text
只读打开
build_meta 读取
实体列表
详情查询
别名查询
FTS 搜索
中文搜索
英文搜索
拼音搜索
拼音首字母
特殊字符
空输入
不存在实体
```

### 30.4 Room 测试

覆盖：

```text
收藏增删
历史 upsert
历史数量限制
笔记保存和删除
搜索历史排序
数据库迁移
```

### 30.5 Compose UI 测试

覆盖：

```text
无数据包时显示导入页
导入成功进入首页
首页分类显示
搜索进入详情
收藏状态更新
历史生成
笔记保存
数据包错误展示
高字体缩放
深色主题
```

### 30.6 数据包升级测试

流程：

```text
安装包 A
收藏实体
写笔记
安装包 B
确认切换
确认收藏和笔记保留
确认旧包可回滚
确认失败包不激活
```

### 30.7 性能测试

Macrobenchmark：

```text
ColdStartupBenchmark
SearchBenchmark
TypeListScrollBenchmark
OpenDetailBenchmark
```

Baseline Profile 关键路径：

```text
启动到首页
点击搜索
输入关键词
打开搜索结果
打开分类列表
滚动列表
打开详情
```

---

## 31. Debug 工具

Debug 构建增加隐藏的“开发者数据检查”页面。

功能：

- 查看所有 `entity_type` 数量；
- 运行 `PRAGMA quick_check`；
- 查询实体 ID；
- 查看原始 `extra_json`；
- 运行测试搜索；
- 检查缺失图片；
- 检查 FTS 与 entities 数量；
- 查看未解析关系数量；
- 复制诊断 JSON。

Release 不显示该入口。

---

## 32. 构建配置

### 32.1 Build Types

```text
debug
release
```

Debug：

- applicationIdSuffix `.debug`；
- 显示完整诊断；
- 不压缩；
- 允许开发者检查页。

Release：

- `isMinifyEnabled = true`；
- `isShrinkResources = true`；
- 关闭详细日志；
- 使用签名配置；
- 生成 Baseline Profile；
- 禁止明文网络配置，因为 App 没有网络功能。

### 32.2 Git 忽略

必须忽略：

```text
local.properties
*.jks
*.keystore
keystore.properties
app/src/main/assets/default-data/*.svdata
**/content/packages/
**/content/staging/
build/
.idea/
```

不要忽略：

```text
gradle-wrapper.jar
数据库测试夹具
Room 导出的 schema
```

---

## 33. 文档要求

项目完成时更新：

### README.md

包含：

- 项目用途；
- 截图；
- 编译环境；
- 如何放入默认数据包；
- 如何生成 APK；
- 如何导入数据包；
- 隐私和版权说明。

### docs/architecture.md

包含：

- 双数据库原因；
- 数据流；
- 包激活；
- 搜索排序；
- 关系解析；
- 错误边界。

### docs/data-package.md

不重复完整数据库字段，只说明 App 导入和验证流程，并链接 `database-reference.md`。

### docs/release.md

包含：

- 修改包名；
- 签名；
- Release 构建；
- 数据包是否内置；
- 许可证检查；
- APK 验证。

---

## 34. 开发阶段

AI 可以连续实现完整项目，但必须按以下顺序推进。每个阶段完成后运行对应测试，不允许跳过基础链路直接堆 UI。

### 阶段 0：工程初始化

完成：

- Gradle 工程；
- Compose；
- Hilt；
- Navigation；
- Material 3；
- Version Catalog；
- Java 17；
- 基础测试；
- README；
- `.gitignore`。

验收：

```text
assembleDebug 成功
lintDebug 成功
基础测试成功
```

### 阶段 1：数据包和启动链路

完成：

- Manifest 模型；
- SafeZipExtractor；
- Validator；
- SHA-256；
- quick_check；
- DataStore active package；
- Bootstrap 页面；
- 测试夹具导入。

验收：

```text
无包显示导入页
有效包成功启用
坏包不影响旧包
重启仍能打开
```

### 阶段 2：内容数据库

完成：

- 只读 SQLite；
- BuildMeta；
- EntitySummary；
- EntityDetail；
- 类型数量；
- 列表和详情；
- Cursor 测试。

### 阶段 3：搜索

完成：

- 标准化；
- 精确和前缀；
- 别名；
- FTS；
- 评分合并；
- 搜索页；
- 最近搜索。

### 阶段 4：首页和分类

完成：

- 首页；
- 常用分类；
- 全部分类；
- 类型列表；
- 图片；
- 空状态；
- 筛选。

### 阶段 5：详情渲染

按顺序：

```text
通用头部
物品
作物
鱼类
村民
配方
装备
商店
任务
怪物
通用 fallback
```

### 阶段 6：用户功能

完成：

- Room；
- 收藏；
- 历史；
- 笔记；
- 设置；
- 缺失实体降级。

### 阶段 7：数据管理

完成：

- 导入新包；
- 版本信息；
- 验证；
- 回滚；
- 清理旧包；
- 诊断导出。

### 阶段 8：质量完善

完成：

- 深浅主题；
- 可访问性；
- 横屏；
- 错误文案；
- UI 测试；
- R8；
- 备份规则；
- 关于页；
- 许可证。

### 阶段 9：性能与发布

完成：

- Macrobenchmark；
- Baseline Profile；
- 性能问题修复；
- Release APK；
- 最终文档；
- 完整验收报告。

---

## 35. AI 编码规则

AI 必须遵守：

1. 每次修改前阅读本文和 `database-reference.md`；
2. 数据库参考文档优先于任何猜测；
3. 不修改 `stardew.db`；
4. 不把收藏写入内容数据库；
5. 不添加网络功能；
6. 不添加爬虫；
7. 不添加账号；
8. 不添加后台服务；
9. 不使用 WebView；
10. 不把游戏数据硬编码到 Kotlin；
11. 不解释未定义的游戏规则；
12. 不依赖 `legacyFields` 下标；
13. 不把未知字段当错误；
14. 不因单条坏数据让整个列表崩溃；
15. 所有 SQL 参数绑定；
16. 所有文件路径做越界检查；
17. 所有数据库和文件任务运行在 IO Dispatcher；
18. 每个功能配套测试；
19. 不为了“架构漂亮”创建无意义模块和 UseCase；
20. 不使用已废弃 API；
21. 依赖使用稳定版本；
22. 不静默吞错；
23. Release 不记录用户笔记、搜索词和完整路径；
24. 不提交真实 `.svdata`、游戏图片或文本；
25. 不提前宣称测试通过，必须实际执行命令；
26. 遇到不确定字段时降级展示，不伪造含义；
27. 新增重要架构决定时同步更新文档；
28. 保持项目始终可编译；
29. 阶段结束后列出修改文件、测试命令和结果；
30. 完整项目结束前执行最终验收清单。

---

## 36. 最终验收清单

### 数据

- [ ] 支持 schema 2；
- [ ] 不支持的 schema 会拒绝；
- [ ] SHA-256 校验；
- [ ] `PRAGMA quick_check`；
- [ ] manifest 与 build_meta 一致；
- [ ] 数据包整体切换；
- [ ] 可回滚；
- [ ] 内容库只读；
- [ ] 用户库独立。

### 搜索

- [ ] 中文；
- [ ] 中文前缀；
- [ ] 英文；
- [ ] 英文大小写；
- [ ] 拼音；
- [ ] 拼音首字母；
- [ ] 别名；
- [ ] 特殊字符安全；
- [ ] 搜索排序合理；
- [ ] 返回后保留状态。

### 浏览

- [ ] 首页；
- [ ] 全部分类；
- [ ] 类型列表；
- [ ] 通用详情；
- [ ] 主要类型详情；
- [ ] 关系跳转；
- [ ] 未解析关系降级；
- [ ] 图片缺失占位；
- [ ] 未知类型不崩溃。

### 用户数据

- [ ] 收藏；
- [ ] 历史；
- [ ] 笔记；
- [ ] 设置；
- [ ] 数据更新后保留；
- [ ] 缺失实体仍保留记录。

### 质量

- [ ] 无 INTERNET 权限；
- [ ] 无后台服务；
- [ ] 深色主题；
- [ ] 字体缩放；
- [ ] TalkBack 基础可用；
- [ ] Release R8；
- [ ] Baseline Profile；
- [ ] 单元测试；
- [ ] 仪器测试；
- [ ] UI 测试；
- [ ] 性能测试；
- [ ] README；
- [ ] 法律说明；
- [ ] 许可证清单；
- [ ] APK 可安装并离线运行。

---

## 37. 交给 AI 的完整任务

```text
请阅读 `.codestable/requirements/stardew-offline-android.md` 和 `.codestable/requirements/database-reference.md`。

根据文档从零实现完整的 Android 项目“星露谷离线图鉴”。

要求：

1. 使用 Kotlin、Jetpack Compose、Material 3、Hilt、Navigation Compose；
2. 使用单 Activity、UDF、ViewModel、StateFlow；
3. stardew.db 使用 SQLiteDatabase 只读打开；
4. 收藏、历史、笔记和搜索历史使用独立 Room 数据库；
5. 设置和当前数据包状态使用 Preferences DataStore；
6. 支持可选的 assets 默认 .svdata 和用户手动导入；
7. 完整实现安全解压、manifest 校验、SHA-256、PRAGMA quick_check、schema 校验、原子切换与回滚；
8. 实现中文、英文、拼音、首字母、别名搜索，并进行分层评分；
9. 实现首页、搜索、分类列表、实体详情、收藏、历史、笔记、设置、数据管理和关于页面；
10. 根据 entity_type 和 officialDerived 实现主要详情渲染器，并为未知或旧式数据提供通用降级；
11. 不联网、不爬 Wiki、不声明 INTERNET 权限；
12. 不修改内容数据库，不提交真实游戏资源；
13. 创建完整单元测试、数据库测试、Compose UI 测试、Macrobenchmark 和 Baseline Profile；
14. 依赖版本集中在 libs.versions.toml，只使用稳定版；
15. 保持项目在每个阶段都可以编译；
16. 按 ANDROID-PROJECT.md 的阶段顺序实现，完成一个阶段后运行测试再继续；
17. 不停留在模板或伪代码，最终必须生成可编译、可安装、可使用的完整项目；
18. 最终执行 assembleDebug、testDebugUnitTest、lintDebug，并在可用设备上执行 connectedDebugAndroidTest；
19. 修复所有由本次实现引入的编译、Lint 和测试错误；
20. 最终输出：
   - 项目结构；
   - 已完成能力；
   - 未完成或受环境限制的能力；
   - 运行过的命令；
   - 测试结果；
   - APK 路径；
   - 如何放入默认 .svdata；
   - 如何在手机中导入 .svdata。

数据库字段含义不得凭空猜测。遇到未知字段时必须按文档降级处理。
```

---

## 38. 完成定义

项目只有同时满足以下条件才算完成：

```text
能编译
能安装
没有数据包时能导入
有有效数据包时能进入首页
能搜索真实数据库
能打开主要实体详情
能显示本地图片
能收藏
能记录历史
能写笔记
能更新数据包
更新后用户数据不丢
坏数据包不会破坏当前可用数据
全程不依赖网络
Release 可构建
测试可重复执行
```

只有静态页面、Mock 数据、未接数据库、未实现导入、未通过编译或只完成某个阶段，都不能视为完整项目。
