package com.example.stardewoffline.data

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.model.EntityTypeCount
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val databases: ContentDatabaseManager,
) {
    suspend fun typeCounts(): AppResult<List<EntityTypeCount>> = databaseResult { it.typeCounts() }
    suspend fun summaries(type: String): AppResult<List<EntitySummary>> = databaseResult { it.summariesByType(type) }
    suspend fun summary(id: String): AppResult<EntitySummary?> = databaseResult { it.summary(id) }
    suspend fun summaries(ids: List<String>): AppResult<Map<String, EntitySummary>> = databaseResult { it.summariesByIds(ids) }
    suspend fun detail(id: String): AppResult<EntityDetail?> = databaseResult { it.detail(id) }
    suspend fun aliases(id: String): AppResult<List<String>> = databaseResult { it.aliases(id) }
    suspend fun categories(type: String): AppResult<List<String>> = databaseResult { it.categories(type) }
    suspend fun packageRoot(): File? = databases.openActive().getOrNull()?.packageRoot

    private suspend fun <T> databaseResult(action: suspend (com.example.stardewoffline.core.database.content.ContentDatabase) -> AppResult<T>): AppResult<T> {
        val result = databases.openActive()
        val database = result.getOrNull() ?: return AppResult.Failure((result as? AppResult.Failure)?.error ?: AppError.NoDataPackage)
        return action(database)
    }
}
