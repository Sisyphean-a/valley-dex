package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stardewoffline.core.common.getOrNull
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
    fun importsTheExplicitRealV4Package() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString(REQUIRED_ARGUMENT) != "true") return@runBlocking
        val archive = File(requireNotNull(arguments.getString(PACKAGE_ARGUMENT)) { "缺少真实数据包路径" })
        check(archive.isFile) { "真实数据包不存在：$archive" }

        val scenario = TestAppScenario.create(instrumentationTestContext())
        try {
            val result = archive.inputStream().use { scenario.dataPackages.installAndActivate(it) }
            val info = result.getOrNull() ?: error("真实 schema 4 包未通过：$result")
            assertEquals(4, info.manifest.schemaVersion)
            assertTrue(info.manifest.publishable)
            assertEquals(DataPackageContract.QUALITY_PASSED, info.manifest.quality.status)
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val REQUIRED_ARGUMENT = "realV4Required"
        const val PACKAGE_ARGUMENT = "realV4PackagePath"
    }
}
