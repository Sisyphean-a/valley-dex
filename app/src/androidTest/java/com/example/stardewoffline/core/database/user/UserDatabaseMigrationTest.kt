package com.example.stardewoffline.core.database.user

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    @Test
    fun migrationFromVersionOneCreatesOrderingIndexes() {
        val context = instrumentationTestContext()
        val databaseName = "user-migration-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        createVersionOneDatabase(databaseFile)
        val database = Room.databaseBuilder(context, UserDatabase::class.java, databaseName)
            .addMigrations(UserDatabase.MIGRATION_1_2)
            .build()
        try {
            database.openHelper.writableDatabase
            assertTrue(indexExists(database, "favorites", "index_favorites_createdAt_entityId"))
            assertTrue(indexExists(database, "view_history", "index_view_history_lastViewedAt_entityId"))
            assertTrue(indexExists(database, "recent_searches", "index_recent_searches_lastUsedAt_normalizedQuery"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersionOneDatabase(file: File) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE favorites (entityId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(entityId))")
            database.execSQL("CREATE TABLE view_history (entityId TEXT NOT NULL, lastViewedAt INTEGER NOT NULL, viewCount INTEGER NOT NULL, PRIMARY KEY(entityId))")
            database.execSQL("CREATE TABLE notes (entityId TEXT NOT NULL, content TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(entityId))")
            database.execSQL("CREATE TABLE recent_searches (normalizedQuery TEXT NOT NULL, displayQuery TEXT NOT NULL, lastUsedAt INTEGER NOT NULL, useCount INTEGER NOT NULL, PRIMARY KEY(normalizedQuery))")
            database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            database.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '94d4ad252dbc0bf3f4cffc2d18462348')")
            database.execSQL("PRAGMA user_version = 1")
        }
    }

    private fun indexExists(database: UserDatabase, table: String, index: String): Boolean =
        database.openHelper.writableDatabase.query("PRAGMA index_list('$table')").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.any { it == index }
        }
}
