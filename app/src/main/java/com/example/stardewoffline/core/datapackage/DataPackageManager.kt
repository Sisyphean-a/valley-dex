package com.example.stardewoffline.core.datapackage

import android.content.Context
import android.util.Log
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.DataPackageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DataPackageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installer: DataPackageInstaller,
    private val validator: DataPackageValidator,
    private val preferences: AppPreferencesRepository,
    private val contentDatabaseManager: ContentDatabaseManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val lifecycleMutex = Mutex()
    private var activeInfo: DataPackageInfo? = null

    suspend fun installAndActivate(
        input: InputStream,
        onStage: (PackageInstallStage) -> Unit = {},
    ): AppResult<DataPackageInfo> {
        val staging = installer.stage(input, onStage)
        val staged = staging.getOrNull()
            ?: return AppResult.Failure(staging.failureOrNull() ?: AppError.Unknown("导入失败"))
        return withContext(ioDispatcher) {
            lifecycleMutex.withLock {
                try {
                    onStage(PackageInstallStage.Preparing)
                    val previous = preferences.current()
                    if (previous.activePackageId == staged.info.id) {
                        contentDatabaseManager.close()
                        activeInfo = null
                    }
                    val isV5 = staged.info.manifest.schemaVersion == DataPackageContract.V5_SCHEMA_VERSION
                    val legacyToPin = if (isV5) {
                        validatedLegacyPinId(previous.activePackageId, previous.pinnedLegacyV4PackageId)
                    } else {
                        null
                    }
                    if (!isV5) {
                        return@withLock AppResult.Failure(AppError.UnsupportedSchema(staged.info.manifest.schemaVersion))
                    }
                    val retainedPreviousId = if (isV5) {
                        if (staged.info.id == previous.activePackageId) {
                            previous.previousCompatibleV5PackageId
                        } else {
                            previous.activePackageId?.takeIf {
                                packageSchemaVersion(it) == DataPackageContract.V5_SCHEMA_VERSION
                            }
                        }
                    } else {
                        null
                    }
                    val retainedBeforeCommit = setOfNotNull(
                        previous.activePackageId,
                        previous.previousPackageId,
                        previous.previousCompatibleV5PackageId,
                        previous.pinnedLegacyV4PackageId,
                        legacyToPin,
                        staged.info.id,
                    )
                    cleanupPackages(retainedBeforeCommit)?.let { error ->
                        if (previous.activePackageId != null && activeInfo == null) openActiveLocked()
                        return@withLock AppResult.Failure(error)
                    }
                    val committed = installer.commit(staged)
                    val installed = committed.getOrNull()
                    if (installed == null) {
                        activeInfo = null
                        if (previous.activePackageId != null) openActiveLocked()
                        return@withLock AppResult.Failure(committed.failureOrNull() ?: AppError.Unknown("无法提交数据包"))
                    }
                    when (val activated = switchToLocked(installed.info.id, retainedPreviousId, installed.info)) {
                        is AppResult.Success -> when (val finalized = installer.finalize(installed)) {
                            is AppResult.Success -> {
                                if (legacyToPin != null) {
                                    preferences.setPinnedLegacyV4Package(legacyToPin)
                                }
                                cleanupPackages(
                                    setOfNotNull(
                                        installed.info.id,
                                        retainedPreviousId,
                                        previous.pinnedLegacyV4PackageId,
                                        legacyToPin,
                                    )
                                )?.let { error ->
                                    Log.e(TAG, "新数据包已启用，但旧数据包清理失败：${error.message}")
                                }
                                preferences.setLastValidatedPackage(installed.info.id)
                                activated
                            }
                            is AppResult.Failure -> {
                                Log.e(TAG, "新数据包已启用，但替换备份清理失败：${finalized.error.message}")
                                preferences.setLastValidatedPackage(installed.info.id)
                                activated
                            }
                        }
                        is AppResult.Failure -> restorePreviousLocked(installed, previous, activated.error)
                    }
                } finally {
                    installer.discard(staged)
                }
            }
        }
    }

    private suspend fun restorePreviousLocked(
        installed: InstalledPackage,
        previous: com.example.stardewoffline.core.datastore.AppPreferences,
        cause: AppError,
    ): AppResult<DataPackageInfo> {
        contentDatabaseManager.close()
        activeInfo = null
        when (val restored = installer.restore(installed)) {
            is AppResult.Failure -> return AppResult.Failure(restored.error)
            is AppResult.Success -> Unit
        }
        preferences.setActivePackage(previous.activePackageId)
        preferences.setPreviousPackage(previous.previousPackageId)
        preferences.setPreviousCompatibleV5Package(previous.previousCompatibleV5PackageId)
        preferences.setPinnedLegacyV4Package(previous.pinnedLegacyV4PackageId)
        preferences.setLastValidatedPackage(previous.lastValidatedPackageId)
        val reopened = previous.activePackageId?.let { openActiveLocked() }
        return if (reopened is AppResult.Failure) AppResult.Failure(reopened.error) else AppResult.Failure(cause)
    }

    /**
     * Flow: serializes package transitions and caches active-package metadata after it is read.
     * Guarantee: callers observe metadata and the SQLite handle for the same active package.
     */
    suspend fun openActive(): AppResult<DataPackageInfo> = withContext(ioDispatcher) {
        lifecycleMutex.withLock { openActiveLocked() }
    }

    /**
     * Flow: pins the activity-package lifecycle while a cross-layer content operation runs.
     * Guarantee: a query cannot combine metadata from one package with rows from another.
     */
    suspend fun <T> withActivePackage(action: suspend (DataPackageInfo) -> AppResult<T>): AppResult<T> {
        val inheritedLease = currentCoroutineContext()[ActivePackageLease]
        if (inheritedLease?.owner === this && inheritedLease.tryAcquire()) {
            return try {
                action(inheritedLease.info)
            } finally {
                inheritedLease.release()
            }
        }
        return withContext(ioDispatcher) {
            lifecycleMutex.withLock {
                val active = openActiveLocked()
                val info = active.getOrNull()
                    ?: return@withLock AppResult.Failure(active.failureOrNull() ?: AppError.NoDataPackage)
                val lease = ActivePackageLease(this@DataPackageManager, info)
                try {
                    withContext(lease) { action(info) }
                } finally {
                    lease.closeAndAwaitNestedReads()
                }
            }
        }
    }

    suspend fun verifyActive(): AppResult<DataPackageInfo> = withContext(ioDispatcher) {
        lifecycleMutex.withLock {
            val current = preferences.current()
            val packageId = current.activePackageId ?: return@withLock AppResult.Failure(AppError.NoDataPackage)
            if (packageSchemaVersion(packageId) != DataPackageContract.V5_SCHEMA_VERSION) {
                deactivateInvalidPackageLocked(current.previousCompatibleV5PackageId)
                return@withLock AppResult.Failure(AppError.UnsupportedSchema(packageSchemaVersion(packageId) ?: -1))
            }
            val verified = validator.validate(packageRoot(packageId))
            if (verified is AppResult.Success) {
                preferences.setLastValidatedPackage(packageId)
                activeInfo = verified.value
            } else {
                deactivateInvalidPackageLocked(
                    current.previousCompatibleV5PackageId ?: current.previousPackageId
                )
            }
            verified
        }
    }

    suspend fun rollback(): AppResult<DataPackageInfo> = withContext(ioDispatcher) {
        lifecycleMutex.withLock {
            val current = preferences.current()
            val previous = current.previousCompatibleV5PackageId
                ?: return@withLock AppResult.Failure(AppError.NoDataPackage)
            switchToLocked(previous, current.activePackageId)
        }
    }

    /**
     * Explicitly records a frozen schema-4 recovery package without activating it.
     * Schema 4 never enters the current App's ordinary install or rollback path.
     */
    suspend fun pinLegacyV4Recovery(packageRoot: File): AppResult<Unit> = withContext(ioDispatcher) {
        lifecycleMutex.withLock {
            val info = validator.validateLegacyRecovery(packageRoot)
                ?: return@withLock AppResult.Failure(AppError.InvalidManifest("旧 v4 恢复包校验失败"))
            if (info.manifest.schemaVersion != 4) {
                return@withLock AppResult.Failure(AppError.UnsupportedSchema(info.manifest.schemaVersion))
            }
            val packageId = info.id
            val source = packageRoot.canonicalFile
            val destination = packageRoot(packageId).canonicalFile
            if (source == destination) {
                return@withLock AppResult.Failure(AppError.InvalidManifest("旧 v4 恢复包不能位于活动包目录"))
            }
            if (destination.exists()) {
                return@withLock AppResult.Failure(AppError.InvalidManifest("旧 v4 恢复包 ID 已存在"))
            }
            if (
                destination.path.startsWith(source.path + File.separator) ||
                source.path.startsWith(destination.path + File.separator)
            ) {
                return@withLock AppResult.Failure(AppError.InvalidManifest("旧 v4 恢复包目录嵌套无效"))
            }
            val staging = File(packagesRoot(), ".legacy-${packageId}.${System.nanoTime()}.staging").canonicalFile
            if (staging.exists()) staging.deleteRecursively()
            staging.parentFile?.mkdirs()
            try {
                source.copyRecursively(staging, overwrite = false)
                if (!staging.renameTo(destination)) {
                    throw IllegalStateException("无法原子提交旧 v4 恢复包")
                }
            } catch (error: Exception) {
                staging.deleteRecursively()
                return@withLock AppResult.Failure(AppError.Unknown(error.message ?: "无法保存旧 v4 恢复包"))
            }
            val current = preferences.current()
            val wasActive = current.activePackageId == packageId
            var preferencesCommitted = false
            try {
                preferences.setLegacyRecoveryState(
                    activePackageId = current.activePackageId?.takeUnless { wasActive },
                    pinnedLegacyV4PackageId = packageId,
                )
                preferencesCommitted = true
                if (wasActive) {
                    contentDatabaseManager.close()
                    activeInfo = null
                }
                AppResult.Success(Unit)
            } catch (error: Exception) {
                if (preferencesCommitted) {
                    runCatching {
                        preferences.setLegacyRecoveryState(
                            activePackageId = current.activePackageId,
                            pinnedLegacyV4PackageId = current.pinnedLegacyV4PackageId,
                        )
                    }
                }
                destination.deleteRecursively()
                AppResult.Failure(AppError.Unknown(error.message ?: "无法保存旧 v4 恢复状态"))
            }
        }
    }

    suspend fun deletePreviousPackage(): AppResult<Unit> = withContext(ioDispatcher) {
        lifecycleMutex.withLock {
            val current = preferences.current()
            val previous = current.previousCompatibleV5PackageId
                ?: current.previousPackageId
                ?: return@withLock AppResult.Success(Unit)
            if (previous == current.pinnedLegacyV4PackageId || packageSchemaVersion(previous) == 4) {
                return@withLock AppResult.Failure(AppError.InvalidManifest("固定旧 v4 数据包只能通过显式发布决定清理"))
            }
            val directory = packageRoot(previous)
            if (directory.exists() && !directory.deleteRecursively()) return@withLock AppResult.Failure(AppError.Unknown("无法删除旧数据包"))
            if (current.previousCompatibleV5PackageId == previous) {
                preferences.setPreviousCompatibleV5Package(null)
            } else {
                preferences.setPreviousPackage(null)
            }
            AppResult.Success(Unit)
        }
    }

    /**
     * Failure: a package that no longer validates is never kept as an active or rollback target.
     * Effect: a previously installed package is restored only after it independently validates and opens.
     */
    private suspend fun deactivateInvalidPackageLocked(previousId: String?) {
        contentDatabaseManager.close()
        activeInfo = null
        preferences.setLastValidatedPackage(null)
        val fallback = previousId?.takeIf { packageSchemaVersion(it) == DataPackageContract.V5_SCHEMA_VERSION }?.let { id ->
            when (val validation = validator.validate(packageRoot(id))) {
                is AppResult.Success -> id to validation.value
                is AppResult.Failure -> null
            }
        }
        if (fallback == null) {
            preferences.setActivePackage(null)
            preferences.setPreviousPackage(null)
            preferences.setPreviousCompatibleV5Package(null)
            return
        }

        val (fallbackId, info) = fallback
        preferences.setActivePackage(fallbackId)
        preferences.setPreviousPackage(null)
        preferences.setPreviousCompatibleV5Package(null)
        if (contentDatabaseManager.openActiveSchema5() is AppResult.Success) {
            activeInfo = info
            preferences.setLastValidatedPackage(fallbackId)
            return
        }
        contentDatabaseManager.close()
        activeInfo = null
        preferences.setActivePackage(null)
    }

    private suspend fun openActiveLocked(): AppResult<DataPackageInfo> {
        val packageId = preferences.current().activePackageId
            ?: return AppResult.Failure(AppError.NoDataPackage)
        activeInfo?.takeIf { it.id == packageId }?.let { return AppResult.Success(it) }
        val root = packageRoot(packageId)
        val manifest = validator.readManifest(root).getOrNull()
            ?: return AppResult.Failure(AppError.InvalidManifest("当前数据包缺少 manifest.json"))
        if (manifest.schemaVersion != DataPackageContract.V5_SCHEMA_VERSION) {
            return AppResult.Failure(AppError.UnsupportedSchema(manifest.schemaVersion))
        }
        val validated = validator.validate(root)
        val info = validated.getOrNull()
            ?: return AppResult.Failure(validated.failureOrNull() ?: AppError.DatabaseCorrupted("当前数据包校验失败"))
        val opened = contentDatabaseManager.openActiveSchema5()
        if (opened is AppResult.Failure) return opened
        val boundInfo = info.copy(id = packageId)
        activeInfo = boundInfo
        return AppResult.Success(boundInfo)
    }

    private suspend fun switchToLocked(
        targetId: String,
        fallbackId: String?,
        knownInfo: DataPackageInfo? = null,
    ): AppResult<DataPackageInfo> {
        val validatedTarget = when (val result = validator.validate(packageRoot(targetId))) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        contentDatabaseManager.close()
        activeInfo = null
        preferences.setActivePackage(targetId)
        val schema = packageSchemaVersion(targetId)
        if (schema != DataPackageContract.V5_SCHEMA_VERSION) {
            preferences.setActivePackage(fallbackId)
            return AppResult.Failure(AppError.UnsupportedSchema(schema ?: -1))
        }
        val opened = contentDatabaseManager.openActiveSchema5()
        if (opened is AppResult.Success) {
            val previous = fallbackId?.takeIf { it != targetId }
            if (knownInfo?.manifest?.schemaVersion == DataPackageContract.V5_SCHEMA_VERSION ||
                packageSchemaVersion(targetId) == DataPackageContract.V5_SCHEMA_VERSION
            ) {
                preferences.setPreviousCompatibleV5Package(previous)
                preferences.setPreviousPackage(null)
            } else {
                preferences.setPreviousPackage(previous)
            }
            if (knownInfo != null) {
                activeInfo = knownInfo.copy(
                    id = targetId,
                    manifest = validatedTarget.manifest,
                    buildMeta = validatedTarget.buildMeta,
                    missingImageCount = validatedTarget.missingImageCount,
                )
                return AppResult.Success(activeInfo!!)
            }
            activeInfo = validatedTarget.copy(id = targetId)
            return AppResult.Success(activeInfo!!)
        }

        preferences.setActivePackage(fallbackId)
        activeInfo = null
        if (fallbackId != null) openActiveLocked()
        return AppResult.Failure((opened as AppResult.Failure).error)
    }

    private fun cleanupPackages(retained: Set<String>): AppError? {
        val stale = packagesRoot().listFiles().orEmpty().filter { it.isDirectory && it.name !in retained }
        return if (stale.any { !it.deleteRecursively() }) AppError.Unknown("无法清理旧数据包") else null
    }

    private suspend fun validatedLegacyPinId(
        activePackageId: String?,
        pinnedLegacyId: String?,
    ): String? {
        val candidate = activePackageId?.takeIf {
            packageSchemaVersion(it) == DataPackageContract.LEGACY_V4_SCHEMA_VERSION && pinnedLegacyId == null
        } ?: return null
        return if (validator.validateLegacyRecovery(packageRoot(candidate)) != null) {
            candidate
        } else {
            Log.w(TAG, "活动 v4 恢复包校验失败，不写入 pinned legacy 状态：$candidate")
            null
        }
    }

    private fun packageSchemaVersion(id: String): Int? = runCatching {
        val root = packageRoot(id)
        if (!File(root, "manifest.json").isFile) return@runCatching null
        validator.readManifest(root).getOrNull()?.schemaVersion
    }.getOrNull()

    private fun packageRoot(id: String): File = File(packagesRoot(), id)
    private fun packagesRoot(): File = File(context.filesDir, "content/packages")
    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error

    /**
     * Guarantee: nested reads share the outer lifecycle lock; child coroutines that entered before
     * the outer action completes are drained before a package switch can close the database handle.
     */
    private class ActivePackageLease(
        val owner: DataPackageManager,
        val info: DataPackageInfo,
    ) : AbstractCoroutineContextElement(Key) {
        private val monitor = Any()
        private var closing = false
        private var nestedReads = 0
        private val drained = CompletableDeferred<Unit>()

        fun tryAcquire(): Boolean = synchronized(monitor) {
            if (closing) false else {
                nestedReads += 1
                true
            }
        }

        fun release() = synchronized(monitor) {
            nestedReads -= 1
            check(nestedReads >= 0) { "活动包读取租约计数异常" }
            if (closing && nestedReads == 0) drained.complete(Unit)
        }

        suspend fun closeAndAwaitNestedReads() {
            val completion = synchronized(monitor) {
                closing = true
                if (nestedReads == 0) null else drained
            }
            completion?.await()
        }

        companion object Key : CoroutineContext.Key<ActivePackageLease>
    }

    private companion object {
        const val TAG = "DataPackageManager"
    }
}
