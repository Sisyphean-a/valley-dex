package com.example.stardewoffline.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.testsupport.SyntheticSchema5DataPackageFactory
import com.example.stardewoffline.testsupport.SyntheticPackageVariant
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDataContinuityTest {
    private val context get() = instrumentationTestContext()

    @Test
    fun preservesFavoritesHistoryAndSearchesAcrossPackageSwitchAndAllowsMissingRecordCleanup() = runBlocking {
        val scenario = TestAppScenario.create(context)
        try {
            install(scenario, SyntheticPackageVariant.A)
            writeUserRecords(scenario)
            install(scenario, SyntheticPackageVariant.B)

            assertEquals(setOf("object:1", "villager:Alice"), scenario.userRepository.favorites().first().map { it.entityId }.toSet())
            assertEquals(setOf("object:1", "villager:Alice"), scenario.userRepository.history().first().map { it.entityId }.toSet())
            assertEquals(listOf("萝卜"), scenario.userRepository.recentSearches().first().map { it.displayQuery })
            assertNull(scenario.schema5ContentRepository.detail("villager:Alice").getOrNull())

            scenario.userRepository.toggleFavorite("villager:Alice", false)
            scenario.userRepository.deleteHistory("villager:Alice")
            assertTrue(scenario.userRepository.favorites().first().none { it.entityId == "villager:Alice" })
            assertTrue(scenario.userRepository.history().first().none { it.entityId == "villager:Alice" })
        } finally {
            scenario.close()
        }
    }

    private suspend fun writeUserRecords(scenario: TestAppScenario) {
        scenario.userRepository.toggleFavorite("object:1", true)
        scenario.userRepository.toggleFavorite("villager:Alice", true)
        scenario.userRepository.recordView("object:1")
        scenario.userRepository.recordView("villager:Alice")
        scenario.userRepository.rememberSearch("萝卜", "萝卜")
    }

    private suspend fun install(scenario: TestAppScenario, variant: SyntheticPackageVariant) {
        SyntheticSchema5DataPackageFactory(context).create(variant).use { fixture ->
            assertTrue(scenario.dataPackages.installAndActivate(fixture.archive.inputStream()) is AppResult.Success)
        }
    }
}
