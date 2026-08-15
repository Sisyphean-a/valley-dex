---
scope: context:content-package
code-paths:
  - app/src/main/java/com/example/stardewoffline/core/datapackage
  - app/src/main/java/com/example/stardewoffline/core/database/content
  - app/src/main/java/com/example/stardewoffline/core/model/DataManifest.kt
  - app/src/main/java/com/example/stardewoffline/core/model/DataPackageInfo.kt
  - app/src/main/java/com/example/stardewoffline/core/datastore/AppPreferencesRepository.kt
---

# 内容数据包领域上下文

这个边界把外部生成的 `.svdata` 发布物安全地变成应用可读取的活动内容包。

## 通用语言

**`.svdata` 数据包**：一个 ZIP 发布物，至少包含 `manifest.json`、`stardew.db`、本地图片和报告目录。

**发布级数据包**：满足应用当前发布契约、可被激活的包；目标协议只支持 `format=stardew-offline-data`、`manifestVersion=2`、SQLite `schemaVersion=5`、`contentContract=player-facts-v1`、`language=zh-CN`、`publishable=true`，且全部事实、关系、条件、来源、视觉与质量门禁通过。schema 4 是旧协议，不在设备上转换。

**活动 v5 包（`active_v5`）**：Preferences DataStore 当前指向、供新版内容查询使用的 schema 5 包。

**兼容上一 v5 包（`previous_compatible_v5`）**：已完整验证、可由当前 App 直接回滚打开的上一 schema 5 包。

**固定旧 v4 包（`pinned_legacy_v4`）**：只供旧版 App 整版恢复的冻结 schema 4 包；不进入新版 App 普通回滚入口，也不被普通包清理删除。

**包类型目录**：manifest 中每个实体类型的 `id`、可读 `displayName` 和数量；它必须和数据库真实类型统计一致。

## 稳定规则

- `DataPackageContract` 先检查格式、manifest/schema/content contract、必需/可选能力、语言、发布资格、质量、数据库与图片清单哈希；不支持的旧 schema 或未知必需能力明确拒绝，不回退到旧查询协议。
- `DataPackageValidator` 在 staging 包上校验数据库/清单/报告内容绑定、SQLite `quick_check` 与 `foreign_key_check`、schema 指纹、必需索引、元数据一致性、核心事实槽、状态和值组合、条件完整性、关系目标/方向/证据、补充事实状态、搜索/筛选投影及视觉清单。清单能力声明和 `package_capabilities` 按 `(capability, requirement)` 集合比较；无直接值的 `dynamic_rule` 主槽仅在同实体的 `<slot>_rule` 槽含类型化值或事实项时有效。上述结构正确性零容错；任何坏包拒绝并保留原活动包。日常内容库仍以只读方式打开。
- 压缩包先进入 staging；`SafeZipExtractor` 拒绝绝对路径、目录越界、空名称、过多条目和超限内容。当前上限为压缩包 512 MiB、解压后 1 GiB、10000 个文件。
- 导入先在 staging 目录完成解压和全语义校验；提交时按 manifest/schema/hash 隔离目录。新版普通回滚只在 `active_v5` 与已完整验证的 `previous_compatible_v5` 之间进行；首个 v5 发布前另行冻结 `pinned_legacy_v4`，仅供旧 App 整版恢复，在首个 v5 完整发布周期和旧 App 回退窗口结束前不得清理，之后也只能通过显式发布决定清理。只有新库打开且活动指针原子切换成功后才启用；任一失败恢复提交前活动状态。半迁移包不得进入业务查询。
- 已激活包的手动验证失败会关闭当前内容库、清除内存元数据；仅当上一包再次完整校验并成功打开时才退回它，否则清空活动包。验证失败的包不得继续由页面缓存或重开句柄读取。
- `DataPackageManager` 的生命周期锁包住仓储查询与目录替换；`ContentDatabaseManager` 在其内部独占当前 SQLite 句柄，并在 IO dispatcher 与互斥锁内打开/关闭。查询方不能自行持有跨切包的句柄。
- 包根目录中的图片路径必须解析后仍位于包根目录；非空图片缺失是导入失败，不以占位图掩盖损坏包。没有图片路径的实体由图鉴边界提供占位图。
- 真实发布级数据包不进入仓库。首个 v5 发布前冻结工作区外真实 v4 包并生成迁移差异报告；真实 v5 验收只接受由真实官方资产构建的显式外部文件。`RealV5PackageAcceptanceTest` 仅在显式 instrumentation 参数 `realV5Required=true` 与 `realV5PackagePath=<外部包>` 同时提供时运行，必须实际安装该包并读取每个发布分类及其首条详情；正式入口为 `STARDEW_SVDATA=<外部包> ./gradlew :app:verifyRealV5Package`，该任务会安装 APK、推送外部包并强制执行该测试。fixture、代码生成的模拟官方目录、旧 schema 包和质量失败包只能证明边界或拒绝路径，不能作为发布成功证据。
- 自动验收覆盖 API 26/30/36、320/360/411dp 手机和至少 600dp 平板、fontScale 1.0/1.3/2.0、浅色/深色及真实 v5 安装/查询/切换/回滚。候选发布另在最低能力手机、当前 API 手机和大屏设备执行，至少一台开启 TalkBack；临时使用仿真设备须在证明中标明。真实包导入/全验证 p95≤30s，活动包冷启动到可交互 p95≤2s。

## 代码锚点

- `core/datapackage/DataPackageContract.kt`：发布条件与支持 schema。
- `core/datapackage/DataPackageValidator.kt`：manifest、数据库元数据、统计和图片校验。
- `core/datapackage/SafeZipExtractor.kt`：归档路径与体积边界。
- `core/datapackage/DataPackageInstaller.kt`、`DataPackageManager.kt`：暂存提交、活动包切换、验证失败停用、回滚和清理。
- `app/src/androidTest/java/com/example/stardewoffline/core/datapackage/RealV5PackageAcceptanceTest.kt`：外部真实 v5 包的安装与全分类/详情读取验收入口。
- `core/database/content/ContentDatabaseFactory.kt`、`ContentDatabaseManager.kt`：只读数据库和句柄生命周期。
- `data/ContentRepository.kt`、`SearchRepository.kt`：在活动包生命周期租约内读取内容。
- `core/datastore/AppPreferencesRepository.kt`：活动包、上一包和最近验证标识。

活动包读取与同 ID 替换的并发边界见 [ADR 004](../adrs/004-activity-package-read-lease.md)。
