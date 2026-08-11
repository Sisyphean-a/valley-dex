package com.example.stardewoffline.data

import com.example.stardewoffline.core.database.user.FavoriteEntity
import com.example.stardewoffline.core.database.user.HistoryEntity
import com.example.stardewoffline.core.database.user.RecentSearchEntity
import com.example.stardewoffline.core.database.user.UserDataDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserDataRepository @Inject constructor(private val dao: UserDataDao) {
    fun favorites(): Flow<List<FavoriteEntity>> = dao.favorites()
    fun history(): Flow<List<HistoryEntity>> = dao.history()
    fun isFavorite(id: String): Flow<Boolean> = dao.isFavorite(id)
    fun recentSearches(): Flow<List<RecentSearchEntity>> = dao.searches()

    suspend fun toggleFavorite(id: String, favorite: Boolean, now: Long = System.currentTimeMillis()) {
        if (favorite) dao.saveFavorite(FavoriteEntity(id, now)) else dao.deleteFavorite(id)
    }

    suspend fun recordView(id: String, now: Long = System.currentTimeMillis()) = dao.recordHistoryView(id, now)

    suspend fun deleteHistory(id: String) = dao.deleteHistory(id)

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun rememberSearch(normalized: String, display: String, now: Long = System.currentTimeMillis()) =
        dao.recordSearchUse(normalized, display, now)

    suspend fun deleteSearch(normalized: String) = dao.deleteSearch(normalized)

    suspend fun clearSearches() = dao.clearSearches()
}
