---
doc_type: feature-ff-note
feature: move-recent-browsing
date: 2026-07-18
requirement:
tags: [home, search, ux]
---

## 做了什么

主页不再展示最近浏览；最近 5 条浏览记录改为仅在搜索页的空搜索状态显示。

## 改了哪些

- `HomeFeature.kt` — 移除主页对浏览历史的订阅和展示。
- `SearchFeature.kt` — 搜索页展示最近浏览，并保留原有最近搜索。
- `RouteNavigationTest.kt` — 覆盖主页隐藏、搜索页展示最近浏览的行为。

## 怎么验证的

构建通过，模拟器界面用例 5/5 通过；实体机确认主页没有最近浏览、搜索页显示最近浏览。
