package com.example.stardewoffline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.model.EntryFact
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.EntrySection
import com.example.stardewoffline.core.model.RelationTarget
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySubmenu
import com.example.stardewoffline.core.model.WikiEntrySubmenuGroup
import com.example.stardewoffline.core.model.WikiEntrySubmenuItem
import com.example.stardewoffline.core.datastore.AppPreferences
import com.example.stardewoffline.core.ui.LocalAppPreferences
import com.example.stardewoffline.core.ui.theme.StardewOfflineTheme
import com.example.stardewoffline.data.EntityRelationResolver
import com.example.stardewoffline.feature.bootstrap.BootstrapRoute
import com.example.stardewoffline.feature.bootstrap.BootstrapViewModel
import com.example.stardewoffline.feature.detail.DetailRoute
import com.example.stardewoffline.feature.detail.DetailScreen
import com.example.stardewoffline.feature.detail.DetailUiState
import com.example.stardewoffline.feature.detail.DetailViewModel
import com.example.stardewoffline.feature.home.HomeRoute
import com.example.stardewoffline.feature.home.HomeViewModel
import com.example.stardewoffline.feature.search.SearchRoute
import com.example.stardewoffline.feature.search.SearchViewModel
import com.example.stardewoffline.feature.type.TypeListRoute
import com.example.stardewoffline.feature.type.TypeListViewModel
import com.example.stardewoffline.data.wiki.DefaultWikiCatalogue
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.TestHostActivity
import com.example.stardewoffline.testsupport.TestViewModelFactory
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<TestHostActivity>()
    private val context get() = instrumentationTestContext()

    @Test
    fun bootstrapWithoutDefaultPackageShowsImportAction() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            val viewModel = provide(scenario) { BootstrapViewModel(scenario.packageRepository) }
            setRoute { BootstrapRoute(onReady = {}, viewModel = viewModel) }
            waitForText("选择数据包")
            assertTrue(context.assets.list("default-data")?.none { it.endsWith(".svdata") } != false)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun homeCategoryTagNavigatesToTheSelectedType() = runBlocking {
        val scenario = readyScenario()
        try {
            scenario.userRepository.recordView("object:1")
            var selected: String? = null
            val viewModel = provide(scenario) {
                HomeViewModel(
                    DefaultWikiCatalogue(scenario.dataPackages, scenario.contentRepository, EntityRelationResolver(scenario.contentRepository), scenario.searchRepository),
                    scenario.preferences,
                )
            }
            setRoute { HomeRoute(onCategory = { selected = it }, onDetail = {}, viewModel = viewModel) }
            composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText("全部分类").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(composeRule.onAllNodesWithText("最近浏览").fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithTag("home-category:type:crop").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals("type:crop", selected) }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun villagerListShowsDenseHeaderAndBackNavigation() = runBlocking {
        val scenario = readyScenario()
        try {
            var backed = false
            val viewModel = provide(scenario) {
                TypeListViewModel(
                    saved = SavedStateHandle(mapOf("categoryId" to "type:villager")),
                    catalogue = DefaultWikiCatalogue(scenario.dataPackages, scenario.contentRepository, EntityRelationResolver(scenario.contentRepository), scenario.searchRepository),
                    content = scenario.contentRepository,
                    preferences = scenario.preferences,
                )
            }
            setRoute { TypeListRoute(onDetail = {}, onBack = { backed = true }, viewModel = viewModel) }
            waitForText("测试村民")
            waitForText("1 条本地资料")
            composeRule.onNodeWithContentDescription("返回").performClick()
            composeRule.runOnIdle { assertTrue(backed) }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun villagerDetailKeepsDenseFactsScheduleAndGiftInteractions() {
        val entry = WikiEntry(
            id = "villager:Alice",
            title = "测试村民",
            englishTitle = "Alice",
            categoryLabel = "村民",
            image = EntryImage.Missing,
            summary = "住在鹈鹕镇的居民",
            sections = listOf(EntrySection("基本资料", listOf(EntryFact("生日", "春季 4 日"), EntryFact("住址", "杂货店")))),
            relations = emptyList(),
            submenus = listOf(
                WikiEntrySubmenu(
                    "日程",
                    "1 条季节/日期规则",
                    listOf(
                        WikiEntrySubmenuGroup(
                            "春季",
                            listOf(WikiEntrySubmenuItem("默认日程", listOf(EntryFact("时间", "09:00"), EntryFact("地点", "城镇")))),
                        ),
                    ),
                ),
                WikiEntrySubmenu(
                    "礼物偏好",
                    "1 项偏好",
                    listOf(
                        WikiEntrySubmenuGroup(
                            "最爱",
                            listOf(WikiEntrySubmenuItem("紫水晶", target = RelationTarget.Entry("object:74", "紫水晶"))),
                        ),
                    ),
                ),
            ),
        )
        setRoute { DetailScreen(DetailUiState(entry = entry), favorite = false, onBack = {}, onFavorite = {}, onDetail = {}) }
        composeRule.onNodeWithTag("detail-submenu-header:日程").performScrollTo().performClick()
        composeRule.onNodeWithTag("detail-schedule-row:默认日程").assertExists()
        composeRule.onNodeWithTag("detail-submenu-header:日程").performClick()
        composeRule.onNodeWithTag("detail-submenu-header:礼物偏好").performScrollTo().performClick()
        composeRule.onNodeWithTag("detail-gift-chip:最爱:紫水晶").assertExists()
    }

    @Test
    fun typeListGridShowsImageAndEnglishName() = runBlocking {
        val scenario = readyScenario()
        try {
            var gridDetail: String? = null
            val viewModel = provide(scenario) {
                TypeListViewModel(
                    saved = SavedStateHandle(mapOf("categoryId" to "type:crop")),
                    catalogue = DefaultWikiCatalogue(scenario.dataPackages, scenario.contentRepository, EntityRelationResolver(scenario.contentRepository), scenario.searchRepository),
                    content = scenario.contentRepository,
                    preferences = scenario.preferences,
                )
            }
            setRoute { TypeListRoute(onDetail = { gridDetail = it }, viewModel = viewModel) }
            waitForText("萝卜种子")
            waitForText("Turnip Seeds")
            assertEquals(1, composeRule.onAllNodesWithText("作物").fetchSemanticsNodes().size)
            composeRule.onNodeWithContentDescription("萝卜种子").assertExists()
            composeRule.onNodeWithTag("wiki-grid-card:crop:1").performClick()
            composeRule.runOnIdle { assertEquals("crop:1", gridDetail) }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typeListWithoutImageOmitsPlaceholderAndDuplicateCategory() = runBlocking {
        val scenario = readyScenario()
        try {
            val viewModel = provide(scenario) {
                TypeListViewModel(
                    saved = SavedStateHandle(mapOf("categoryId" to "type:fish")),
                    catalogue = DefaultWikiCatalogue(scenario.dataPackages, scenario.contentRepository, EntityRelationResolver(scenario.contentRepository), scenario.searchRepository),
                    content = scenario.contentRepository,
                    preferences = scenario.preferences,
                )
            }
            setRoute { TypeListRoute(onDetail = {}, viewModel = viewModel) }
            waitForText("测试鱼")
            assertTrue(composeRule.onAllNodesWithContentDescription("测试鱼 暂无图片").fetchSemanticsNodes().isEmpty())
            assertEquals(1, composeRule.onAllNodesWithText("鱼类").fetchSemanticsNodes().size)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun typeListAndSearchResultsNavigateWithStableEntityIds() = runBlocking {
        val scenario = readyScenario()
        try {
            var typeDetail: String? = null
            val listViewModel = provide(scenario) {
                TypeListViewModel(
                    saved = SavedStateHandle(mapOf("categoryId" to "type:crop")),
                    catalogue = DefaultWikiCatalogue(scenario.dataPackages, scenario.contentRepository, EntityRelationResolver(scenario.contentRepository), scenario.searchRepository),
                    content = scenario.contentRepository,
                    preferences = scenario.preferences,
                )
            }
            setRoute { TypeListRoute(onDetail = { typeDetail = it }, viewModel = listViewModel) }
            waitForText("萝卜种子")
            composeRule.onNodeWithText("萝卜种子").performClick()
            composeRule.runOnIdle { assertEquals("crop:1", typeDetail) }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun searchResultAndDetailRouteRemainReachable() = runBlocking {
        val scenario = readyScenario()
        try {
            scenario.userRepository.recordView("object:1")
            var searchDetail: String? = null
            val searchViewModel = provide(scenario) {
                SearchViewModel(
                    catalogue = DefaultWikiCatalogue(
                        scenario.dataPackages,
                        scenario.contentRepository,
                        EntityRelationResolver(scenario.contentRepository),
                        scenario.searchRepository,
                    ),
                    content = scenario.contentRepository,
                    user = scenario.userRepository,
                    preferences = scenario.preferences,
                )
            }
            setRoute { SearchRoute(onDetail = { searchDetail = it }, viewModel = searchViewModel) }
            composeRule.onNode(hasSetTextAction()).performTextInput("Turnip")
            waitForText("萝卜")
            assertTrue(composeRule.onAllNodesWithText("最近浏览").fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("最近搜索").fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithContentDescription("打开 萝卜").performClick()
            composeRule.runOnIdle { assertEquals("object:1", searchDetail) }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun detailRouteLoadsAStableIdAndRecordsItsView() = runBlocking {
        val scenario = readyScenario()
        try {
            val viewModel = provide(scenario) {
                DetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf("id" to "object:1")),
                    catalogue = DefaultWikiCatalogue(
                        scenario.dataPackages,
                        scenario.contentRepository,
                        EntityRelationResolver(scenario.contentRepository),
                        scenario.searchRepository,
                    ),
                    content = scenario.contentRepository,
                    user = scenario.userRepository,
                )
            }
            setRoute { DetailRoute(onBack = {}, onDetail = {}, viewModel = viewModel) }
            waitForText("萝卜")
            assertTrue(scenario.userRepository.history().first().any { it.entityId == "object:1" })
        } finally {
            scenario.close()
        }
    }

    private suspend fun readyScenario(): TestAppScenario {
        val scenario = TestAppScenario.create(context)
        SyntheticDataPackageFactory(context).create(SyntheticPackageVariant.A).use { fixture ->
            check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
        return scenario
    }

    private inline fun <reified T : ViewModel> provide(
        scenario: TestAppScenario,
        noinline creator: () -> T,
    ): T {
        val factory = TestViewModelFactory(mapOf(T::class.java to creator))
        return ViewModelProvider(scenario.viewModels, factory)[T::class.java]
    }

    private fun setRoute(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppPreferences provides AppPreferences()) {
                StardewOfflineTheme(content = content)
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
