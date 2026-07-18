---
doc_type: feature-design-review
feature: 2026-07-18-wiki-refactor-safety-net
status: passed
review_state: passed
review_reason: ""
reviewer_id: /root/safety_net_design_round6
reviewed: 2026-07-18
round: 6
---

# wiki-refactor-safety-net feature design 审查报告

## 1. Scope And Inputs

- Design: `wiki-refactor-safety-net-design.md`
- Checklist: `wiki-refactor-safety-net-checklist.yaml`
- Roadmap: `.codestable/roadmap/wiki-product-refactor/`
- Related docs: `stardew-offline-android.md`、`database-reference.md`
- Code facts checked: 数据包管理、内容库、搜索、收藏、历史、详情及现有 Android 测试。

### Independent Review

- Status: completed
- Detection: independent-agent
- Provider / agent: `/root/safety_net_design_review`
- Raw output: 发现个人数据范围、成功切包回滚、夹具契约、UI 装配和 schema 2 过渡约束问题。
- Merge policy: 主线程已按现有代码和需求逐条核验。
- Gate effect: blocking，修订后需重新独立审查。

## 2. Design Summary

- Goal: 用真实本地 Android 边界建立可重复安全网，不依赖真实游戏资源。
- Steps: 6；包生命周期与个人连续性是风险热点。

## 3. Findings

### blocking

- [x] FDR-001 `design §2.2/§3；checklist S5` 将笔记与搜索历史纳入“缺失实体可见可删除”。
  - Evidence: 当前仅收藏、历史有缺失实体 UI；笔记只从存在实体详情读取，搜索历史并不关联实体。
  - Impact: 原验收无法在不改变生产行为的前提下成立。
  - Fix: 拆为收藏/历史缺失清理、笔记稳定 ID 读写、搜索历史独立连续性。
- [x] FDR-002 `design §2.4/§3；checklist S2` 漏掉成功 A→B 后回滚 A。
  - Evidence: `DataPackageManager.rollback()` 已以 previous package 切换，项目需求也列出此路径。
  - Impact: 现有关键回滚行为没有回归保护。
  - Fix: 加入 A→B→A 包 ID 与实体读取验收。

### important

- [x] FDR-003 `design §2.1` 夹具契约不够实施化。已补齐包结构、A/B 差异、失败样本和预期错误分类。
- [x] FDR-004 `design §2.2` App UI 场景装配与同步机制不明确。已补齐同应用上下文初始化/清理和语义等待约束。
- [x] FDR-005 `design §1` 未明确 schema 2 成功样本的移交责任。已明确由 schema4-package-contract 替换该过渡基线。

### nit

- [x] FDR-006 Matrix 的 S1–S6 未与清单和来源精确对应。已补齐 step ID 和 design 节引用。

### suggestion

- [x] 加入 `assembleDebug` 作为支持性编译验证。

### learning

- schema 2 安全网只能证明当前本地行为，绝不构成 schema 4 发布资格证据。

### praise

- 真实导入、SQLite、Room 与 Compose 路径优先，且拒绝成功开关和网络 mock。

## 4. User Review Focus

- 本轮修订扩大了测试夹具与验收的可执行细节，需完整独立复审后再交给用户统一确认。

## 5. Evidence Confidence Ledger

| Check | Verdict | Evidence Class | Basis | Follow-up |
|---|---|---|---|---|
| Acceptance Coverage Matrix | revised | E | design §3 与 checklist S1–S6 已更新 | full re-review |
| DoD Contract | revised | E | 新增 CMD-004 | full re-review |
| Steps and checks traceability | revised | E | step ID 与 source 已补齐 | full re-review |
| Roadmap contract compliance | revised | E/C | schema 2 过渡责任已明确 | full re-review |
| Module interface design | pass | C | 未新增生产接口 | full re-review |
| Validation and artifacts | revised | E | 场景装配和清理已明确 | full re-review |

Summary: E=5, C=1, H=0, H-only core checks=none。

## 6. Residual Risk

- 合成 schema 2 包不验证真实 schema 4 包，后续条目必须用显式真实包验收消除该风险。

