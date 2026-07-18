package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.DataPackageInfo
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageFailure
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.runBlocking
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
            assertTrue(scenario.contentRepository.detail("villager:Alice").getOrNull() == null)

            val rollback = scenario.dataPackages.rollback().getOrNull() ?: error("回滚失败")
            assertEquals(first.id, rollback.id)
            assertEquals("测试村民", scenario.contentRepository.detail("villager:Alice").getOrNull()?.nameZh)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun rejectedPackagesLeaveTheCurrentPackageReadable() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            val active = import(scenario, SyntheticPackageVariant.A).getOrNull() ?: error("A 包未导入")
            SyntheticPackageFailure.entries.forEach { failure ->
                val rejected = import(scenario, SyntheticPackageVariant.A, failure)
                assertExpectedError(rejected, failure)
                assertEquals(active.id, scenario.dataPackages.openActive().getOrNull()?.id)
                assertEquals("萝卜", scenario.contentRepository.detail("object:1").getOrNull()?.nameZh)
            }
        } finally {
            scenario.close()
        }
    }

    private suspend fun import(
        scenario: TestAppScenario,
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure? = null,
    ): AppResult<DataPackageInfo> {
        val fixture = SyntheticDataPackageFactory(context).create(variant, failure)
        return try {
            fixture.archive.inputStream().use { input -> scenario.dataPackages.installAndActivate(input) }
        } finally {
            fixture.close()
        }
    }

    private fun assertExpectedError(result: AppResult<*>, failure: SyntheticPackageFailure) {
        val error = (result as? AppResult.Failure)?.error
        val matches = when (failure) {
            SyntheticPackageFailure.UnsupportedSchema,
            SyntheticPackageFailure.LegacySchema -> error is AppError.UnsupportedSchema
            SyntheticPackageFailure.InvalidFormat -> error is AppError.InvalidPackageFormat
            SyntheticPackageFailure.NotPublishable -> error is AppError.NotPublishable
            SyntheticPackageFailure.QualityFailed -> error is AppError.QualityFailed
            SyntheticPackageFailure.InvalidJson, SyntheticPackageFailure.MissingDatabase -> error is AppError.InvalidManifest
            SyntheticPackageFailure.HashMismatch -> error is AppError.HashMismatch
            SyntheticPackageFailure.MetadataMismatch,
            SyntheticPackageFailure.MismatchedEntityCount -> error is AppError.MetadataMismatch
            SyntheticPackageFailure.MissingImage -> error is AppError.ImageMissing
            SyntheticPackageFailure.InvalidEntityTypeCatalog -> error is AppError.InvalidEntityTypeCatalog
            SyntheticPackageFailure.MissingBuildMeta,
            SyntheticPackageFailure.MissingSearchIndex -> error is AppError.DatabaseCorrupted
        }
        assertTrue("$failure 返回了 $error", matches)
    }
}
