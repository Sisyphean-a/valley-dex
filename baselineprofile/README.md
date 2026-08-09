# 性能基准

此模块面向 `:app` 的 `benchmarkRelease` 变体生成 Baseline Profile，并测量冷启动、分类筛选和进入详情的帧耗时。

## 受控数据

`app/src/benchmarkRelease/assets/default-data/stardew-benchmark-fixture.svdata` 是一个只含两个合成条目的 schema 4 测试包，固定包含“作物”分类和“萝卜种子”。它随**基准变体**安装，不进入 debug 或正式 release 包，也不包含真实游戏数据、图片或玩家信息。

每项性能测试会清除目标基准应用的数据，让应用通过正常的内置包启动流程导入该合成包；导入完成后才开始采样。因此数据包导入和系统文件选择器不会混入启动或交互指标。受控包不存在、无法通过校验或页面路径不符合预期时测试会直接失败。

## 前提

- 已连接 Android 10（API 29）或更高版本的可用设备或模拟器。
- 测试会清除 `benchmarkRelease` 目标应用的本地数据；不要在该变体保存任何要保留的个人记录。

## 运行

```powershell
./gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

比较性能数字时保持设备、Android 版本、数据包、测试迭代数和编译模式一致。没有设备时，源码编译通过只表示基准入口可构建，不表示性能已验收。
