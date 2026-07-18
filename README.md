# 星露谷离线图鉴

完全离线的 Android 图鉴。它只读取用户导入的发布级 schema 4 `.svdata` 数据包，不请求网络、不读取存档，也不修改内容数据库。

## 构建

需要 Android SDK API 36 与 Java 17。Windows 下执行：

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
```

安装包位于 `app/build/outputs/apk/debug/app-debug.apk`；可安装的压缩发布包位于 `app/build/outputs/apk/release/app-release.apk`。发布包当前用本机 debug 签名，只适合验证，正式发布必须替换为私有签名。

## 数据包

手机中首次启动可直接选择 `.svdata` 或 ZIP 文件导入。应用只接受 `schemaVersion=4`、`publishable=true` 且质量通过的数据包；会校验 manifest、SHA-256、SQLite `quick_check`、build_meta/artifact_metadata、实体类型目录、实体数量与已声明图片。失败不会覆盖当前可用数据。

若需要以工作区外的真实数据包执行设备验收，设置 `STARDEW_SVDATA` 后运行：

```powershell
$env:STARDEW_SVDATA = 'D:\path\to\stardew-zh-cn.svdata'
.\gradlew.bat verifyRealV4Package
```

## 版权与隐私

本项目是非官方工具，与 ConcernedApe、发行商或 Wiki 没有从属关系。游戏名称、角色和素材归其权利人所有。应用不联网、不做分析、不上传收藏、笔记或搜索内容。
