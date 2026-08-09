package com.example.stardewoffline.core.database.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDataDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC, entityId ASC") fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE entityId = :id)") fun isFavorite(id: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFavorite(value: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE entityId = :id") suspend fun deleteFavorite(id: String)

    @Query("SELECT * FROM view_history ORDER BY lastViewedAt DESC, entityId ASC LIMIT 200") fun history(): Flow<List<HistoryEntity>>
    @Query("""
        INSERT INTO view_history(entityId, lastViewedAt, viewCount)
        VALUES(:id, :now, 1)
        ON CONFLICT(entityId) DO UPDATE SET
            lastViewedAt = excluded.lastViewedAt,
            viewCount = viewCount + 1
    """)
    suspend fun upsertHistoryView(id: String, now: Long)
    @Query("DELETE FROM view_history WHERE entityId = :id") suspend fun deleteHistory(id: String)
    @Query("DELETE FROM view_history") suspend fun clearHistory()
    @Query("DELETE FROM view_history WHERE entityId NOT IN (SELECT entityId FROM view_history ORDER BY lastViewedAt DESC, entityId ASC LIMIT 200)") suspend fun trimHistory()

    @Transaction
    suspend fun recordHistoryView(id: String, now: Long) {
        upsertHistoryView(id, now)
        trimHistory()
    }

    @Query("SELECT * FROM notes WHERE entityId = :id LIMIT 1") fun note(id: String): Flow<NoteEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveNote(value: NoteEntity)
    @Query("DELETE FROM notes WHERE entityId = :id") suspend fun deleteNote(id: String)

    @Query("SELECT * FROM recent_searches ORDER BY lastUsedAt DESC, normalizedQuery ASC LIMIT 20") fun searches(): Flow<List<RecentSearchEntity>>
    @Query("""
        INSERT INTO recent_searches(normalizedQuery, displayQuery, lastUsedAt, useCount)
        VALUES(:normalized, :display, :now, 1)
        ON CONFLICT(normalizedQuery) DO UPDATE SET
            displayQuery = excluded.displayQuery,
            lastUsedAt = excluded.lastUsedAt,
            useCount = useCount + 1
    """)
    suspend fun upsertSearchUse(normalized: String, display: String, now: Long)
    @Query("DELETE FROM recent_searches WHERE normalizedQuery NOT IN (SELECT normalizedQuery FROM recent_searches ORDER BY lastUsedAt DESC, normalizedQuery ASC LIMIT 20)")
    suspend fun trimSearches()

    @Transaction
    suspend fun recordSearchUse(normalized: String, display: String, now: Long) {
        upsertSearchUse(normalized, display, now)
        trimSearches()
    }

    @Query("DELETE FROM recent_searches WHERE normalizedQuery = :query") suspend fun deleteSearch(query: String)
    @Query("DELETE FROM recent_searches") suspend fun clearSearches()
}
