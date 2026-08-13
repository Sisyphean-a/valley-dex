---
status: superseded
superseded-by: 005
scope: context:content-package
date: 2026-07-18
---

# ADR 002：只激活发布级 schema 4 数据包

> 本决定已由 [ADR 005：只激活并类型化消费 player-facts-v1 数据包](005-player-facts-v1-package-and-consumption.md) 替代。下文保留当时决定。

## 背景

初始 Android 交付曾消费 schema 2 包；后续构建器发布契约改为 schema 4，并增加发布资格、质量、类型目录和 artifact metadata。继续兼容旧成功路径会让页面无法判断字段和质量边界。

## 决定

应用只接受 `format=stardew-offline-data`、`schemaVersion=4`、`language=zh-CN`、`publishable=true`、质量通过且翻译/数据错误为零的包。导入时验证 manifest、数据库 SHA-256、`quick_check`、`build_meta`、`artifact_metadata`、实体与类型统计、搜索索引和图片路径；验证成功后才原子切换活动包，失败恢复原活动包。

## 真实备选

1. 继续成功导入 schema 2，并在页面兼容两套字段。
2. 只校验 manifest，激活后再让查询暴露错误。
3. 接受质量失败包并用页面占位或猜测继续展示。

不选这些方案：它们会把外部契约变化隐藏在 UI 中，或者在内容不完整时覆盖当前可用包。

## 后果

- schema 2、不可发布 fixture 和质量失败包只能作为明确拒绝测试样本。
- 真实 schema 4 成功验收依赖工作区外的 `STARDEW_SVDATA`，不能由仓库 fixture 代替。
- 需要维护包类型目录与数据库统计的一致性；新增类型可被“全部分类”发现，但产品专题分类需在应用配置中显式加入。

## 范围与代码锚点

范围：`context:content-package`；实现：`core/datapackage/DataPackageContract.kt`、`DataPackageValidator.kt`、`DataPackageManager.kt`、`SafeZipExtractor.kt`、`app/build.gradle.kts` 的 `verifyRealV4Package`。

## 相关历史

旧 schema 2 规范和 `wiki-product-refactor` 路线图已迁移为历史证据；见 `.codestable/history/2026-07.md`。
