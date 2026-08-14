package com.example.stardewoffline.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import java.io.File
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.data.wiki.Schema5WikiCatalogue
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.SyntheticSchema5DataPackageFactory
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WikiCatalogueTest {
    private val context get() = instrumentationTestContext()

    @Test
    fun everyPublishedTypeHasAReadableAllCategoriesPath() = runBlocking {
        val scenario = readyScenario()
        try {
            val catalogue = catalogue(scenario)
            val sections = catalogue.sections().getOrNull() ?: error("目录不可用")
            assertTrue(sections.none { it.id == "major" })
            val all = sections.flatMap { it.categories }
            assertEquals(setOf("type:object", "type:crop", "type:fish", "type:villager"), all.map { it.id }.toSet())
            assertEquals("作物", all.first { it.id == "type:crop" }.title)
            assertTrue(all.all { it.title.isNotBlank() && it.entryCount > 0 })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typeCategoriesReturnNormalizedEntries() = runBlocking {
        val scenario = readyScenario()
        try {
            val catalogue = catalogue(scenario)
            val objects = catalogue.entries(CatalogueQuery("type:object")).getOrNull() ?: error("物品分类不可用")
            assertTrue(objects.entries.any { it.id == "object:1" && it.title == "萝卜" })
            val crop = catalogue.entries(CatalogueQuery("type:crop", "种子")).getOrNull() ?: error("作物分类不可用")
            assertEquals(listOf("crop:1"), crop.entries.map { it.id })
            assertTrue(crop.entries.single().image is com.example.stardewoffline.core.model.EntryImage.Packaged)
            val filtered = catalogue.entries(CatalogueQuery("type:crop", entryCategory = "春季")).getOrNull() ?: error("分类筛选不可用")
            assertEquals(listOf("crop:1"), filtered.entries.map { it.id })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun unknownCategoryIsRejected() = runBlocking {
        val scenario = readyScenario()
        try {
            val catalogue = catalogue(scenario)
            assertTrue(catalogue.entries(CatalogueQuery("unknown")) is AppResult.Failure)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun failedVerificationInvalidatesCachedCatalogueEntries() = runBlocking {
        val scenario = readyScenario()
        try {
            val catalogue = catalogue(scenario)
            assertTrue(catalogue.entries(CatalogueQuery("type:crop")).getOrNull()?.entries?.isNotEmpty() == true)
            val packageId = checkNotNull(scenario.preferences.current().activePackageId)
            File(scenario.context.filesDir, "content/packages/$packageId/stardew.db").writeBytes(byteArrayOf(0))

            assertTrue(scenario.dataPackages.verifyActive() is AppResult.Failure)
            assertTrue(catalogue.entries(CatalogueQuery("type:crop")) is AppResult.Failure)
            assertTrue(scenario.dataPackages.openActive() is AppResult.Failure)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typedFacetFilterUsesSchema5BrowseFacets() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
            }
            val spring = catalogue(scenario).entries(
                CatalogueQuery("type:crop", entryCategory = "春季"),
            ).getOrNull() ?: error("季节筛选失败")
            assertEquals(listOf("crop:1"), spring.entries.map { it.id })
            val winter = catalogue(scenario).entries(
                CatalogueQuery("type:crop", entryCategory = "冬季"),
            ).getOrNull() ?: error("空结果筛选失败")
            assertTrue(winter.entries.isEmpty())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typedFactItemConditionIsKeptInReadableEntry() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
            }
            val entry = catalogue(scenario).entry("fish:1").getOrNull() ?: error("鱼类条目不可用")
            val values = entry.sections.flatMap { it.facts }.map { it.value }
            assertTrue(values.any { it.contains("海滩") && it.contains("条件：春季可用") })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typedVillagerSupportFactsBecomeReadableSubmenus() = runBlocking {
        val scenario = readyScenario()
        try {
            val entry = catalogue(scenario).entry("villager:Alice").getOrNull() ?: error("村民条目不可用")
            assertEquals(setOf("日程", "礼物偏好"), entry.submenus.map { it.title }.toSet())
            assertTrue(entry.submenus.first { it.title == "日程" }.groups.single().items.single().details.any { it.label == "地点" && it.value == "小镇" })
            assertTrue(entry.submenus.first { it.title == "礼物偏好" }.groups.single().title == "最爱")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun entryUsesReadableLabelsAndResolvableRelations() = runBlocking {
        val scenario = readyScenario()
        try {
            val entry = catalogue(scenario).entry("crop:1").getOrNull() ?: error("条目不可用")
            assertEquals("萝卜种子", entry.title)
            assertEquals("作物", entry.categoryLabel)
            assertTrue(entry.image is com.example.stardewoffline.core.model.EntryImage.Packaged)
            assertTrue(entry.relations.any { it.target is com.example.stardewoffline.core.model.RelationTarget.Entry })
        } finally {
            scenario.close()
        }
    }

    private fun catalogue(scenario: TestAppScenario) = Schema5WikiCatalogue(scenario.dataPackages, scenario.schema5ContentRepository)

    private suspend fun readyScenario(): TestAppScenario {
        val scenario = TestAppScenario.create(context)
        SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
            check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
        return scenario
    }
}
