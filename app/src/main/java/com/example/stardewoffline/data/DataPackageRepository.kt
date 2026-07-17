package com.example.stardewoffline.data

import android.content.Context
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datapackage.DataPackageManager
import com.example.stardewoffline.core.datapackage.PackageInstallStage
import com.example.stardewoffline.core.model.DataPackageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataPackageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: DataPackageManager,
) {
    suspend fun openActive(): AppResult<DataPackageInfo> = manager.openActive()

    suspend fun import(input: InputStream, onStage: (PackageInstallStage) -> Unit): AppResult<DataPackageInfo> =
        manager.installAndActivate(input, onStage)

    suspend fun installDefault(onStage: (PackageInstallStage) -> Unit): AppResult<DataPackageInfo> {
        val defaultName = context.assets.list(DEFAULT_DATA_DIRECTORY)?.firstOrNull { it.endsWith(".svdata") }
            ?: return AppResult.Failure(AppError.NoDataPackage)
        return context.assets.open("$DEFAULT_DATA_DIRECTORY/$defaultName").use { manager.installAndActivate(it, onStage) }
    }

    suspend fun hasDefaultPackage(): Boolean = context.assets.list(DEFAULT_DATA_DIRECTORY)
        ?.any { it.endsWith(".svdata") } == true

    suspend fun verifyActive(): AppResult<DataPackageInfo> = manager.verifyActive()
    suspend fun rollback(): AppResult<DataPackageInfo> = manager.rollback()

    private companion object {
        const val DEFAULT_DATA_DIRECTORY = "default-data"
    }
}
