package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.database.content.ContentDatabaseFactory
import com.example.stardewoffline.testsupport.SyntheticDataPackage
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageFailure
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyntheticDataPackageTest {
    private val context get() = instrumentationTestContext()
    private val fixture get() = SyntheticDataPackageFactory(context)

    @Test
    fun validVariantsMeetManifestDatabaseImageAndSearchContracts() = runBlocking {
        SyntheticPackageVariant.entries.forEach { variant ->
            fixture.create(variant).use { archive ->
                val result = validate(archive)
                assertTrue("${variant.name} 应通过校验", result is AppResult.Success)
                val info = (result as AppResult.Success).value
                assertEquals(if (variant == SyntheticPackageVariant.A) 4 else 3, info.buildMeta.entityCount)
                assertEquals(4, info.buildMeta.schemaVersion)
                assertEquals(0, info.missingImageCount)
            }
        }
    }

    @Test
    fun controlledFailuresKeepTheirExpectedErrorCategories() = runBlocking {
        SyntheticPackageFailure.entries.forEach { failure ->
            fixture.create(SyntheticPackageVariant.A, failure).use { archive ->
                assertExpectedError(validate(archive), failure)
            }
        }
    }

    @Test
    fun scenarioUsesTestAppStorageAndCleansIt() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            assertTrue(scenario.packageRepository.openActive() is AppResult.Failure)
        } finally {
            scenario.close()
        }
        assertFalse(File(context.filesDir, "content").exists())
    }

    private suspend fun validate(archive: SyntheticDataPackage): AppResult<com.example.stardewoffline.core.model.DataPackageInfo> {
        val extracted = File(context.cacheDir, "wiki-extracted-${UUID.randomUUID()}")
        return try {
            val extraction = SafeZipExtractor(Dispatchers.IO).extract(archive.archive, extracted)
            check(extraction is AppResult.Success) { "测试包无法解压：$extraction" }
            validator().validate(extracted)
        } finally {
            extracted.deleteRecursively()
        }
    }

    private fun validator() = DataPackageValidator(
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = false },
        hashUtils = HashUtils(Dispatchers.IO),
        databaseFactory = ContentDatabaseFactory(Dispatchers.IO),
        ioDispatcher = Dispatchers.IO,
    )

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
