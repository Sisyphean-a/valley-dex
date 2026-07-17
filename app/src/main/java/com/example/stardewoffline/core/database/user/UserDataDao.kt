package com.example.stardewoffline.core.database.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDataDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE entityId = :id)") fun isFavorite(id: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFavorite(value: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE entityId = :id") suspend fun deleteFavorite(id: String)

    @Query("SELECT * FROM view_history ORDER BY lastViewedAt DESC LIMIT 200") fun history(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM view_history WHERE entityId = :id LIMIT 1") suspend fun historyItem(id: String): HistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveHistory(value: HistoryEntity)
    @Query("DELETE FROM view_history WHERE entityId = :id") suspend fun deleteHistory(id: String)
    @Query("DELETE FROM view_history") suspend fun clearHistory()
    @Query("DELETE FROM view_history WHERE entityId NOT IN (SELECT entityId FROM view_history ORDER BY lastViewedAt DESC LIMIT 200)") suspend fun trimHistory()

    @Query("SELECT * FROM notes WHERE entityId = :id LIMIT 1") fun note(id: String): Flow<NoteEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveNote(value: NoteEntity)
    @Query("DELETE FROM notes WHERE entityId = :id") suspend fun deleteNote(id: String)

    @Query("SELECT * FROM recent_searches ORDER BY lastUsedAt DESC LIMIT 20") fun searches(): Flow<List<RecentSearchEntity>>
    @Query("SELECT * FROM recent_searches WHERE normalizedQuery = :query LIMIT 1") suspend fun search(query: String): RecentSearchEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSearch(value: RecentSearchEntity)
    @Query("DELETE FROM recent_searches WHERE normalizedQuery = :query") suspend fun deleteSearch(query: String)
    @Query("DELETE FROM recent_searches") suspend fun clearSearches()
}
