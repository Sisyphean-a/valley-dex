package com.example.stardewoffline.core.database.content

import android.content.Context
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.DataManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.serialization.json.Json
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
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var openedSchema5: OpenedSchema5? = null

    internal suspend fun openActiveSchema5(): AppResult<Schema5ContentDatabase> = withContext(ioDispatcher) {
        mutex.withLock { openSchema5Locked() }
    }

    suspend fun <T> useActiveSchema5(action: suspend (Schema5ContentDatabase) -> AppResult<T>): AppResult<T> = withContext(ioDispatcher) {
        mutex.withLock {
            when (val opened = openSchema5Locked()) {
                is AppResult.Success -> action(opened.value)
                is AppResult.Failure -> AppResult.Failure(opened.error)
            }
        }
    }

    suspend fun activePackageRoot(): File? = withContext(ioDispatcher) {
        mutex.withLock {
            openedSchema5?.database?.packageRoot
        }
    }

    suspend fun close() = withContext(ioDispatcher) { mutex.withLock { closeLocked() } }

    private suspend fun openSchema5Locked(): AppResult<Schema5ContentDatabase> {
        val packageId = preferences.current().activePackageId
            ?: return AppResult.Failure(AppError.NoDataPackage)
        openedSchema5?.takeIf { it.id == packageId }?.let { return AppResult.Success(it.database) }
        closeLocked()
        val root = File(context.filesDir, "content/packages/$packageId")
        val manifestFile = File(root, "manifest.json")
        if (!manifestFile.isFile) return AppResult.Failure(AppError.InvalidManifest("当前数据包缺少 manifest.json"))
        val manifest = runCatching {
            json.decodeFromString<DataManifest>(manifestFile.readText())
        }.getOrElse { return AppResult.Failure(AppError.InvalidManifest("当前数据包 manifest.json 无效")) }
        if (manifest.schemaVersion != 5) {
            return AppResult.Failure(AppError.UnsupportedSchema(manifest.schemaVersion))
        }
        if (!manifest.publishable) {
            return AppResult.Failure(AppError.NotPublishable)
        }
        val databaseName = manifest.database.file
        val databaseFile = File(root, databaseName).canonicalFile
        if (!databaseFile.path.startsWith(root.canonicalPath + File.separator)) {
            return AppResult.Failure(AppError.InvalidManifest("数据库路径越界"))
        }
        val result = factory.openSchema5(root, databaseFile)
        if (result is AppResult.Success) openedSchema5 = OpenedSchema5(packageId, result.value)
        return result
    }

    private fun closeLocked() {
        openedSchema5?.database?.close()
        openedSchema5 = null
    }

    private data class OpenedSchema5(val id: String, val database: Schema5ContentDatabase)
}
