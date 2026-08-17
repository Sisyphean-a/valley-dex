# 性能基准

此模块面向 `:app` 的 `benchmarkRelease` 变体生成 Baseline Profile，并测量冷启动、分类筛选和进入详情的帧耗时。

## 当前状态

`app/src/benchmarkRelease/assets/default-data/stardew-benchmark-fixture.svdata` 仍是只含两个合成条目的 schema 4 fixture。当前应用的普通安装路径只接受 schema 5，因此清空目标应用数据后，该 fixture 不能可靠地让基准路径进入图鉴；在 fixture 升级为发布级 schema 5 包前，不得把宏基准输出视为性能验收。

## 前提

- 已连接 Android 10（API 29）或更高版本的可用设备或模拟器。
- 测试会清除 `benchmarkRelease` 目标应用的本地数据；不要在该变体保存任何要保留的个人记录。

## 运行

```powershell
./gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

比较性能数字时保持设备、Android 版本、数据包、测试迭代数和编译模式一致。没有设备时，源码编译通过只表示基准入口可构建，不表示性能已验收。
