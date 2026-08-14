package com.example.stardewoffline.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.testsupport.SyntheticSchema5DataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.SyntheticSearchStorage
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.runBlocking
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
            assertEquals(setOf("object", "crop", "fish", "villager"), scenario.schema5ContentRepository.typeCounts().getOrNull()?.map { it.type }?.toSet())
            assertEquals(listOf("萝卜种子"), scenario.schema5ContentRepository.summaries("crop").getOrNull()?.map { it.nameZh })
            assertEquals(emptyList<String>(), scenario.schema5ContentRepository.detailsByIds(emptyList()).getOrNull()?.map { it.id })
            assertEquals(listOf("crop:1", "fish:1"), scenario.schema5ContentRepository.detailsByIds(listOf("crop:1", "fish:1")).getOrNull()?.map { it.id })
            val crop = scenario.schema5ContentRepository.detail("crop:1").getOrNull() ?: error("缺少 crop:1")
            assertEquals("crop:1", crop.id)
            assertEquals(listOf("根菜"), scenario.schema5ContentRepository.aliases("object:1").getOrNull())
            assertEquals("object:1", scenario.schema5ContentRepository.detail("legacy:object:1").getOrNull()?.id)
            assertNull(scenario.schema5ContentRepository.detail("missing:1").getOrNull())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun batchSummariesDeduplicateIdsAndKeepMissingIdsAbsent() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario)
            val ids = buildList {
                repeat(1_000) { add("missing:$it") }
                add("crop:1")
                add("crop:1")
                add("fish:1")
            }
            val summaries = scenario.schema5ContentRepository.summaries(ids).getOrNull() ?: error("批量摘要查询失败")
            assertEquals(setOf("crop:1", "fish:1"), summaries.keys)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun browseUsesTypedFacetsAndStablePackageBoundCursor() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
            }
            val first = scenario.schema5ContentRepository.browse(
                types = setOf("object", "crop", "fish", "villager"),
                pageSize = 1,
            ).getOrNull() ?: error("首批查询失败")
            val firstId = first.summaries.values.flatten().single().id
            val cursor = first.nextCursor ?: error("缺少下一页游标")
            val second = scenario.schema5ContentRepository.browse(
                types = setOf("object", "crop", "fish", "villager"),
                cursor = cursor,
                pageSize = 10,
            ).getOrNull() ?: error("下一页查询失败")
            assertTrue(second.summaries.values.flatten().none { it.id == firstId })
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.B).use { fixture ->
                check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
            }
            assertTrue(
                scenario.schema5ContentRepository.browse(
                    types = setOf("object", "crop", "fish", "villager"),
                    cursor = cursor,
                    pageSize = 10,
                ) is AppResult.Failure,
            )
            val spring = scenario.schema5ContentRepository.browse(
                types = setOf("crop"),
                facetFilters = mapOf("season" to setOf("春季")),
            ).getOrNull() ?: error("facet 查询失败")
            assertEquals(listOf("crop:1"), spring.summaries.getValue("crop").map { it.id })
            assertTrue(scenario.schema5ContentRepository.browse(setOf("crop"), cursor = "bad") is AppResult.Failure)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun searchesAcrossNamesAliasesPinyinAndFts() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario)
            val nameHits = scenario.schema5ContentRepository.search("萝").getOrNull().orEmpty()
            assertContains(nameHits.map { it.summary.id }, "object:1")
            assertEquals("名称", nameHits.first { it.summary.id == "object:1" }.reason)
            assertContains(scenario.schema5ContentRepository.search("turn").getOrNull()?.map { it.summary.id }, "object:1")
            assertContains(scenario.schema5ContentRepository.search("根菜").getOrNull()?.map { it.summary.id }, "object:1")
            assertContains(scenario.schema5ContentRepository.search("lb").getOrNull()?.map { it.summary.id }, "object:1")
            assertEquals(listOf("crop:1"), scenario.schema5ContentRepository.search("萝", setOf("crop")).getOrNull()?.map { it.summary.id })
            assertContains(scenario.schema5ContentRepository.search("水域专用词").getOrNull()?.map { it.summary.id }, "fish:1")
            assertEquals(emptyList<String>(), scenario.schema5ContentRepository.search(" ").getOrNull()?.map { it.summary.id })
            assertEquals(emptyList<String>(), scenario.schema5ContentRepository.search("不存在").getOrNull()?.map { it.summary.id })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typedSearchExposesStableCursorPages() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
            }
            val first = scenario.schema5ContentRepository.searchPage("测试", pageSize = 1).getOrNull()
                ?: error("首批搜索失败")
            assertEquals(1, first.results.size)
            val cursor = first.nextCursor ?: error("搜索缺少下一页游标")
            val second = scenario.schema5ContentRepository.searchPage(
                "测试",
                cursor = cursor,
                pageSize = 10,
            ).getOrNull() ?: error("下一批搜索失败")
            assertTrue(second.results.none { it.summary.id == first.results.single().summary.id })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun plainSearchTableMakesFtsFailureObservable() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario, SyntheticSearchStorage.PlainTable)
            assertTrue(scenario.schema5ContentRepository.search("水域专用词") is AppResult.Failure)
            assertTrue((scenario.schema5ContentRepository.search("水域专用词") as AppResult.Failure).error is AppError.DatabaseQueryFailed)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun schema5FixtureIsReadThroughTypedRepository() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
                check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
            }
            val crop = scenario.schema5ContentRepository.detail("crop:1").getOrNull() ?: error("缺少 schema 5 crop")
            assertEquals("萝卜种子", crop.summary.nameZh)
            assertEquals("测试资料", crop.facts.single { it.slotKey == "fixture_answer" }.value?.text)
            val fish = scenario.schema5ContentRepository.detail("fish:1").getOrNull() ?: error("缺少 schema 5 fish")
            val fishItem = fish.facts.single { it.slotKey == "fixture_answer" }.items.single()
            assertEquals("海滩", fishItem.value.text)
            assertEquals("春季可用", fishItem.condition?.playerSummary)
            assertEquals("official_direct", fishItem.sources.single().kind)
            assertEquals("春季", crop.summary.facets.single().value.text)
            assertEquals("derived", crop.summary.facets.single().sources.single().evidenceKind)
            assertEquals("schema 5 instrumentation fixture", crop.facts.single().sources.single().title)
        } finally {
            scenario.close()
        }
    }

    private suspend fun install(scenario: TestAppScenario, storage: SyntheticSearchStorage = SyntheticSearchStorage.Fts4) {
        SyntheticSchema5DataPackageFactory(context).create(SyntheticPackageVariant.A, searchStorage = storage).use { fixture ->
            check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
    }

    private fun assertContains(values: List<String>?, expected: String) {
        assertTrue("$expected 不在 $values 中", values.orEmpty().contains(expected))
    }
}
