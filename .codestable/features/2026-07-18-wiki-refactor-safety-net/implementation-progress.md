---
doc_type: implementation-progress
feature: 2026-07-18-wiki-refactor-safety-net
status: complete
updated_at: 2026-07-18
---

# 实施进度

## S1 测试夹具骨架

- 已新增运行时生成的 A/B `.svdata` 测试包：包含 manifest、SQLite、FTS4、1×1 WebP、拼音、别名和作物到收获物关系。
- 已新增八类单点损坏样本，并按现有校验器错误分类写入设备测试。
- 已新增显式生产对象图：真实导入、内容库、仓储、内存 Room、偏好清理、内容目录清理及每 case 独立的 ViewModel store/factory。
- API 35 模拟器 `wiki-api35` 已执行完整 `connectedDebugAndroidTest`：18 项全部通过，覆盖 S1–S6。
- `./gradlew.bat testDebugUnitTest lintDebug assembleDebug` 已通过；`git diff --check` 已通过。
- 测试 APK 只提供无默认数据包的 assets，临时导入和状态则使用目标 APK 的可写沙箱，确保不依赖本地真实 `.svdata`。
