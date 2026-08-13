---
scope: workspace
---

# 领域上下文

工作区提供一个完全离线的《星露谷物语》中文图鉴：用户导入发布级数据包，应用按玩家可理解的分类浏览和搜索内容，并在数据包更新后保留本地个人记录。

## 作用域

- [context:content-package](contexts/content-package.md)：`.svdata` 的发布资格、完整性校验、活动包切换和回滚。代码位置：`app/src/main/java/com/example/stardewoffline/core/datapackage`、`core/database/content`。
- [context:offline-encyclopedia](contexts/offline-encyclopedia.md)：语义分类、图鉴条目、搜索、关系阅读和个人连续性。代码位置：`app/src/main/java/com/example/stardewoffline/data/wiki`、`core/json`、`data`、`feature`、`navigation`、`core/database/user`。

## 通用语言

**离线图鉴**：只使用本地活动数据包提供游戏内容、使用本地页面组织阅读的 Android 应用；它不是联网 Wiki，也不读取玩家存档。

**内容数据包**：用户提供的、可被应用验证并整体激活的 `.svdata` 文件；目标协议是 manifest 2 / schema 5 / `player-facts-v1`，内容事实来自包的类型化契约，不由页面、raw JSON 回退或应用硬编码生成。

## 稳定规则

- 应用不声明网络能力，不上传任何收藏、历史或搜索内容。
- `stardew.db` 是外部内容库，只读且不可写入用户数据；用户数据由独立的 Room 数据库拥有。
- 不提交真实游戏数据、图片、默认包或签名密钥；测试夹具只证明应用边界，不代表真实发布数据。
- 未知字段、游戏条件和未解析关系只能按存在性或可读降级展示，不能猜测成攻略结论或运行时判断。
