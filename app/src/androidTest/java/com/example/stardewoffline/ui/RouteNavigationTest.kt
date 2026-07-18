package com.example.stardewoffline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datastore.AppPreferences
import com.example.stardewoffline.core.ui.LocalAppPreferences
import com.example.stardewoffline.core.ui.theme.StardewOfflineTheme
import com.example.stardewoffline.data.EntityRelationResolver
import com.example.stardewoffline.feature.bootstrap.BootstrapRoute
import com.example.stardewoffline.feature.bootstrap.BootstrapViewModel
import com.example.stardewoffline.feature.detail.DetailRoute
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
            composeRule.onNodeWithTag("home-category:type:crop").performClick()
            composeRule.runOnIdle { assertEquals("type:crop", selected) }
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
            waitForText("最近浏览")
            composeRule.onNode(hasSetTextAction()).performTextInput("Turnip")
            waitForText("物品")
            composeRule.onAllNodesWithText("物品")[1].performClick()
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
