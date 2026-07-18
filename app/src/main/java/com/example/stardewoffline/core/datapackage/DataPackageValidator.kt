package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseFactory
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.DataPackageInfo
import com.example.stardewoffline.core.model.ManifestContent
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
        val validationCopy = File.createTempFile("stardew-validation-", ".db")
        databaseFile.copyTo(validationCopy, overwrite = true)
        val databaseResult = databaseFactory.openForValidation(root, validationCopy)
        val database = databaseResult.getOrNull()
            ?: run {
                validationCopy.delete()
                return AppResult.Failure(databaseResult.failureOrNull() ?: AppError.DatabaseOpenFailed("校验打开失败"))
            }
        return try {
            database.quickCheck().failureOrNull()?.let { return AppResult.Failure(it) }
            val meta = database.getBuildMeta().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取 build_meta"))
            validateMeta(meta, manifest)?.let { return AppResult.Failure(it) }
            val count = database.entityCount().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取实体数量"))
            if (count != meta.entityCount || count != manifest.content.entities) {
                return AppResult.Failure(AppError.DatabaseCorrupted("实体数量与清单不一致"))
            }
            validateEntityTypes(database, manifest.content)?.let { return AppResult.Failure(it) }
            val searchCount = database.searchCount().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取搜索索引数量"))
            if (searchCount < count * MIN_SEARCH_INDEX_RATIO) {
                return AppResult.Failure(AppError.DatabaseCorrupted("搜索索引数量异常"))
            }
            val paths = database.imagePaths().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法校验图片路径"))
            paths.forEach { imagePath ->
                val imageFile = resolveInside(root, imagePath)
                    ?: return AppResult.Failure(AppError.UnsafeArchiveEntry(imagePath))
                if (!imageFile.isFile) return AppResult.Failure(AppError.ImageMissing(imagePath))
            }
            AppResult.Success(DataPackageInfo(manifest.database.sha256, manifest, meta, missingImageCount = 0))
        } finally {
            database.close()
            validationCopy.delete()
        }
    }

    private fun validateMeta(meta: com.example.stardewoffline.core.model.BuildMeta, manifest: DataManifest): AppError? {
        val artifact = meta.artifactMetadata
        return when {
            meta.schemaVersion != manifest.schemaVersion -> AppError.MetadataMismatch("build_meta.schema_version")
            meta.locale != manifest.language -> AppError.MetadataMismatch("build_meta.locale")
            meta.entityCount != manifest.content.entities -> AppError.MetadataMismatch("build_meta.entity_count")
            meta.builderVersion != manifest.builderVersion -> AppError.MetadataMismatch("build_meta.builder_version")
            meta.generatedAt != manifest.generatedAt -> AppError.MetadataMismatch("build_meta.generated_at")
            meta.gameVersion != manifest.gameVersion -> AppError.MetadataMismatch("build_meta.game_version")
            meta.sourceHash != manifest.sourceHash -> AppError.MetadataMismatch("build_meta.source_hash")
            artifact.schemaVersion != manifest.schemaVersion -> AppError.MetadataMismatch("artifact_metadata.schemaVersion")
            artifact.language != manifest.language -> AppError.MetadataMismatch("artifact_metadata.language")
            artifact.builderVersion != manifest.builderVersion -> AppError.MetadataMismatch("artifact_metadata.builderVersion")
            artifact.generatedAt != manifest.generatedAt -> AppError.MetadataMismatch("artifact_metadata.generatedAt")
            artifact.gameVersion != manifest.gameVersion -> AppError.MetadataMismatch("artifact_metadata.gameVersion")
            artifact.sourceHash != manifest.sourceHash -> AppError.MetadataMismatch("artifact_metadata.sourceHash")
            artifact.publishable != manifest.publishable -> AppError.MetadataMismatch("artifact_metadata.publishable")
            artifact.content != manifest.content -> AppError.MetadataMismatch("artifact_metadata.content")
            artifact.quality != manifest.quality -> AppError.MetadataMismatch("artifact_metadata.quality")
            else -> null
        }
    }

    private suspend fun validateEntityTypes(
        database: com.example.stardewoffline.core.database.content.ContentDatabase,
        content: ManifestContent,
    ): AppError? {
        val declared = declaredTypeCounts(content) ?: return AppError.InvalidEntityTypeCatalog("声明统计不一致")
        val actual = database.typeCounts().getOrNull()
            ?.associate { it.type to it.count }
            ?: return AppError.DatabaseCorrupted("无法读取实体类型统计")
        return if (actual == declared) null else AppError.InvalidEntityTypeCatalog("类型统计与数据库不一致")
    }

    private fun declaredTypeCounts(content: ManifestContent): Map<String, Int>? {
        val catalog = content.entityTypes
        if (catalog.isEmpty() || catalog.any { it.id.isBlank() || it.displayName.isBlank() || it.count <= 0 }) return null
        val catalogCounts = catalog.associate { it.id to it.count }
        if (catalogCounts.size != catalog.size || catalogCounts.values.sum() != content.entities) return null
        val basic = mapOf("object" to content.objects, "crop" to content.crops, "fish" to content.fish, "villager" to content.villagers)
            .filterValues { it > 0 }
        if (content.extraCounts.any { (id, count) -> id.isBlank() || count <= 0 || id in basic }) return null
        return (basic + content.extraCounts).takeIf { it == catalogCounts }
    }

    private fun resolveInside(root: File, path: String): File? = runCatching {
        File(root, path).canonicalFile.takeIf { it.path.startsWith(root.canonicalPath + File.separator) }
    }.getOrNull()

    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error

    companion object {
        const val MIN_SEARCH_INDEX_RATIO = 0.8
    }
}
