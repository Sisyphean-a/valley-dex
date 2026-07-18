package com.example.stardewoffline.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startupSearchAndDetailJourney() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("搜索")), UI_TIMEOUT_MS)
        device.findObject(By.desc("搜索")).click()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.stardewoffline"
        const val UI_TIMEOUT_MS = 15_000L
    }
}
