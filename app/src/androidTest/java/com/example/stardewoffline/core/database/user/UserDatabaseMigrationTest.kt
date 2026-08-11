package com.example.stardewoffline.core.database.user

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    @Test
    fun migrationFromVersionOneCreatesOrderingIndexesAndRemovesRetiredNotes() {
        val context = instrumentationTestContext()
        val databaseName = "user-migration-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        createVersionOneDatabase(databaseFile)
        val database = Room.databaseBuilder(context, UserDatabase::class.java, databaseName)
            .addMigrations(UserDatabase.MIGRATION_1_2, UserDatabase.MIGRATION_2_3)
            .build()
        try {
            database.openHelper.writableDatabase
            assertTrue(indexExists(database, "favorites", "index_favorites_createdAt_entityId"))
            assertTrue(indexExists(database, "view_history", "index_view_history_lastViewedAt_entityId"))
            assertTrue(indexExists(database, "recent_searches", "index_recent_searches_lastUsedAt_normalizedQuery"))
            assertFalse(tableExists(database, "notes"))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFromVersionTwoRemovesRetiredNotesAndPreservesCurrentRecords() = runBlocking {
        val context = instrumentationTestContext()
        val databaseName = "user-migration-v2-${UUID.randomUUID()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        createVersionTwoDatabase(databaseFile)
        val database = Room.databaseBuilder(context, UserDatabase::class.java, databaseName)
            .addMigrations(UserDatabase.MIGRATION_2_3)
            .build()
        try {
            database.openHelper.writableDatabase
            assertFalse(tableExists(database, "notes"))
            assertEquals(listOf("object:1"), database.userDataDao().favorites().first().map { it.entityId })
            assertEquals(listOf("object:1"), database.userDataDao().history().first().map { it.entityId })
            assertEquals(listOf("萝卜"), database.userDataDao().searches().first().map { it.displayQuery })
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

    private fun createVersionTwoDatabase(file: File) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE favorites (entityId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(entityId))")
            database.execSQL("CREATE INDEX index_favorites_createdAt_entityId ON favorites (createdAt DESC, entityId ASC)")
            database.execSQL("CREATE TABLE view_history (entityId TEXT NOT NULL, lastViewedAt INTEGER NOT NULL, viewCount INTEGER NOT NULL, PRIMARY KEY(entityId))")
            database.execSQL("CREATE INDEX index_view_history_lastViewedAt_entityId ON view_history (lastViewedAt DESC, entityId ASC)")
            database.execSQL("CREATE TABLE notes (entityId TEXT NOT NULL, content TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(entityId))")
            database.execSQL("CREATE TABLE recent_searches (normalizedQuery TEXT NOT NULL, displayQuery TEXT NOT NULL, lastUsedAt INTEGER NOT NULL, useCount INTEGER NOT NULL, PRIMARY KEY(normalizedQuery))")
            database.execSQL("CREATE INDEX index_recent_searches_lastUsedAt_normalizedQuery ON recent_searches (lastUsedAt DESC, normalizedQuery ASC)")
            database.execSQL("INSERT INTO favorites VALUES ('object:1', 1)")
            database.execSQL("INSERT INTO view_history VALUES ('object:1', 1, 1)")
            database.execSQL("INSERT INTO notes VALUES ('object:1', 'legacy note', 1)")
            database.execSQL("INSERT INTO recent_searches VALUES ('萝卜', '萝卜', 1, 1)")
            database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            database.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'f2463fdf3baab3af8c0dd21aa01c37da')")
            database.execSQL("PRAGMA user_version = 2")
        }
    }

    private fun indexExists(database: UserDatabase, table: String, index: String): Boolean =
        database.openHelper.writableDatabase.query("PRAGMA index_list('$table')").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(name) else null }.any { it == index }
        }

    private fun tableExists(database: UserDatabase, table: String): Boolean =
        database.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { it.moveToFirst() }
}
