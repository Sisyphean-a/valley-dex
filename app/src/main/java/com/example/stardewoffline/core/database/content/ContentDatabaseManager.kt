package com.example.stardewoffline.core.database.content

import android.content.Context
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ContentDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferencesRepository,
    private val factory: ContentDatabaseFactory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var opened: OpenedDatabase? = null

    suspend fun openActive(): AppResult<ContentDatabase> = withContext(ioDispatcher) {
        mutex.withLock {
            val packageId = preferences.current().activePackageId
                ?: return@withLock AppResult.Failure(AppError.NoDataPackage)
            opened?.takeIf { it.id == packageId }?.let { return@withLock AppResult.Success(it.database) }
            closeLocked()
            val root = File(context.filesDir, "content/packages/$packageId")
            val result = factory.open(root, File(root, "stardew.db"))
            if (result is AppResult.Success) opened = OpenedDatabase(packageId, result.value)
            result
        }
    }

    suspend fun close() = withContext(ioDispatcher) { mutex.withLock { closeLocked() } }

    private fun closeLocked() {
        opened?.database?.close()
        opened = null
    }

    private data class OpenedDatabase(val id: String, val database: ContentDatabase)
}