## 7. Verdict

- Status: changes-requested
- Next: 第 3 轮发现实质缺口，修订后需再次独立复审。

## 8. Focused Closure

- none；本轮改变验收语义和范围，必须完整复审。

## 9. Round 2 Findings

### blocking

- [x] FDR-007 `design §2.1/S1/S3` 未固定当前 SQLite 夹具的列、FTS 和计数契约。已补 manifest、7 个 build_meta 键、详情列、FTS4 列、1:1 搜索行、A/B 和关系样本。
- [x] FDR-008 `design §2.2/S1/S4` 未选定真实应用的 DI 与 Activity 生命周期。已采用 Hilt Android Test，固定注入、准备、启动、语义等待、关闭和清理顺序。

### important

- [x] FDR-009 缺失数据库的预期错误分类错误。已改为 `InvalidManifest`，并分别列明打开失败和数据库损坏的分类。
- [x] FDR-010 首页到列表没有稳定 UI selector。已允许唯一的不可见首页 test tag，并明确由 `wiki-semantic-home` 替换。

### Residual Risk

- Hilt 依赖、runner、合成 SQLite DDL 和 test tag 都是本轮新增的可执行契约，必须完整独立复审后才可定稿。

## 10. Round 3 Findings

### blocking

- [x] FDR-011 `design §2.2/S1/S4` Hilt、ActivityScenario 与 Compose selector 的启动顺序未闭合。已改为在 `setContent` 前装配真实生产对象图并直接挂载 Route，不启动受 Bootstrap 约束的 MainActivity。
- [x] FDR-012 `design §2.2` 删除已注入的单例 `user.db` 会破坏 Room 生命周期。已改为每例独立内存 Room，teardown 只关闭实例。

### important

- [x] FDR-013 拼音、首字母和 FTS 专用命中样本不完整。已固定四个实体、pinyin/initials 与专用关键词，并纳入 S3。
- [x] FDR-014 FTS 错误分类不精确。已区分缺表校验失败与普通表在查询时的 `DatabaseQueryFailed`。
- [x] FDR-015 Hilt runner/依赖不完整。改用显式生产对象图后不再需要该配置。

### Residual Risk

- Route 级 Compose 测试验证现有 ViewModel 和导航回调，不等同于从 Bootstrap 到 AppNavHost 的完整启动验收；后者留给最终真机验收。

## 11. Round 4 Findings

### blocking

- [x] FDR-016 Route 对象图缺 SavedStateHandle 与 ViewModel 生命周期。已固定 type/id 参数种子、每 case ViewModelStore 与先清 composition 再 clear store 的 teardown。
- [x] FDR-017 无包 UI 未覆盖 Bootstrap。已纳入真实 BootstrapRoute，只断言 NeedDataPackage 与选择按钮可达，不驱动系统选择器。
- [x] FDR-018 损坏 SQLite 的 `DatabaseOpenFailed` 不稳定。已从 archive 夹具承诺中移除，改为独立 factory 失败观察。

### important

- [x] FDR-019 首页 tag 已固定为唯一的 `home-category:<type>`，仅挂在可点击分类行。
- [x] FDR-020 Route 直接挂载缺主题和偏好 provider。已明确复用现有 Theme 与 LocalAppPreferences。

### Residual Risk

- 本条只验证 Route 回调级导航与 Bootstrap 无包可达性；完整 Activity 启动和文件选择意图由最终真机验收覆盖。

## 12. Round 5 Findings

- [x] FDR-021：所有 Route ViewModel（含 Bootstrap）改由同一 `ViewModelProvider`/store 托管，teardown 先清 composition 并收束，再 clear store。
- [x] FDR-022：无包场景改用已验证无默认包的 androidTest APK context，不依赖开发机 target APK 资产。
- 本轮修订影响场景生命周期，需重新独立复审后才能定稿。

## 13. Round 6 Verdict

- 独立复审通过：无 blocking 或 important。
- 已核验 androidTest context、唯一 ViewModelStore、SavedStateHandle 和 teardown 与现有接口一致。
