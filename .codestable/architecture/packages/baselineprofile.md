---
scope: package:baselineprofile
code-paths:
  - baselineprofile/src/main
  - baselineprofile/build.gradle.kts
---

# `:baselineprofile` 性能验证包

## 职责

`:baselineprofile` 是 Android 测试包，目标项目为 `:app`。它只记录启动、搜索、分类浏览和详情等关键路径的基线配置，并承载宏基准测试入口；不拥有图鉴领域规则，也不参与生产依赖注入。

## 边界与锚点

- `baselineprofile/build.gradle.kts` 将 `targetProjectPath` 指向 `:app`，使用 Java/Kotlin 17。
- `baselineprofile/src/main/java` 是性能路径定义位置；页面和数据包契约的变化应通过 `:app` 的真实行为验证，不在此包复制规则。
- `app/src/benchmarkRelease/assets/default-data/stardew-benchmark-fixture.svdata` 是仅随 benchmarkRelease 打包的合成 schema 4 包；它固定提供“作物”和“萝卜种子”，不含真实游戏或玩家数据。测试在采样前清除该变体数据并走普通内置包导入，随后测量冷启动、分类内筛选和详情路径。

## 验证

构建基线包使用 `./gradlew.bat :baselineprofile:assembleBenchmarkRelease`；执行设备基准使用 `./gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest`。性能数字需要真实设备或可启动模拟器；没有设备时只能记录编译结果，不能宣称性能验收完成。
