# 项目注意事项

## 报告语言

项目记忆正文使用中文；YAML、JSON 和 frontmatter 的机器字段保持原样。

## 按范围加载

先读本文件和 `architecture/INDEX.md`，再按改动路径读取对应的包页、`requirements/CONTEXT.md` 及直接相关的领域上下文。不要默认遍历历史或旧过程资料。

## 长期约束

- 应用完全离线：不联网、不读取玩家存档、不上传收藏、历史或搜索内容。
- 内容数据包只接受当前支持的 manifest 2 / schema 5 / `player-facts-v1` 发布级数据包；`stardew.db` 只读，收藏、历史和搜索历史写入独立的 Room 数据库。
- 不提交真实 `.svdata`、游戏图片或签名密钥；未知字段、条件和关系不得被猜测成游戏结论。
- 真实 schema 5 成功包来自工作区外；需要真实包时显式设置 `STARDEW_SVDATA`，不能用 fixture 冒充发布验收。

## 验证入口

- `./gradlew.bat assembleDebug`
- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat lintDebug`
- 有设备时执行 `./gradlew.bat connectedDebugAndroidTest`
- 真实包验收执行 `./gradlew.bat :app:verifyRealV5Package`，并先设置 `STARDEW_SVDATA`。

完整设备测试未通过前，不把编译、单测或部分仪器测试写成最终功能验收通过。
