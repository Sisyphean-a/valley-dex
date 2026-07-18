package com.example.stardewoffline.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datastore.AppPreferences
import com.example.stardewoffline.core.ui.LocalAppPreferences
import com.example.stardewoffline.core.ui.theme.StardewOfflineTheme
import com.example.stardewoffline.feature.favorites.FavoritesRoute
import com.example.stardewoffline.feature.favorites.FavoritesViewModel
import com.example.stardewoffline.feature.history.HistoryRoute
import com.example.stardewoffline.feature.history.HistoryViewModel
import com.example.stardewoffline.data.EntityRelationResolver
import com.example.stardewoffline.data.wiki.DefaultWikiCatalogue
import com.example.stardewoffline.testsupport.SyntheticDataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.TestHostActivity
import com.example.stardewoffline.testsupport.TestViewModelFactory
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MissingPersonalRecordRouteTest {
    @get:Rule val composeRule = createAndroidComposeRule<TestHostActivity>()
    private val context get() = instrumentationTestContext()

    @Test
    fun missingFavoriteRemainsVisibleUntilTheUserDeletesIt() = runBlocking {
        val scenario = missingRecordScenario()
        try {
            val viewModel = ViewModelProvider(
                scenario.viewModels,
                TestViewModelFactory(mapOf(FavoritesViewModel::class.java to {
                    FavoritesViewModel(
                        scenario.userRepository,
                        catalogue(scenario),
                        scenario.contentRepository,
                    )
                })),
            )[FavoritesViewModel::class.java]
            setContent { FavoritesRoute(onDetail = {}, viewModel = viewModel) }
            waitForMissingRecord()
            composeRule.onNodeWithContentDescription("删除收藏").performClick()
            composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText("当前数据包中已不存在").fetchSemanticsNodes().isEmpty() }
            assertTrue(scenario.userRepository.favorites().first().none { it.entityId == MISSING_ID })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun missingHistoryRemainsVisibleUntilTheUserDeletesIt() = runBlocking {
        val scenario = missingRecordScenario()
        try {
            val viewModel = ViewModelProvider(
                scenario.viewModels,
                TestViewModelFactory(mapOf(HistoryViewModel::class.java to {
                    HistoryViewModel(scenario.userRepository, catalogue(scenario))
                })),
            )[HistoryViewModel::class.java]
            setContent { HistoryRoute(onBack = {}, onDetail = {}, viewModel = viewModel) }
            waitForMissingRecord()
            composeRule.onNodeWithContentDescription("删除历史记录").performClick()
            composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText("当前数据包中已不存在").fetchSemanticsNodes().isEmpty() }
            assertTrue(scenario.userRepository.history().first().none { it.entityId == MISSING_ID })
        } finally {
            scenario.close()
        }
    }

    private suspend fun missingRecordScenario(): TestAppScenario {
        val scenario = TestAppScenario.create(context)
        import(scenario, SyntheticPackageVariant.A)
        scenario.userRepository.toggleFavorite(MISSING_ID, true)
        scenario.userRepository.recordView(MISSING_ID)
        import(scenario, SyntheticPackageVariant.B)
        return scenario
    }

    private suspend fun import(scenario: TestAppScenario, variant: SyntheticPackageVariant) {
        SyntheticDataPackageFactory(context).create(variant).use { fixture ->
            check(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppPreferences provides AppPreferences()) {
                StardewOfflineTheme(content = content)
            }
        }
    }

    private fun catalogue(scenario: TestAppScenario) = DefaultWikiCatalogue(
        scenario.dataPackages,
        scenario.contentRepository,
        EntityRelationResolver(scenario.contentRepository),
        scenario.searchRepository,
    )

    private fun waitForMissingRecord() {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodesWithText("当前数据包中已不存在").fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val MISSING_ID = "villager:Alice"
        const val TIMEOUT = 5_000L
    }
}
