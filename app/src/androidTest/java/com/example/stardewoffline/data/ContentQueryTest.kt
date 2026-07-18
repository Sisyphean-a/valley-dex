package com.example.stardewoffline.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.SyntheticSearchStorage
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentQueryTest {
    private val context get() = instrumentationTestContext()

    @Test
    fun readsTypesListsDetailsAliasesAndMissingIds() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario)
            assertEquals(setOf("object", "crop", "fish", "villager"), scenario.contentRepository.typeCounts().getOrNull()?.map { it.type }?.toSet())
            assertEquals(listOf("萝卜种子"), scenario.contentRepository.summaries("crop").getOrNull()?.map { it.nameZh })
            val crop = scenario.contentRepository.detail("crop:1").getOrNull() ?: error("缺少 crop:1")
            val derived = crop.extraJson["officialDerived"] as? JsonObject
            assertEquals(JsonPrimitive("object:1"), derived?.get("harvestItemId"))
            assertEquals(listOf("根菜"), scenario.contentRepository.aliases("object:1").getOrNull())
            assertNull(scenario.contentRepository.detail("missing:1").getOrNull())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun searchesAcrossNamesAliasesPinyinAndFts() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario)
            assertContains(scenario.searchRepository.search("萝").getOrNull()?.map { it.summary.id }, "object:1")
            assertContains(scenario.searchRepository.search("turn").getOrNull()?.map { it.summary.id }, "object:1")
            assertContains(scenario.searchRepository.search("根菜").getOrNull()?.map { it.summary.id }, "object:1")
            assertContains(scenario.searchRepository.search("lb").getOrNull()?.map { it.summary.id }, "object:1")
            assertContains(scenario.searchRepository.search("水域专用词").getOrNull()?.map { it.summary.id }, "fish:1")
            assertEquals(emptyList<String>(), scenario.searchRepository.search(" ").getOrNull()?.map { it.summary.id })
            assertEquals(emptyList<String>(), scenario.searchRepository.search("不存在").getOrNull()?.map { it.summary.id })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun plainSearchTableMakesFtsFailureObservable() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario, SyntheticSearchStorage.PlainTable)
            assertTrue(scenario.searchRepository.search("水域专用词") is AppResult.Failure)
            assertTrue((scenario.searchRepository.search("水域专用词") as AppResult.Failure).error is AppError.DatabaseQueryFailed)
        } finally {
            scenario.close()
        }
    }

    private suspend fun install(scenario: TestAppScenario, storage: SyntheticSearchStorage = SyntheticSearchStorage.Fts4) {
        SyntheticDataPackageFactory(context).create(SyntheticPackageVariant.A, searchStorage = storage).use { fixture ->
            check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
    }

    private fun assertContains(values: List<String>?, expected: String) {
        assertTrue("$expected 不在 $values 中", values.orEmpty().contains(expected))
    }
}
