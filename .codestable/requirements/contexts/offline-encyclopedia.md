---
scope: context:offline-encyclopedia
code-paths:
  - app/src/main/java/com/example/stardewoffline/data/wiki
  - app/src/main/java/com/example/stardewoffline/core/model/WikiCatalogueModels.kt
  - app/src/main/java/com/example/stardewoffline/core/json
  - app/src/main/java/com/example/stardewoffline/core/formatter
  - app/src/main/java/com/example/stardewoffline/data
  - app/src/main/java/com/example/stardewoffline/feature
  - app/src/main/java/com/example/stardewoffline/navigation
  - app/src/main/java/com/example/stardewoffline/core/database/user
---

# 离线图鉴领域上下文

这个边界把活动内容包转换成可阅读的语义目录和图鉴条目，并管理跨内容包的本地个人连续性。

## 通用语言

**图鉴分类**：面向玩家的浏览入口，由应用内目录配置和活动包可用类型共同决定；它不是数据库的原始 `entity_type` 标签。

**图鉴条目**：供列表、搜索、详情和个人记录恢复使用的可读模型，至少包含稳定 ID、中文标题、可选英文标题、类型显示名、图片状态、摘要、事实分组和关系。

**稳定实体 ID**：`<entity_type>:<official-source-id>` 形式的跨页面和跨包键；不能只用数字源 ID。

**关系目标**：关系解析后的三种状态：可跳转的条目、可读文本、或“关联内容暂未收录”的不可用目标。

**村民支援记录**：数据包中的 `npc_schedule` 和 `villager_gift` 原始记录。它们保留在内容库中，但不是普通浏览条目；由对应 `villager` 条目聚合为日程和礼物偏好子菜单。日程键按游戏语义展示为春、夏、秋、冬四组及其日期覆盖（如 `spring_4`），季节无关日期、天气、节日和婚后规则另列；不能把每条原始记录当成无日期的普通日程。

**个人记录**：Room 中的收藏、浏览历史、笔记和最近搜索。它们通过稳定实体 ID 与活动包内容建立软引用。

## 稳定规则

- `WikiCatalogue` 是页面的内容边界。首页、分类、搜索、详情、收藏和历史使用它的模型，不直接读取 `ContentDatabase`、原始 JSON 或技术类型 ID。
- 当前主题目录只有活动包存在的用户可浏览类型才显示：`farm`（农场与物品）、`villagers`（村民）、`people`（世界与生物）和 `activities`（活动与配方）。`npc_schedule` 与 `villager_gift` 是村民支援记录，不在主题目录、全部分类或普通搜索结果中单独出现；它们由对应村民条目聚合为可展开子菜单。`all`（全部分类）按 manifest 的可读 `displayName` 列出其余有数据类型，普通界面不把原始 ID 当标题。
- 列表使用轻量摘要；详情由 `DetailPresentationParser` 按实体类型读取已确认字段和 `officialDerived`。缺失字段不推断为 `false`、`0` 或永不发生；未知字段不强行解释，游戏条件只原样说明而不求值。
- 关系解析集中在 `EntityRelationResolver`，先生成候选稳定 ID 再批量查询。无法唯一跳转时，普通关系保留可读信息或显示明确未收录状态；商店商品关系只保留能解析为已发布条目的商品，未收录的运行时/动态选项直接隐藏，不创建假实体、不向普通页面泄漏原始 ID。
- 搜索先标准化输入，再合并中文/英文前缀、别名、拼音、首字母和 FTS 命中；按实体 ID 去重并保留最高分和命中原因。已选实体类型会下推到每种 SQLite 查询，页面保留当前包完整的类型筛选入口。查询参数必须绑定，用户输入中的 LIKE/FTS 特殊字符不能改变查询语义。
- 图片只从活动包的 `image_path` 解析本地文件；路径越界或文件不存在时使用明确的本地占位，不联网、不现场裁切游戏图集。
- 收藏、历史、笔记和搜索历史不写内容库。数据包更新后保留稳定 ID 记录；当前包不存在的收藏/历史仍可见并可删除，笔记和搜索历史的连续性不依赖当前实体存在。
- 村民日程只展示包含有效时间和地点的记录；坐标、日程条件及 MAIL/GOTO 等技术指令不进入日程阅读模型，界面按时间顺序纵向排列多组“时间 / 地点”两列，组间可用向下箭头连接，日期/季节仅作为分组上下文。
- 主导航是首页、搜索、收藏、更多；数据管理从“更多”进入。最近浏览显示在搜索页的空搜索状态，不再作为首页内容块。详情 ID 进入 Navigation 前必须 URI 编码。

## 代码锚点

- `data/wiki/WikiCatalogue.kt`：目录配置、条目模型、搜索适配和关系降级。
- `core/model/WikiCatalogueModels.kt`：图鉴分类、条目、关系目标和图片状态。
- `core/database/content/ContentDatabase.kt`、`data/SearchRepository.kt`：摘要批量投影、实体类型筛选、别名、拼音和 FTS 查询及分层评分。
- `core/json/DetailPresentationParser.kt`、`core/formatter/DetailFormatters.kt`：类型事实、条件和数值的可读表达。
- `data/EntityRelationResolver.kt`：稳定 ID 候选与批量关系解析。
- `core/database/user/UserDatabase.kt`、`UserDataDao.kt`、`data/UserDataRepository.kt`：本地个人记录和软引用。
- `feature/home/HomeFeature.kt`、`feature/type/TypeListFeature.kt`、`feature/search/SearchFeature.kt`、`feature/detail/DetailScreen.kt`、`feature/favorites/FavoritesFeature.kt`、`feature/history/HistoryFeature.kt`：用户路径。
