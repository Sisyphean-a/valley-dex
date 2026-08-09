---
status: accepted
scope: context:offline-encyclopedia
date: 2026-07-18
---

# ADR 003：用语义图鉴目录隔离原始实体类型

## 背景

直接把 `entities.entity_type` 作为首页入口会把数据库内部标签、数字标题和原始字段暴露给普通用户。数据包是事实来源，但不应该承担产品信息架构。

## 决定

通过 `WikiCatalogue` 在内容查询和 Compose 页面之间建立语义边界。应用配置提供主题分类；活动包的 `entityTypes.displayName` 和数量只用于裁剪可用入口与兜底的“全部分类”。条目统一映射为可读标题、类型显示名、事实分组、关系目标和图片状态；详情只呈现已确认字段，未知字段和条件不被猜测。

## 真实备选

1. 保留原始类型列表，只更换颜色和卡片样式。
2. 用人工占位文章填满首页，绕开真实内容数据。
3. 让每个页面各自解释 SQLite 行和 `extra_json`。

当前方案才同时改变信息架构、保留真实数据来源并集中处理未来字段差异；其余方案会产生重复解析或伪造内容。

## 后果

- 页面不再依赖原始类型 ID；新类型仍可从“全部分类”发现，但专题分类要改应用配置。
- `WikiCatalogue`、详情解析器和关系解析器成为维护重点；页面只负责状态和展示。
- 目录是真实活动包的投影，包没有数据时对应入口不显示；没有图片时必须使用明确的产品占位。

## 范围与代码锚点

范围：`context:offline-encyclopedia`；实现：`data/wiki/WikiCatalogue.kt`、`core/model/WikiCatalogueModels.kt`、`core/json/DetailPresentationParser.kt`、`data/EntityRelationResolver.kt`、`feature/home/HomeFeature.kt`、`feature/type/TypeListFeature.kt`、`feature/detail/DetailScreen.kt`。

## 相关历史

见 `.codestable/history/2026-07.md` 的 Wiki 产品重构条目和 `.codestable/history/2026-08.md` 的详情可读化条目。
