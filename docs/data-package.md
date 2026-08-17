# 数据包导入

`.svdata` 是包含 `manifest.json`、`stardew.db`、本地图片和报告的 ZIP。应用只支持 `format=stardew-offline-data`、`manifestVersion=2`、`schemaVersion=5`、`contentContract=player-facts-v1`、`language=zh-CN`、`publishable=true` 且质量通过的数据包；schema 4 不能作为新版图鉴内容导入。

导入时限制压缩包 512 MiB、解压内容 1 GiB、文件数 10000，并拒绝绝对路径和目录越界。随后在暂存目录校验清单与报告内容绑定、SHA-256、SQLite `quick_check` 与 `foreign_key_check`、schema 指纹、类型化事实、关系、条件和图片；成功后才激活，失败保留原活动包。

当前发布边界、激活与回滚规则见 [内容数据包领域上下文](../.codestable/requirements/contexts/content-package.md)。
