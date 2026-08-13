---
scope: workspace
---

# 架构索引

单仓 Android 项目，生产能力集中在 `:app`，性能基线与宏基准测试位于 `:baselineprofile`。应用以本地发布数据包提供内容，以本地用户数据库保存个人记录。

## 实现范围

- [package:app](packages/app.md)：应用启动、数据包生命周期、只读内容查询、语义图鉴、个人数据、Compose 页面和导航。代码位于 `app/`。
- [package:baselineprofile](packages/baselineprofile.md)：面向 `:app` 的 Baseline Profile 与 Macrobenchmark，不承载业务事实。代码位于 `baselineprofile/`。

## 领域入口

- [context:content-package](../requirements/contexts/content-package.md)：manifest 2 / schema 5 / `player-facts-v1` 发布契约、校验、激活和回滚；见 [ADR 005](../requirements/adrs/005-player-facts-v1-package-and-consumption.md)。
- [context:offline-encyclopedia](../requirements/contexts/offline-encyclopedia.md)：语义目录、条目阅读、搜索、关系和个人连续性。
- [workspace 作用域地图](../requirements/CONTEXT.md)：项目共同的离线、隐私和数据边界。

## 依赖方向

`feature` / `navigation` 只通过 `data/wiki`、数据仓储和状态模型获得业务结果；数据仓储向下依赖 `core` 的 SQLite、Room、DataStore、数据包和 JSON 解析；Hilt 在 `di/` 装配单例对象。页面不直接拼 SQL、打开 SQLite、解压包或操作 DAO。

## 主要运行入口

- `StardewOfflineRoot`：先处理当前包/导入状态，再进入主导航。
- `AppNavHost`：四个主入口为首页、搜索、收藏、更多；分类和详情使用 URI 编码后的路由参数。
- `WikiCatalogue`：页面读取语义分类、条目、搜索命中和关系目标的应用内边界。
- `DataPackageManager`：数据包安装、激活、验证、回滚和旧包清理的生命周期边界。
