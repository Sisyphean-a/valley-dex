---
doc_type: functional-acceptance
goal: "stardew-offline-android"
verdict: pass
updated_at: "2026-07-18"
---

# 功能验收

独立验收结论：PASS。

- Release APK 已在 API 35 模拟器安装并启动，Debug 和 Release 均使用内置真实 `.svdata`。
- 数据包为 schema 2、`zh-CN`、3688 条实体；校验覆盖 SHA、`quick_check`、元数据、图片及无空格拼音查询。
- 模拟器实测英文 `parsnip` 和拼音 `fangfengcao` 均返回“防风草 / Parsnip”，可进入详情页查看真实字段和关联数据。
- 正常内容查询保持只读；仅校验临时副本以满足 SQLite FTS `quick_check` 的写入需求。
- Debug/Release 构建、单元测试、Lint、仪器测试和基准模块构建全部成功。
