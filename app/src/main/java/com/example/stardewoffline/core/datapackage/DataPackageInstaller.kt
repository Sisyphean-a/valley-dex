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
    /**
     * Flow: copy, extract, and validate outside the active-package directory.
     * Guarantee: an invalid or interrupted import cannot alter a readable package.
     */
    internal suspend fun stage(
        input: InputStream,
        onStage: (PackageInstallStage) -> Unit = {},
    ): AppResult<StagedPackage> = withContext(ioDispatcher) {
        val archive = File.createTempFile("stardew-import-", ".svdata", context.cacheDir)
        val staging = File(contentRoot(), "staging/${UUID.randomUUID()}")
        var retained = false
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
            retained = true
            AppResult.Success(StagedPackage(staging, info))
        } catch (error: PackageLimitException) {
            AppResult.Failure(AppError.PackageTooLarge("压缩包超过 512 MiB"))
        } catch (error: Exception) {
            AppResult.Failure(AppError.Unknown(error.message ?: "导入失败"))
        } finally {
            archive.delete()
            if (!retained && staging.exists()) staging.deleteRecursively()
        }
    }

    /**
     * Flow: atomically replace an existing package only after staging validation has succeeded.
     * Guarantee: retains the prior directory until the lifecycle manager has opened the replacement.
     */
    internal suspend fun commit(staged: StagedPackage): AppResult<InstalledPackage> = withContext(ioDispatcher) {
        val destination = File(contentRoot(), "packages/${staged.info.id}")
        val backup = File(contentRoot(), "staging/backup-${UUID.randomUUID()}")
        try {
            destination.parentFile?.mkdirs()
            val backupRoot = backup.takeIf { destination.exists() }?.also { move(destination, it) }
            try {
                move(staged.root, destination)
            } catch (error: Exception) {
                if (destination.exists() && !destination.deleteRecursively()) throw error
                if (backupRoot != null) move(backupRoot, destination)
                throw error
            }
            AppResult.Success(InstalledPackage(destination, staged.info, backupRoot))
        } catch (error: Exception) {
            AppResult.Failure(AppError.Unknown(error.message ?: "无法提交数据包"))
        }
    }

    internal suspend fun finalize(installed: InstalledPackage): AppResult<Unit> = withContext(ioDispatcher) {
        val backup = installed.backupRoot ?: return@withContext AppResult.Success(Unit)
        if (!backup.exists() || backup.deleteRecursively()) AppResult.Success(Unit)
        else AppResult.Failure(AppError.Unknown("无法清理已替换数据包的备份"))
    }

    /** Failure: restores the pre-import directory when activation or cleanup cannot complete. */
    internal suspend fun restore(installed: InstalledPackage): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            if (installed.root.exists() && !installed.root.deleteRecursively()) {
                return@withContext AppResult.Failure(AppError.Unknown("无法移除未启用的新数据包"))
            }
            installed.backupRoot?.takeIf(File::exists)?.let { move(it, installed.root) }
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(AppError.Unknown(error.message ?: "无法恢复原数据包"))
        }
    }

    internal suspend fun discard(staged: StagedPackage) = withContext(ioDispatcher) {
        if (staged.root.exists() && !staged.root.deleteRecursively()) {
            throw IllegalStateException("无法清理导入临时目录")
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

    private fun move(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        runCatching { Files.move(source.toPath(), destination.toPath(), ATOMIC_MOVE) }
            .recoverCatching { Files.move(source.toPath(), destination.toPath(), REPLACE_EXISTING) }
            .getOrThrow()
    }

    private fun contentRoot(): File = File(context.filesDir, "content")

    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error
    private class PackageLimitException : IllegalStateException()
}

internal data class StagedPackage(val root: File, val info: DataPackageInfo)
internal data class InstalledPackage(
    val root: File,
    val info: DataPackageInfo,
    val backupRoot: File?,
)

enum class PackageInstallStage(val message: String) {
    Copying("正在复制数据包"),
    Extracting("正在安全解压"),
    Validating("正在校验数据库"),
    Preparing("正在准备启用数据"),
}
