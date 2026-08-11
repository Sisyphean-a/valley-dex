---
status: accepted
scope: workspace
date: 2026-07-18
---

# ADR 001：内容库只读，个人数据独立保存

## 背景

`stardew.db` 是外部生成、随数据包整体替换的内容库；收藏、历史和最近搜索属于应用自己的用户状态。把两类数据写进同一库会让内容更新、回滚和用户数据迁移互相耦合。

## 决定

内容库用 Android `SQLiteDatabase` 只读打开，并由 `ContentDatabaseManager` 独占句柄；收藏、历史和最近搜索使用独立的 Room `user.db`。用户表中的实体 ID 是软引用，内容包更新不自动删除记录。

## 真实备选

1. 用 Room 接管外部内容库并随包做模式迁移。
2. 把收藏、历史和最近搜索直接写入 `stardew.db`。
3. 维持双库，但允许页面直接操作 SQLite 或 DAO。

选择当前方案，因为外部库不属于应用的迁移责任，双库能让包级切换和用户状态独立；页面直接越过仓储会泄漏句柄生命周期和数据边界。

## 后果

- 更换活动包前必须关闭旧内容库；失败时可以恢复上一包而不损失用户记录。
- 收藏、历史需要在 UI 层处理当前包缺失实体，但不会因实体删除而静默丢失；最近搜索不依赖实体存在。
- 查询、解压、哈希和数据库打开均需走 IO dispatcher；测试要覆盖真实 SQLite 与 Room 边界。

## 范围与代码锚点

范围：`workspace`；实现：`core/database/content/ContentDatabaseFactory.kt`、`ContentDatabaseManager.kt`、`core/database/user/UserDatabase.kt`、`UserDataDao.kt`、`data/UserDataRepository.kt`。

## 相关历史

见 `.codestable/history/2026-07.md` 的 Android 初始交付条目。
