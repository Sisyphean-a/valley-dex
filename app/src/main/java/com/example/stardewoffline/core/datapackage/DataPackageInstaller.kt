package com.example.stardewoffline.core.datapackage

import android.content.Context
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.DataPackageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class DataPackageInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: SafeZipExtractor,
    private val validator: DataPackageValidator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun install(
        input: InputStream,
        onStage: (PackageInstallStage) -> Unit = {},
    ): AppResult<InstalledPackage> = withContext(ioDispatcher) {
        val archive = File.createTempFile("stardew-import-", ".svdata", context.cacheDir)
        val staging = File(contentRoot(), "staging/${UUID.randomUUID()}")
        try {
            onStage(PackageInstallStage.Copying)
            copyInput(input, archive)
            onStage(PackageInstallStage.Extracting)
            extractor.extract(archive, staging).failureOrNull()?.let { return@withContext AppResult.Failure(it) }
            onStage(PackageInstallStage.Validating)
            val validation = validator.validate(staging)
            val info = validation.getOrNull() ?: return@withContext AppResult.Failure(
                validation.failureOrNull() ?: AppError.Unknown("数据包校验失败"),
            )
            onStage(PackageInstallStage.Preparing)
            AppResult.Success(InstalledPackage(moveToPackages(staging, info.id), info))
        } catch (error: PackageLimitException) {
            AppResult.Failure(AppError.PackageTooLarge("压缩包超过 512 MiB"))
        } catch (error: Exception) {
            AppResult.Failure(AppError.Unknown(error.message ?: "导入失败"))
        } finally {
            archive.delete()
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun copyInput(input: InputStream, target: File) {
        var copied = 0L
        input.use { source ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > SafeZipExtractor.MAX_COMPRESSED_BYTES) throw PackageLimitException()
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun moveToPackages(staging: File, packageId: String): File {
        val destination = File(contentRoot(), "packages/$packageId")
        destination.parentFile?.mkdirs()
        if (destination.isDirectory) return destination
        runCatching { Files.move(staging.toPath(), destination.toPath(), ATOMIC_MOVE) }
            .recoverCatching { Files.move(staging.toPath(), destination.toPath(), REPLACE_EXISTING) }
            .getOrThrow()
        return destination
    }

    private fun contentRoot(): File = File(context.filesDir, "content")

    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error
    private class PackageLimitException : IllegalStateException()
}

data class InstalledPackage(val root: File, val info: DataPackageInfo)

enum class PackageInstallStage(val message: String) {
    Copying("正在复制数据包"),
    Extracting("正在安全解压"),
    Validating("正在校验数据库"),
    Preparing("正在准备启用数据"),
}
