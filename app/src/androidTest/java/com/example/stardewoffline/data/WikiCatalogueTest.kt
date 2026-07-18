package com.example.stardewoffline.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.data.wiki.DefaultWikiCatalogue
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
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
            val all = sections.first { it.id == "all" }.categories
            assertEquals(setOf("type:object", "type:crop", "type:fish", "type:villager"), all.map { it.id }.toSet())
            assertEquals("作物", all.first { it.id == "type:crop" }.title)
            assertTrue(all.all { it.title.isNotBlank() && it.entryCount > 0 })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun semanticAndTypeCategoriesReturnNormalizedEntries() = runBlocking {
        val scenario = readyScenario()
        try {
            val catalogue = catalogue(scenario)
            val farm = catalogue.entries(CatalogueQuery("farm")).getOrNull() ?: error("农场分类不可用")
            assertTrue(farm.entries.any { it.id == "object:1" && it.title == "萝卜" })
            val crop = catalogue.entries(CatalogueQuery("type:crop", "种子")).getOrNull() ?: error("作物分类不可用")
            assertEquals(listOf("crop:1"), crop.entries.map { it.id })
            assertTrue(crop.entries.single().image is com.example.stardewoffline.core.model.EntryImage.Packaged)
            val filtered = catalogue.entries(CatalogueQuery("farm", entryCategory = "种子")).getOrNull() ?: error("分类筛选不可用")
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

    private fun catalogue(scenario: TestAppScenario) = DefaultWikiCatalogue(
        scenario.dataPackages,
        scenario.contentRepository,
        EntityRelationResolver(scenario.contentRepository),
        scenario.searchRepository,
    )

    private suspend fun readyScenario(): TestAppScenario {
        val scenario = TestAppScenario.create(context)
        SyntheticDataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
            check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
        return scenario
    }
}
