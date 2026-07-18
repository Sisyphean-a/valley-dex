package com.example.stardewoffline.core.database.content

import android.database.sqlite.SQLiteDatabase
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class ContentDatabaseFactory @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun open(packageRoot: File, databaseFile: File): AppResult<ContentDatabase> = withContext(ioDispatcher) {
        openDatabase(packageRoot, databaseFile, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS, queryOnly = true)
    }

    suspend fun openForValidation(packageRoot: File, databaseFile: File): AppResult<ContentDatabase> = withContext(ioDispatcher) {
        openDatabase(packageRoot, databaseFile, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS, queryOnly = false)
    }

    private fun openDatabase(
        packageRoot: File,
        databaseFile: File,
        flags: Int,
        queryOnly: Boolean,
    ): AppResult<ContentDatabase> {
        if (!databaseFile.isFile) return AppResult.Failure(AppError.DatabaseOpenFailed("数据库文件不存在"))
        return runCatching {
            val database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                flags,
            )
            if (queryOnly) database.execSQL("PRAGMA query_only = ON")
            ContentDatabase(packageRoot, database, ioDispatcher)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(AppError.DatabaseOpenFailed(it.message ?: "打开失败")) },
        )
    }
}
