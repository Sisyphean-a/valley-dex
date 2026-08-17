# 发布

1. 替换 `applicationId` 为唯一包名，并配置私有签名。
2. 确认发布包不包含真实 `.svdata`、游戏图片或签名密钥。
3. 执行 `./gradlew.bat assembleRelease lintDebug testDebugUnitTest`。
4. 使用工作区外的真实 schema 5 包执行 `STARDEW_SVDATA=<路径> ./gradlew.bat :app:verifyRealV5Package`。
5. 在真实设备完成仪器测试及当前 [内容数据包领域上下文](../.codestable/requirements/contexts/content-package.md) 要求的响应式、无障碍和性能验收。
