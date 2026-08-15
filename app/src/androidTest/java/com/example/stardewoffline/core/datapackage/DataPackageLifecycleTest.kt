package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.DataPackageInfo
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticSchema5DataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataPackageLifecycleTest {
    private val context get() = instrumentationTestContext()

    @Test
    fun importsBothVariantsAndRollsBackToTheFirstPackage() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            val first = import(scenario, SyntheticPackageVariant.A).getOrNull() ?: error("A 包未导入")
            val second = import(scenario, SyntheticPackageVariant.B).getOrNull() ?: error("B 包未导入")
            assertNotEquals(first.id, second.id)
            assertTrue(scenario.schema5ContentRepository.detail("villager:Alice").getOrNull() == null)

            val rollback = scenario.dataPackages.rollback().getOrNull() ?: error("回滚失败")
            assertEquals(first.id, rollback.id)
            assertEquals("测试村民", scenario.schema5ContentRepository.detail("villager:Alice").getOrNull()?.nameZh)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun legacyV4RecoveryPinIsStoredWithoutEnteringTypedQueries() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticDataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                assertTrue(scenario.dataPackages.pinLegacyV4Recovery(fixture.root) is AppResult.Success)
            }
            val preferences = scenario.preferences.current()
            assertEquals(null, preferences.activePackageId)
            assertTrue(preferences.pinnedLegacyV4PackageId != null)
            assertTrue(scenario.schema5ContentRepository.detail("object:1") is AppResult.Failure)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun nestedCoroutineReadReusesTheActivePackageLeaseWithoutDeadlocking() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            import(scenario, SyntheticPackageVariant.A).getOrNull() ?: error("数据包未导入")
            val result = withTimeout(5_000) {
                scenario.dataPackages.withActivePackage {
                    coroutineScope {
                        async { scenario.schema5ContentRepository.summaries("villager") }.await()
                    }
                }
            }
            assertEquals(1, result.getOrNull()?.size)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun verificationFailureFallsBackToThePreviousValidPackage() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            val first = import(scenario, SyntheticPackageVariant.A).getOrNull() ?: error("A 包未导入")
            val second = import(scenario, SyntheticPackageVariant.B).getOrNull() ?: error("B 包未导入")
            File(scenario.context.filesDir, "content/packages/${second.id}/stardew.db").writeBytes(byteArrayOf(0))

            assertTrue(scenario.dataPackages.verifyActive() is AppResult.Failure)
            assertEquals(first.id, scenario.dataPackages.openActive().getOrNull()?.id)
            assertEquals("测试村民", scenario.schema5ContentRepository.detail("villager:Alice").getOrNull()?.nameZh)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun reimportingAPackageRestoresItsDamagedDirectory() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                val installed = scenario.dataPackages.installAndActivate(fixture.archive.inputStream()).getOrNull()
                    ?: error("初次导入失败")
                File(scenario.context.filesDir, "content/packages/${installed.id}/stardew.db").writeBytes(byteArrayOf(0))
                assertTrue(scenario.dataPackages.verifyActive() is AppResult.Failure)

                val restored = scenario.dataPackages.installAndActivate(fixture.archive.inputStream()).getOrNull()
                    ?: error("重新导入失败")
                assertEquals(installed.id, restored.id)
                assertEquals("萝卜", scenario.schema5ContentRepository.detail("object:1").getOrNull()?.nameZh)
            }
        } finally {
            scenario.close()
        }
    }

    private suspend fun import(
        scenario: TestAppScenario,
        variant: SyntheticPackageVariant,
    ): AppResult<DataPackageInfo> {
        val fixture = SyntheticSchema5DataPackageFactory(context).create(variant)
        return try {
            fixture.archive.inputStream().use { input -> scenario.dataPackages.installAndActivate(input) }
                .also { result ->
                    if (result is AppResult.Failure) {
                        error("$variant fixture 导入失败：${result.error.message}")
                    }
                }
        } finally {
            fixture.close()
        }
    }

}
