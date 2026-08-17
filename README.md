# 星露谷离线图鉴

完全离线的 Android 图鉴。它只读取用户导入的 manifest 2 / schema 5 / `player-facts-v1` 发布级 `.svdata` 数据包，不请求网络、不读取存档，也不修改内容数据库。

## 构建

需要 Android SDK API 36 与 Java 17。Windows 下执行：

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
```

安装包位于 `app/build/outputs/apk/debug/app-debug.apk`；可安装的压缩发布包位于 `app/build/outputs/apk/release/app-release.apk`。发布包当前用本机 debug 签名，只适合验证，正式发布必须替换为私有签名。

## 数据包

手机中首次启动可直接选择 `.svdata` 或 ZIP 文件导入。应用只接受 `format=stardew-offline-data`、`manifestVersion=2`、`schemaVersion=5`、`contentContract=player-facts-v1`、`language=zh-CN`、`publishable=true` 且质量通过的数据包；导入会在暂存目录校验清单、内容绑定、数据库完整性、类型化事实、关系、条件与图片。失败不会覆盖当前可用数据。

若需要以工作区外的真实数据包执行设备验收，设置 `STARDEW_SVDATA` 后运行：

```powershell
$env:STARDEW_SVDATA = 'D:\path\to\stardew-zh-cn.svdata'
.\gradlew.bat :app:verifyRealV5Package
```

数据包约束见 [数据包导入](docs/data-package.md)，发布检查见 [发布](docs/release.md)。

## 版权与隐私

本项目是非官方工具，与 ConcernedApe、发行商或 Wiki 没有从属关系。游戏名称、角色和素材归其权利人所有。应用不联网、不做分析、不上传收藏、历史或搜索内容。
