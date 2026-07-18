package com.example.stardewoffline.core.datapackage

import android.content.Context
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.DataPackageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataPackageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installer: DataPackageInstaller,
    private val validator: DataPackageValidator,
    private val preferences: AppPreferencesRepository,
    private val contentDatabaseManager: ContentDatabaseManager,
) {
    suspend fun installAndActivate(
        input: InputStream,
        onStage: (PackageInstallStage) -> Unit = {},
    ): AppResult<DataPackageInfo> {
        val installation = installer.install(input, onStage)
        val installed = installation.getOrNull()
            ?: return AppResult.Failure(installation.failureOrNull() ?: AppError.Unknown("导入失败"))
        onStage(PackageInstallStage.Preparing)
        return activate(installed.info)
    }

    suspend fun openActive(): AppResult<DataPackageInfo> {
        val openResult = contentDatabaseManager.openActive()
        val database = openResult.getOrNull()
            ?: return AppResult.Failure(openResult.failureOrNull() ?: AppError.NoDataPackage)
        val packageId = preferences.current().activePackageId ?: return AppResult.Failure(AppError.NoDataPackage)
        val manifest = validator.readManifest(packageRoot(packageId)).getOrNull()
            ?: return AppResult.Failure(AppError.InvalidManifest("当前数据包缺少 manifest.json"))
        val meta = database.getBuildMeta().getOrNull()
            ?: return AppResult.Failure(AppError.DatabaseQueryFailed("无法读取当前数据版本"))
        return AppResult.Success(DataPackageInfo(packageId, manifest, meta, missingImageCount = 0))
    }

    suspend fun verifyActive(): AppResult<DataPackageInfo> {
        val packageId = preferences.current().activePackageId ?: return AppResult.Failure(AppError.NoDataPackage)
        val verified = validator.validate(packageRoot(packageId))
        if (verified is AppResult.Success) preferences.setLastValidatedPackage(packageId)
        return verified
    }

    suspend fun rollback(): AppResult<DataPackageInfo> {
        val current = preferences.current()
        val previous = current.previousPackageId ?: return AppResult.Failure(AppError.NoDataPackage)
        return switchTo(previous, current.activePackageId)
    }

    suspend fun deletePreviousPackage(): AppResult<Unit> {
        val previous = preferences.current().previousPackageId ?: return AppResult.Success(Unit)
        val directory = packageRoot(previous)
        if (directory.exists() && !directory.deleteRecursively()) return AppResult.Failure(AppError.Unknown("无法删除旧数据包"))
        preferences.setPreviousPackage(null)
        return AppResult.Success(Unit)
    }

    private suspend fun activate(info: DataPackageInfo): AppResult<DataPackageInfo> {
        val oldId = preferences.current().activePackageId
        return switchTo(info.id, oldId, info)
    }

    private suspend fun switchTo(
        targetId: String,
        fallbackId: String?,
        knownInfo: DataPackageInfo? = null,
    ): AppResult<DataPackageInfo> {
        contentDatabaseManager.close()
        preferences.setActivePackage(targetId)
        val opened = contentDatabaseManager.openActive()
        if (opened is AppResult.Success) {
            preferences.setPreviousPackage(fallbackId?.takeIf { it != targetId })
            cleanupPackages(targetId, fallbackId)
            return if (knownInfo != null) AppResult.Success(knownInfo) else openActive()
        }
        preferences.setActivePackage(fallbackId)
        if (fallbackId != null) contentDatabaseManager.openActive()
        return AppResult.Failure((opened as AppResult.Failure).error)
    }

    private fun cleanupPackages(activeId: String, previousId: String?) {
        val retained = setOfNotNull(activeId, previousId)
        packagesRoot().listFiles()?.filter { it.isDirectory && it.name !in retained }?.forEach(File::deleteRecursively)
    }

    private fun packageRoot(id: String): File = File(packagesRoot(), id)
    private fun packagesRoot(): File = File(context.filesDir, "content/packages")
    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error
}
