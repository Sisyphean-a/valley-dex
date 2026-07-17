# 数据包导入

`.svdata` 是包含 `manifest.json`、`stardew.db`、本地图片和报告的 ZIP。应用只支持 `format = stardew-offline-data`、`schemaVersion = 2`、`language = zh-CN`。

导入时限制压缩包 512 MiB、解压内容 1 GiB、文件数 10000，并拒绝绝对路径和目录越界。随后校验数据库 SHA-256、`PRAGMA quick_check`、`build_meta`、实体数量和搜索索引数量。

完整字段契约见 `.codestable/requirements/database-reference.md`。
