package com.example.stardewoffline.data

import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.datapackage.DataPackageManager
import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.model.EntityTypeCount
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val packages: DataPackageManager,
    private val databases: ContentDatabaseManager,
) {
    suspend fun typeCounts(): AppResult<List<EntityTypeCount>> = databaseResult { it.typeCounts() }
    suspend fun summaries(type: String): AppResult<List<EntitySummary>> = databaseResult { it.summariesByType(type) }
    suspend fun summaries(types: Set<String>): AppResult<Map<String, List<EntitySummary>>> = databaseResult { it.summariesByTypes(types) }
    suspend fun details(type: String): AppResult<List<EntityDetail>> = databaseResult { it.detailsByType(type) }
    suspend fun detailsByIds(ids: List<String>): AppResult<List<EntityDetail>> =
        if (ids.isEmpty()) AppResult.Success(emptyList()) else databaseResult { it.detailsByIds(ids) }
    suspend fun supportIds(sourceId: String): AppResult<List<String>> = databaseResult { it.supportIds(sourceId) }
    suspend fun summary(id: String): AppResult<EntitySummary?> = databaseResult { it.summary(id) }
    suspend fun summaries(ids: List<String>): AppResult<Map<String, EntitySummary>> = databaseResult { it.summariesByIds(ids) }
    suspend fun detail(id: String): AppResult<EntityDetail?> = databaseResult { it.detail(id) }
    suspend fun aliases(id: String): AppResult<List<String>> = databaseResult { it.aliases(id) }
    suspend fun categories(type: String): AppResult<List<String>> = databaseResult { it.categories(type) }
    suspend fun packageRoot(): File? = packages.withActivePackage {
        databases.useActive { database -> AppResult.Success(database.packageRoot) }
    }.getOrNull()

    private suspend fun <T> databaseResult(action: suspend (com.example.stardewoffline.core.database.content.ContentDatabase) -> AppResult<T>): AppResult<T> =
        packages.withActivePackage { databases.useActive(action) }
}
