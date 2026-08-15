# R0 恢复证据（schema 4 恢复线）

> 本文档记录 [RECOVERY.md](RECOVERY.md) R0“先保全，再恢复”的执行证据。
> 生成时间：2026-08-15（会话运行日）；设备：本机 Android 模拟器（AVD `vd-recovery`，Android 15 / API 35，pixel_5 配置）。

## 1. 保全（refs 与分支）

### 备份 ref（不会随提交移动）

- App（`E:\github\valley-dex`）：`refs/recovery/backup-app-schema5-HEAD` → `5cfc59ef6d080691368618ca402da2d07999fe3e`
- builder（`E:\github\stardew-offline-data-builder`）：`refs/recovery/backup-builder-schema5-HEAD` → `e1ea3394c27202774dd37e633a61b87a4c464a6c`

### schema 4 恢复分支 / worktree

- 分支：`recovery/schema4-line` @ `54acb8461dfaac4e41b8dbe07dc30da3a3bb7c4a`（迁移前最后一个代码基线）
- worktree：`E:\github\valley-dex-schema4-recovery`（工作树干净，`git status --short` 为空）
- 主工作树、master（`5cfc59e`）及其未提交用户改动原样保留；未移动、未重写、未强推任何历史。
- 两仓 `git diff --check` 干净。

## 2. 数据包（冻结已验证 v4）

- 来源：`E:\github\stardew-offline-data-builder\dist\stardew-zh-cn.svdata`（2026-08-10 冻结构建）
- 包文件 SHA-256：`33dc5591bbf20ee79e105b89e99b1232c6b8984946b54f290114ecacfe828fe1`
- 包内 `stardew.db` SHA-256：`864f7b9caf2d7252421eb29604240e7075c8b8a23466b15e5a445be02c41c16b`（与 manifest 一致，完整性校验通过）
- manifest：`schemaVersion=4`、`publishable=true`、`quality.status=passed`、游戏版本 `1.6.15.24356`、3625 实体（村民 34、作物 50、鱼 74…）

## 3. 构建与自动检查

在 `E:\github\valley-dex-schema4-recovery`：

- `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` → BUILD SUCCESSFUL（27s）
- `.\gradlew.bat :app:testDebugUnitTest` → BUILD SUCCESSFUL（全部单元测试通过）

在设备（`emulator-5554`）：

- 安装 `app-debug.apk` + `app-debug-androidTest.apk` 成功
- `RealV4PackageValidationTest.importsTheExplicitRealV4Package` → OK（1 test）
  - 参数：`-e realV4Required true -e realV4PackagePath /data/local/tmp/stardew-v4.svdata`
  - 断言：schemaVersion=4、publishable、quality=passed、可安装激活

## 4. 设备关键路径截图（人工 + Qwen-VL 复核）

截图存放：`E:\github\valley-dex\.tmp\recovery-r0\`（`.tmp` 不在 Git 跟踪范围）。

| # | 文件 | 路径 | 观察结论 |
|---|------|------|----------|
| 01 | `01-bootstrap.png` | 启动 → 引导页 | 中文引导“请选择由游戏资源生成的 .svdata 数据包”，无泄露 |
| 02 | `02-picker.png` | 系统文档选择器 | Downloads 中可见 `stardew-v4.svdata` |
| 03 | `03-after-import.png` | 首页 | “VALLEY INDEX / 农场资料库”，快捷入口 作物50/任务78/商店65/村民34，全部分类 23 类 · 3165 条，全中文 |
| 04 | `04-villagers.png` | 分类页（村民） | 34 条本地资料；4 列网格头像完整可辨认（含矮人/科罗布斯特殊造型）；中文名+英文副名；筛选标签“全部村民/不可结婚村民/可结婚女性村民” |
| 05 | `05-detail-abigail.png` | 详情页 | 头像完整；核心信息全中文：生日 秋季 13日、居住区域 鹈鹕镇、可婚配 是、别名 阿比 等 |
| 06 | `06-favorited.png` | 详情页收藏 | 收藏按钮可用 |
| 07 | `07-back-to-list.png` | 返回 → 分类列表 | 返回键回到村民列表 |
| 08 | `08-back-to-home.png` | 返回 → 首页 | 系统返回回到首页 |
| 09/10 | `09-search.png` / `10-search-jodi.png` | 搜索 tab | 输入 `jodi` → “找到 3 条结果”：乔迪（村民）、乔迪的请求（任务）、沙漠节：乔迪（商店），离线检索 |
| 11 | `11-favorites.png` | 收藏 tab | “我的收藏 · 1 条已保存资料”列出阿比盖尔 |
| 12/13 | `12-fav-to-detail.png` / `13-back-to-fav.png` | 收藏→详情→返回 | 从收藏打开详情并正确返回 |
| 14 | `14-villagers-scrolled.png` | 列表滚动 | 滚动后显示玛鲁/玛妮/潘姆，滚动可用 |

## 5. 复现命令（恢复线重建）

```powershell
# 1. 代码基线
cd E:\github\valley-dex
git worktree add E:\github\valley-dex-schema4-recovery recovery/schema4-line
# 2. 构建 APK
cd E:\github\valley-dex-schema4-recovery
.\gradlew.bat :app:assembleDebug
# 3. 数据包（冻结 v4 或 builder 重建）
#    冻结：E:\github\stardew-offline-data-builder\dist\stardew-zh-cn.svdata
#    重建：cd E:\github\stardew-offline-data-builder; python -m builder build-v4-legacy
# 4. 安装 + 验证
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb push <v4包> /data/local/tmp/stardew-v4.svdata
adb shell am instrument -w -r -e realV4Required true -e realV4PackagePath /data/local/tmp/stardew-v4.svdata `
  -e class com.example.stardewoffline.core.datapackage.RealV4PackageValidationTest `
  com.example.stardewoffline.debug.test/androidx.test.runner.AndroidJUnitRunner
# 5. UI 路径：引导页选择数据包 → 首页 → 分类 → 详情 → 搜索 → 收藏 → 返回
```

## 6. R0 完成标准核对

- [x] 可复现：分支 + gradle 命令 + 冻结包 hash 可重建
- [x] 可安装：模拟器安装成功并通过 v4 验证 instrumentation 测试
- [x] 可浏览：首页/分类/详情/搜索/收藏/返回全路径截图验证，中文界面、头像完整
- [x] schema 5 工作完整保留：master `5cfc59e` 与备份 ref 未动
- [x] 互不覆盖：恢复线使用独立 worktree 与独立 AVD 应用数据；主工作树未受影响

## 7. 残余风险与说明

- 验证设备为本机 Android 模拟器（无实体手机接入）；RECOVERY.md 要求的“真实设备”路径待有设备时按第 5 节命令复核。
- 本证据由 UI 层级 dump + Qwen-VL 图像复核得出，非人眼逐像素审阅；关键截图保留在 `.tmp\recovery-r0\` 供人工复核。
- v4 基线本身存在既有的轻微瑕疵（如详情“完美度评分：true”为布尔直出），属于 v4 发布基线行为；R0 只恢复基线，不修 v4 UI。schema 5 的对应修复由 R1–R5 完成。
