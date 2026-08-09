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

**发布级数据包**：满足应用当前发布契约、可被激活的包；当前只支持 `format=stardew-offline-data`、`schemaVersion=4`、`language=zh-CN`、`publishable=true`，且质量状态为 `passed`、数据错误和缺失/无效翻译计数均为零。

**活动包**：Preferences DataStore 中 `active_package_id` 指向的、当前供内容查询使用的包。`previous_package_id` 只保存可回滚的上一包。

**包类型目录**：manifest 中每个实体类型的 `id`、可读 `displayName` 和数量；它必须和数据库真实类型统计一致。

## 稳定规则

- `DataPackageContract` 先检查格式、schema、语言、发布资格、质量、数据库路径和 SHA-256 格式；不支持的旧 schema 明确拒绝，不回退到旧查询协议。
- `DataPackageValidator` 在 staging 包上校验数据库哈希、SQLite `quick_check`、`build_meta`、`artifact_metadata`、实体总数、类型目录、搜索索引数量和非空图片路径。`quick_check` 使用临时可写副本；日常内容库仍以只读方式打开。
- 压缩包先进入 staging；`SafeZipExtractor` 拒绝绝对路径、目录越界、空名称、过多条目和超限内容。当前上限为压缩包 512 MiB、解压后 1 GiB、10000 个文件。
- 导入先在 staging 目录完成解压和完整校验；提交时临时保留同 ID 的旧目录，只有新库打开且旧备份清理完成才删除它。提交、打开或该替换备份清理失败都会恢复提交前的活动/上一包偏好和目录；无关旧包的后续清理失败会显式记录日志，但不伪装成新包启用失败。
- 已激活包的手动验证失败会关闭当前内容库、清除内存元数据；仅当上一包再次完整校验并成功打开时才退回它，否则清空活动包。验证失败的包不得继续由页面缓存或重开句柄读取。
- `DataPackageManager` 的生命周期锁包住仓储查询与目录替换；`ContentDatabaseManager` 在其内部独占当前 SQLite 句柄，并在 IO dispatcher 与互斥锁内打开/关闭。查询方不能自行持有跨切包的句柄。
- 包根目录中的图片路径必须解析后仍位于包根目录；非空图片缺失是导入失败，不以占位图掩盖损坏包。没有图片路径的实体由图鉴边界提供占位图。
- 真实发布级 schema 4 包不进入仓库。`verifyRealV4Package` 只接受显式 `STARDEW_SVDATA` 文件；不可发布 fixture、旧 schema 包和质量失败包只能用于拒绝路径测试。

## 代码锚点

- `core/datapackage/DataPackageContract.kt`：发布条件与支持 schema。
- `core/datapackage/DataPackageValidator.kt`：manifest、数据库元数据、统计和图片校验。
- `core/datapackage/SafeZipExtractor.kt`：归档路径与体积边界。
- `core/datapackage/DataPackageInstaller.kt`、`DataPackageManager.kt`：暂存提交、活动包切换、验证失败停用、回滚和清理。
- `core/database/content/ContentDatabaseFactory.kt`、`ContentDatabaseManager.kt`：只读数据库和句柄生命周期。
- `data/ContentRepository.kt`、`SearchRepository.kt`：在活动包生命周期租约内读取内容。
- `core/datastore/AppPreferencesRepository.kt`：活动包、上一包和最近验证标识。

活动包读取与同 ID 替换的并发边界见 [ADR 004](../adrs/004-activity-package-read-lease.md)。
