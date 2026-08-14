package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealV4PackageValidationTest {
    @Test
    fun rejectsTheExplicitRealV4PackageFromOrdinaryInstall() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString(REQUIRED_ARGUMENT) != "true") return@runBlocking
        val archive = File(requireNotNull(arguments.getString(PACKAGE_ARGUMENT)) { "缺少真实数据包路径" })
        check(archive.isFile) { "真实数据包不存在：$archive" }

        val scenario = TestAppScenario.create(instrumentationTestContext())
        try {
            val result = archive.inputStream().use { scenario.dataPackages.installAndActivate(it) }
            assertTrue(result is AppResult.Failure)
            assertEquals(
                AppError.UnsupportedSchema(4),
                (result as AppResult.Failure).error,
            )
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val REQUIRED_ARGUMENT = "realV4Required"
        const val PACKAGE_ARGUMENT = "realV4PackagePath"
    }
}
