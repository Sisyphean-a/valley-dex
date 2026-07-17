package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class SafeZipExtractor @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun extract(archive: File, destination: File): AppResult<ExtractionResult> = withContext(ioDispatcher) {
        if (archive.length() > MAX_COMPRESSED_BYTES) {
            return@withContext AppResult.Failure(AppError.PackageTooLarge("压缩包超过 512 MiB"))
        }
        if (!destination.mkdirs() && !destination.isDirectory) {
            return@withContext AppResult.Failure(AppError.Unknown("无法创建临时目录"))
        }
        runCatching { unpack(archive, destination.canonicalFile) }
            .fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(toError(it)) },
            )
    }

    private fun unpack(archive: File, root: File): ExtractionResult {
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += 1
                validateEntry(entry.name, root, entries)
                val target = File(root, entry.name).canonicalFile
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    totalBytes = copyEntry(zip, target, totalBytes)
                }
                zip.closeEntry()
            }
        }
        return ExtractionResult(root, entries, totalBytes)
    }

    private fun validateEntry(name: String, root: File, entries: Int) {
        val normalizedName = name.replace('\\', '/')
        if (name.isBlank() || normalizedName.startsWith('/') || normalizedName.split('/').any { it == ".." }) {
            throw UnsafeEntryException(name)
        }
        if (entries > MAX_FILE_COUNT || !File(root, normalizedName).canonicalPath.startsWith(root.path + File.separator)) {
            throw UnsafeEntryException(name)
        }
    }

    private fun copyEntry(input: ZipInputStream, target: File, existingBytes: Long): Long {
        var total = existingBytes
        FileOutputStream(target).buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_UNCOMPRESSED_BYTES) throw PackageLimitException()
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun toError(error: Throwable): AppError = when (error) {
        is UnsafeEntryException -> AppError.UnsafeArchiveEntry(error.entry)
        is PackageLimitException -> AppError.PackageTooLarge("解压后的内容超过 1 GiB")
        else -> AppError.InvalidPackageFormat(error.message ?: "无法解压文件")
    }

    private class UnsafeEntryException(val entry: String) : IllegalArgumentException()
    private class PackageLimitException : IllegalStateException()

    companion object {
        const val MAX_COMPRESSED_BYTES = 512L * 1024 * 1024
        const val MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024
        const val MAX_FILE_COUNT = 10_000
    }
}

data class ExtractionResult(val root: File, val entryCount: Int, val byteCount: Long)
