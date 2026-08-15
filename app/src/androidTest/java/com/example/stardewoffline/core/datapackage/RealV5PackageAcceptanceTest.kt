package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.data.wiki.Schema5WikiCatalogue
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in acceptance test for an external real schema-5 package.
 *
 * The package is intentionally supplied by instrumentation arguments rather
 * than checked into the repository. This test proves the same import and typed
 * catalogue path that a player uses after selecting a release candidate.
 */
@RunWith(AndroidJUnit4::class)
class RealV5PackageAcceptanceTest {
    @Test
    fun installsAndReadsEveryPublishedCategoryFromTheExternalRealPackage() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString(REQUIRED_ARGUMENT) != "true") return@runBlocking
        val archive = File(requireNotNull(arguments.getString(PACKAGE_ARGUMENT)) { "缺少真实数据包路径" })
        check(archive.isFile) { "真实数据包不存在：$archive" }

        val scenario = TestAppScenario.create(instrumentationTestContext())
        try {
            val installed = archive.inputStream().use { scenario.dataPackages.installAndActivate(it) }
                .getOrNull() ?: error("真实 schema 5 数据包未能安装")
            assertTrue(installed.manifest.schemaVersion == 5)

            val catalogue = Schema5WikiCatalogue(scenario.dataPackages, scenario.schema5ContentRepository)
            val sections = catalogue.sections().getOrNull() ?: error("真实数据包没有可读分类")
            val categories = sections.flatMap { it.categories }
            assertFalse("真实数据包没有可浏览分类", categories.isEmpty())
            categories.forEach { category ->
                val page = catalogue.entries(CatalogueQuery(category.id)).getOrNull()
                    ?: error("分类无法读取：${category.id}")
                assertFalse("分类没有条目：${category.id}", page.entries.isEmpty())
                val entry = catalogue.entry(page.entries.first().id).getOrNull()
                    ?: error("条目无法读取：${page.entries.first().id}")
                assertTrue("条目标题为空：${entry.id}", entry.title.isNotBlank())
            }
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val REQUIRED_ARGUMENT = "realV5Required"
        const val PACKAGE_ARGUMENT = "realV5PackagePath"
    }
}
