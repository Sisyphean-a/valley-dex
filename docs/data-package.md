# 数据包导入

`.svdata` 是包含 `manifest.json`、`stardew.db`、本地图片和报告的 ZIP。应用只支持 `format = stardew-offline-data`、`schemaVersion = 4`、`language = zh-CN`、`publishable = true` 且质量通过的数据包。

导入时限制压缩包 512 MiB、解压内容 1 GiB、文件数 10000，并拒绝绝对路径和目录越界。随后校验数据库 SHA-256、`PRAGMA quick_check`、`build_meta`、`artifact_metadata`、实体数量、类型目录与搜索索引数量。

完整字段契约见 [`database-reference.md`](database-reference.md)。应用侧的当前发布边界、激活与回滚规则见 [项目记忆中的内容数据包领域上下文](../.codestable/requirements/contexts/content-package.md)。
