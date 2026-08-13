---
status: accepted
scope: context:content-package, context:offline-encyclopedia
date: 2026-08-13
supersedes: 002
---

# ADR 005：只激活并类型化消费 player-facts-v1 数据包

## 背景

schema 4 只提供通用实体列和 `extra_json.officialDerived`。应用需要在 `WikiCatalogue`、详情解析器和村民支援逻辑中再次解释 raw JSON，无法在激活前证明核心事实状态、条件、关系方向、逐事实来源和视觉契约完整。继续支持 v4/v5 双栈会长期保留两套语义及测试矩阵。

## 决定

新版产品只安装 `format=stardew-offline-data`、`manifestVersion=2`、SQLite `schemaVersion=5`、`contentContract=player-facts-v1` 的发布包。schema 4 是旧协议，不在设备上原地转换，也不进入新版产品的正常读取路径。

安装在 staging 中验证 manifest、数据库与报告/图片清单的内容绑定，并执行数据库 hash、schema 指纹、`quick_check`、`foreign_key_check`、必需索引、核心事实槽、状态和值组合、关系目标/方向/证据、条件完整性、补充事实状态和视觉清单检查。不支持的必需能力或任一半迁移迹象均拒绝激活；未知可选能力可以忽略。

验证成功后才原子切换活动包；失败保留原活动包。包目录按版本组合与数据库 hash 隔离，不能覆盖原包。保留位分为当前 `active_v5`、可由新版 App 完整验证后直接回滚的 `previous_compatible_v5`，以及只供旧 App 整版恢复的 `pinned_legacy_v4`。固定 v4 不进入新版 App 的普通回滚入口，也不被普通包清理删除；首个 v5 完整发布周期和旧 App 回退窗口结束后仍需显式发布决定才能清理。旧 App 不能打开 v5，新 App 不能把 v4 作为内容查询回退。

`WikiCatalogue` 继续作为页面唯一内容边界，但 schema 5 的 repository 和 DTO 只读取类型化事实、关系、条件、来源摘要、视觉状态、`entity_cards` 和 `browse_facets`。`extra_json`、`officialDerived` 和 `legacyFields` 不再属于公共读取 API；如旧解析代码暂留，只能隔离为不被新版产品调用的 v4 adapter。

## 真实备选

1. 同一 app 长期双栈支持 v4/v5：迁移平滑，但每个页面和查询都承担双语义风险。
2. 在设备上将 v4 原地转换成 v5：v4 缺少状态、条件完整性和逐事实证据，只能猜测。
3. 保留 raw JSON 作为新 DTO 的回退：会绕过 builder 契约与安装门禁，再次形成双事实源。

## 后果

- 内容数据库 repository、验证器、DTO、详情表达、搜索和筛选需要一次破坏性迁移。
- 列表与搜索使用预计算且经一致性校验的 `entity_cards` / `browse_facets`，详情按需读取规范事实与关系，不加载完整证据图。
- 首个 v5 发布前冻结真实 v4 包，并检查实体 ID、核心事实状态、关系、视觉和分类覆盖差异；当前实现只有 active/previous，必须迁移为 `active_v5`、`previous_compatible_v5` 和 `pinned_legacy_v4` 三种生命周期。
- v5 语义缺陷通过完整新包或更高 schema 修复，不在运行时恢复 `officialDerived` 解析。

## 范围与代码锚点

范围：`context:content-package`、`context:offline-encyclopedia`；实现锚点：`core/datapackage/DataPackageContract.kt`、`DataPackageValidator.kt`、`DataPackageInstaller.kt`、`DataPackageManager.kt`、`core/database/content/ContentDatabase.kt`、`data/wiki/WikiCatalogue.kt`、`core/model/WikiCatalogueModels.kt`。

## 相关历史

见 `.codestable/history/2026-08.md` 的 player-facts-v1 契约条目，以及 `.wayfinding/player-first-encyclopedia/decisions/09-cross-repo-data-contract.md`。
