# 发布

1. 替换 `applicationId` 为唯一包名。
2. 配置私有签名，移除开发用 debug 签名。
3. 确认是否在本地放入默认 `.svdata`，该资源不能提交。
4. 执行 `./gradlew.bat assembleRelease lintDebug testDebugUnitTest`。
5. 在真实设备安装 `app-release.apk`，导入数据包并运行仪器测试。
