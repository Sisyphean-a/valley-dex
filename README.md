# 星露谷离线图鉴

完全离线的 Android 数据查询工具。它只读取用户导入的 schema 2 `.svdata` 数据包，不请求网络、不读取存档，也不修改内容数据库。

## 构建

需要 Android SDK API 36 与 Java 17。Windows 下执行：

```powershell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
```

安装包位于 `app/build/outputs/apk/debug/app-debug.apk`；可安装的压缩发布包位于 `app/build/outputs/apk/release/app-release.apk`。发布包当前用本机 debug 签名，只适合验证，正式发布必须替换为私有签名。

## 数据包

可将个人生成的 `stardew-zh-cn.svdata` 放到 `app/src/main/assets/default-data/`，构建时会自动内置。该目录已忽略，真实游戏数据不会提交。

手机中首次启动可直接选择 `.svdata` 或 ZIP 文件导入。应用会校验 manifest、SHA-256、schema、SQLite `quick_check`、元数据与实体数量；失败不会覆盖当前可用数据。

## 版权与隐私

本项目是非官方工具，与 ConcernedApe、发行商或 Wiki 没有从属关系。游戏名称、角色和素材归其权利人所有。应用不联网、不做分析、不上传收藏、笔记或搜索内容。
