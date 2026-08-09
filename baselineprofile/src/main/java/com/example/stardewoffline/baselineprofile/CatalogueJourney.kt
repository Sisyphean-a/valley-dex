package com.example.stardewoffline.baselineprofile

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Flow: clears only the benchmark target's app data, then lets its benchmark-only default asset
 * complete the ordinary bootstrap import before measurements/profile collection begin.
 * Failure: absence of the controlled asset is a hard failure rather than a benchmark of the import UI.
 */
internal fun prepareControlledPackage() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.executeShellCommand("pm clear $TARGET_PACKAGE").close()
    val device = UiDevice.getInstance(instrumentation)
    device.pressHome()
    val launch = requireNotNull(instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)) {
        "未安装基准目标应用：$TARGET_PACKAGE"
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    instrumentation.context.startActivity(launch)
    device.requireCatalogueJourneyReady()
    device.pressHome()
}

internal fun UiDevice.returnToHome(timeoutMillis: Long = UI_TIMEOUT_MS) {
    repeat(MAX_BACK_STEPS) {
        if (hasObject(By.desc("打开分类 作物"))) return
        pressBack()
        waitForIdle()
    }
    check(wait(Until.hasObject(By.desc("打开分类 作物")), timeoutMillis)) {
        "无法将目标应用恢复到受控图鉴首页。"
    }
}

internal fun UiDevice.requireCatalogueJourneyReady(timeoutMillis: Long = UI_TIMEOUT_MS) {
    check(wait(Until.hasObject(By.desc("搜索")), timeoutMillis)) {
        "基准测试需要已导入的受控 schema 4 数据包；当前应用未到达主导航。"
    }
    check(wait(Until.hasObject(By.desc("打开分类 作物")), timeoutMillis)) {
        "受控数据包必须包含可浏览的“作物”分类。"
    }
}

internal fun UiDevice.runCatalogueJourney(timeoutMillis: Long = UI_TIMEOUT_MS) {
    requireCatalogueJourneyReady(timeoutMillis)
    findObject(By.desc("打开分类 作物")).click()
    check(wait(Until.hasObject(By.desc("分类搜索输入框")), timeoutMillis)) {
        "未打开作物分类页。"
    }
    findObject(By.desc("分类搜索输入框")).text = PROFILE_QUERY
    check(wait(Until.hasObject(By.desc("打开 $PROFILE_RESULT")), timeoutMillis)) {
        "受控数据包中缺少可预测的搜索结果：$PROFILE_RESULT。"
    }
    findObject(By.desc("打开 $PROFILE_RESULT")).click()
    check(wait(Until.hasObject(By.desc("返回")), timeoutMillis)) {
        "未进入条目详情页。"
    }
}

internal const val TARGET_PACKAGE = "com.example.stardewoffline"
internal const val PROFILE_QUERY = "萝卜"
internal const val PROFILE_RESULT = "萝卜种子"
internal const val UI_TIMEOUT_MS = 15_000L
private const val MAX_BACK_STEPS = 4
