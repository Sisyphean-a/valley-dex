package com.example.stardewoffline.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.core.database.user.UserDatabase
import com.example.stardewoffline.testsupport.instrumentationTestContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDataRepositoryTest {
    @Test
    fun concurrentRecordsAccumulateCountsAndTrimHistoryDeterministically() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(instrumentationTestContext(), UserDatabase::class.java).build()
        val repository = UserDataRepository(database.userDataDao())
        try {
            coroutineScope {
                (1..20).map { async { repository.recordView("object:1", now = 1_000L) } }.awaitAll()
            }
            assertEquals(20, repository.history().first().single().viewCount)

            (1..210).forEach { repository.recordView("object:$it", now = 2_000L + it) }
            val history = repository.history().first()
            assertEquals(200, history.size)
            assertFalse(history.any { it.entityId == "object:1" })
            assertEquals((11..210).map { "object:$it" }.sorted(), history.map { it.entityId }.sorted())
        } finally {
            database.close()
        }
    }

    @Test
    fun concurrentSearchRecordsAccumulateUseCount() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(instrumentationTestContext(), UserDatabase::class.java).build()
        val repository = UserDataRepository(database.userDataDao())
        try {
            coroutineScope {
                (1..20).map { async { repository.rememberSearch("turnip", "Turnip", now = 1_000L) } }.awaitAll()
            }
            val search = repository.recentSearches().first().single()
            assertEquals(20, search.useCount)
            assertEquals("Turnip", search.displayQuery)

            (1..25).forEach { repository.rememberSearch("query:$it", "Query $it", now = 2_000L + it) }
            val searches = repository.recentSearches().first()
            assertEquals(20, searches.size)
            assertFalse(searches.any { it.normalizedQuery == "turnip" })
            assertEquals((6..25).map { "query:$it" }.toSet(), searches.map { it.normalizedQuery }.toSet())
        } finally {
            database.close()
        }
    }
}
