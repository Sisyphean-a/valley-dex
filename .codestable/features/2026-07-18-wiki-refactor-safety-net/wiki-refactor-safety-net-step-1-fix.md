---
doc_type: implementation-step-fix
feature: 2026-07-18-wiki-refactor-safety-net
step: S1
attempt: 2
status: active
updated_at: 2026-07-18
---

# S1 窄修复：设备夹具目录

## 失败标准

API 35 instrumentation 环境中，测试 APK 的内部 `cache`、`files` 和 device-protected 目录均无法作为 `java.nio.file.Files.createTempDirectory` 的父目录，导致合成包未生成。

## 允许范围

只修改测试夹具的临时存储位置及其创建方式；不修改生产数据包、导入、校验、Room、测试断言或路由逻辑。

## 必跑验证

`connectedDebugAndroidTest` 仅运行 `SyntheticDataPackageTest`，随后再恢复全部安全网设备测试。
