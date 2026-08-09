---
status: accepted
scope: context:content-package
date: 2026-08-09
---

# ADR 004：活动包替换与内容读取共用生命周期租约

## 背景

内容库句柄锁只能防止正在使用的 SQLite 句柄被关闭。若同一包 ID 被重新导入，目录在旧包备份与新包提交之间会短暂不存在；没有覆盖查询和目录替换的共同锁，新的内容查询仍可能按旧偏好重开目录，导致瞬时失败或读到错误版本。

## 决定

`DataPackageManager` 是活动包生命周期的唯一锁所有者。内容与搜索仓储必须在该锁的活动包租约内再使用 `ContentDatabaseManager` 的句柄锁；嵌套图鉴调用复用可重入租约，进入中的子读取在外层读取结束前被排空，不能重复等待非可重入的生命周期锁。

导入在 staging 目录完成解压和完整校验，只有提交阶段持有生命周期锁。替换同 ID 目录时先保留旧目录备份；新库能打开后即完成切换，备份或无关旧包删除失败会明确记录日志而不把已成功的新包切换伪装成失败。提交或打开失败仍恢复目录和导入前的活动/上一包偏好。手动验证失败立即停止该包，只有上一包再次完整校验和打开成功时才回退。

## 真实备选

1. 只依赖 `ContentDatabaseManager` 的句柄锁。
2. 同 ID 替换时允许短暂无目录，并让页面重试。
3. 直接覆盖活动目录中的单个文件。

不选这些方案：它们不能把目录、偏好和句柄视为一个状态，且会隐藏导入中的一致性错误或暴露混合版本。

## 后果

- 切换时短暂串行化内容读取，但耗时的复制、解压和验证仍在锁外完成。
- `ContentRepository` 和 `SearchRepository` 不能绕过生命周期租约直接使用内容数据库。
- 包校验失败不再保留旧缓存/已开句柄可读；用户会回到可验证的上一包或导入入口。

## 范围与代码锚点

范围：`context:content-package`；实现：`core/datapackage/DataPackageInstaller.kt`、`DataPackageManager.kt`、`core/database/content/ContentDatabaseManager.kt`、`data/ContentRepository.kt`、`data/SearchRepository.kt`。

## 相关历史

见 `.codestable/history/2026-08.md` 的性能与包生命周期条目。
