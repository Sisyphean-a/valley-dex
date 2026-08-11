package com.example.stardewoffline.core.database.user

import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "favorites",
    indices = [
        Index(
            value = ["createdAt", "entityId"],
            orders = [Index.Order.DESC, Index.Order.ASC],
            name = "index_favorites_createdAt_entityId",
        ),
    ],
)
data class FavoriteEntity(@PrimaryKey val entityId: String, val createdAt: Long)

@Entity(
    tableName = "view_history",
    indices = [
        Index(
            value = ["lastViewedAt", "entityId"],
            orders = [Index.Order.DESC, Index.Order.ASC],
            name = "index_view_history_lastViewedAt_entityId",
        ),
    ],
)
data class HistoryEntity(@PrimaryKey val entityId: String, val lastViewedAt: Long, val viewCount: Int = 1)

@Entity(
    tableName = "recent_searches",
    indices = [
        Index(
            value = ["lastUsedAt", "normalizedQuery"],
            orders = [Index.Order.DESC, Index.Order.ASC],
            name = "index_recent_searches_lastUsedAt_normalizedQuery",
        ),
    ],
)
data class RecentSearchEntity(@PrimaryKey val normalizedQuery: String, val displayQuery: String, val lastUsedAt: Long, val useCount: Int = 1)

@Database(entities = [FavoriteEntity::class, HistoryEntity::class, RecentSearchEntity::class], version = 3, exportSchema = true)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDataDao(): UserDataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_createdAt_entityId` ON `favorites` (`createdAt` DESC, `entityId` ASC)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_view_history_lastViewedAt_entityId` ON `view_history` (`lastViewedAt` DESC, `entityId` ASC)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_searches_lastUsedAt_normalizedQuery` ON `recent_searches` (`lastUsedAt` DESC, `normalizedQuery` ASC)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `notes`")
            }
        }
    }
}
