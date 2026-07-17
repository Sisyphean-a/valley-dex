package com.example.stardewoffline.data

import com.example.stardewoffline.core.database.user.FavoriteEntity
import com.example.stardewoffline.core.database.user.HistoryEntity
import com.example.stardewoffline.core.database.user.NoteEntity
import com.example.stardewoffline.core.database.user.RecentSearchEntity
import com.example.stardewoffline.core.database.user.UserDataDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class UserDataRepository @Inject constructor(private val dao: UserDataDao) {
    fun favorites(): Flow<List<FavoriteEntity>> = dao.favorites()
    fun history(): Flow<List<HistoryEntity>> = dao.history()
    fun note(id: String): Flow<NoteEntity?> = dao.note(id)
    fun isFavorite(id: String): Flow<Boolean> = dao.isFavorite(id)
    fun recentSearches(): Flow<List<RecentSearchEntity>> = dao.searches()

    suspend fun toggleFavorite(id: String, favorite: Boolean, now: Long = System.currentTimeMillis()) {
        if (favorite) dao.saveFavorite(FavoriteEntity(id, now)) else dao.deleteFavorite(id)
    }

    suspend fun recordView(id: String, now: Long = System.currentTimeMillis()) {
        val current = dao.historyItem(id)
        dao.saveHistory(HistoryEntity(id, now, (current?.viewCount ?: 0) + 1))
        dao.trimHistory()
    }

    suspend fun saveNote(id: String, content: String, now: Long = System.currentTimeMillis()) {
        require(content.length <= 5000) { "笔记不能超过 5000 个字符" }
        if (content.isBlank()) dao.deleteNote(id) else dao.saveNote(NoteEntity(id, content, now))
    }

    suspend fun rememberSearch(normalized: String, display: String, now: Long = System.currentTimeMillis()) {
        val current = dao.search(normalized)
        dao.saveSearch(RecentSearchEntity(normalized, display, now, (current?.useCount ?: 0) + 1))
    }
}
