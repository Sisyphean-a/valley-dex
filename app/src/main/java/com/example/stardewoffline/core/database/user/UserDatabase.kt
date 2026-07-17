package com.example.stardewoffline.core.database.user

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val entityId: String, val createdAt: Long)

@Entity(tableName = "view_history")
data class HistoryEntity(@PrimaryKey val entityId: String, val lastViewedAt: Long, val viewCount: Int = 1)

@Entity(tableName = "notes")
data class NoteEntity(@PrimaryKey val entityId: String, val content: String, val updatedAt: Long)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(@PrimaryKey val normalizedQuery: String, val displayQuery: String, val lastUsedAt: Long, val useCount: Int = 1)

@Database(entities = [FavoriteEntity::class, HistoryEntity::class, NoteEntity::class, RecentSearchEntity::class], version = 1, exportSchema = true)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDataDao(): UserDataDao
}
