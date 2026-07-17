package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseFactory
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.DataPackageInfo
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class DataPackageValidator @Inject constructor(
    private val json: Json,
    private val hashUtils: HashUtils,
    private val databaseFactory: ContentDatabaseFactory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun validate(packageRoot: File): AppResult<DataPackageInfo> = withContext(ioDispatcher) {
        val manifestResult = readManifest(packageRoot)
        val manifest = manifestResult.getOrNull()
            ?: return@withContext AppResult.Failure(manifestResult.failureOrNull() ?: AppError.InvalidManifest("无法读取 manifest.json"))
        DataPackageContract.validateManifest(manifest)?.let { return@withContext AppResult.Failure(it) }
        val databaseFile = resolveInside(packageRoot, manifest.database.file)
            ?: return@withContext AppResult.Failure(AppError.InvalidManifest("数据库路径越界"))
        if (!databaseFile.isFile) return@withContext AppResult.Failure(AppError.InvalidManifest("数据库文件不存在"))
        if (!hashUtils.sha256(databaseFile).equals(manifest.database.sha256, ignoreCase = true)) {
            return@withContext AppResult.Failure(AppError.HashMismatch)
        }
        validateDatabase(packageRoot, databaseFile, manifest)
    }

    fun readManifest(packageRoot: File): AppResult<DataManifest> = runCatching {
        val file = File(packageRoot, "manifest.json")
        AppResult.Success(json.decodeFromString<DataManifest>(file.readText()))
    }.getOrElse { AppResult.Failure(AppError.InvalidManifest(it.message ?: "无法解析 JSON")) }

    private suspend fun validateDatabase(
        root: File,
        databaseFile: File,
        manifest: DataManifest,
    ): AppResult<DataPackageInfo> {
        val databaseResult = databaseFactory.open(root, databaseFile)
        val database = databaseResult.getOrNull()
            ?: return AppResult.Failure(databaseResult.failureOrNull() ?: AppError.DatabaseOpenFailed("只读打开失败"))
        return try {
            database.quickCheck().failureOrNull()?.let { return AppResult.Failure(it) }
            val meta = database.getBuildMeta().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取 build_meta"))
            validateMeta(meta.schemaVersion, meta.locale, meta.entityCount, manifest)?.let { return AppResult.Failure(it) }
            val count = database.entityCount().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取实体数量"))
            if (count != meta.entityCount || count != manifest.content.entities) {
                return AppResult.Failure(AppError.DatabaseCorrupted("实体数量与清单不一致"))
            }
            val searchCount = database.searchCount().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取搜索索引数量"))
            if (searchCount < count * MIN_SEARCH_INDEX_RATIO) {
                return AppResult.Failure(AppError.DatabaseCorrupted("搜索索引数量异常"))
            }
            val paths = database.imagePaths().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法校验图片路径"))
            val missingImages = paths.count { imagePath ->
                val imageFile = resolveInside(root, imagePath)
                    ?: return AppResult.Failure(AppError.UnsafeArchiveEntry(imagePath))
                !imageFile.isFile
            }
            AppResult.Success(DataPackageInfo(manifest.database.sha256, manifest, meta, missingImages))
        } finally {
            database.close()
        }
    }

    private fun validateMeta(schema: Int, locale: String, count: Int, manifest: DataManifest): AppError? = when {
        schema != manifest.schemaVersion -> AppError.DatabaseCorrupted("schema_version 不匹配")
        locale != manifest.language -> AppError.DatabaseCorrupted("locale 不匹配")
        count <= 0 -> AppError.DatabaseCorrupted("entity_count 无效")
        else -> null
    }

    private fun resolveInside(root: File, path: String): File? = runCatching {
        File(root, path).canonicalFile.takeIf { it.path.startsWith(root.canonicalPath + File.separator) }
    }.getOrNull()

    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error

    companion object {
        const val MIN_SEARCH_INDEX_RATIO = 0.8
    }
}
