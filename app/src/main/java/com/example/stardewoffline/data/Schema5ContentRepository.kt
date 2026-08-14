package com.example.stardewoffline.data

import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.database.content.Schema5ContentDatabase
import com.example.stardewoffline.core.model.Schema5BrowsePage
import com.example.stardewoffline.core.model.Schema5EntityDetail
import com.example.stardewoffline.core.model.Schema5EntitySummary
import com.example.stardewoffline.data.SearchQueryNormalizer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Schema5ContentRepository @Inject constructor(
    private val databases: ContentDatabaseManager,
) {
    suspend fun typeCounts() = use { it.typeCounts() }

    suspend fun summaries(type: String): AppResult<List<Schema5EntitySummary>> = use { it.summariesByType(type) }

    suspend fun summaries(types: Set<String>): AppResult<Map<String, List<Schema5EntitySummary>>> = use {
        it.summariesByTypes(types)
    }

    suspend fun summariesByFacet(
        types: Set<String>,
        values: Set<String>,
    ): AppResult<Map<String, List<Schema5EntitySummary>>> = use {
        it.summariesByTypes(types, values)
    }

    suspend fun browse(
        types: Set<String>,
        facetFilters: Map<String, Set<String>> = emptyMap(),
        keyword: String? = null,
        cursor: String? = null,
        pageSize: Int = 60,
    ): AppResult<Schema5BrowsePage> {
        val normalized = keyword?.let(SearchQueryNormalizer::normalize)
        val ftsQuery = normalized?.ftsQuery ?: normalized?.normalized
        return use { database ->
            database.browseByTypes(types, facetFilters, ftsQuery, cursor, pageSize)
        }
    }

    suspend fun summaries(ids: List<String>): AppResult<Map<String, Schema5EntitySummary>> = use {
        it.summariesByIds(ids)
    }

    suspend fun reverseRelations(entityId: String) = use { it.reverseRelations(entityId) }

    suspend fun detailsByIds(ids: List<String>): AppResult<List<Schema5EntityDetail>> =
        if (ids.isEmpty()) AppResult.Success(emptyList()) else use { it.detailsByIds(ids) }

    suspend fun summary(id: String): AppResult<Schema5EntitySummary?> = use { it.summary(id) }

    suspend fun detail(id: String): AppResult<Schema5EntityDetail?> = use { it.detail(id) }

    suspend fun aliases(id: String): AppResult<List<String>> = use { it.aliases(id) }

    suspend fun packageRoot(): File? = databases.activePackageRoot()

    suspend fun search(
        query: String,
        entityTypes: Set<String> = emptySet(),
    ): AppResult<List<com.example.stardewoffline.core.model.Schema5SearchResult>> {
        if (SearchQueryNormalizer.normalize(query) == null) return AppResult.Success(emptyList())
        val results = mutableListOf<com.example.stardewoffline.core.model.Schema5SearchResult>()
        var cursor: String? = null
        do {
            val page = when (val result = searchPage(query, entityTypes, cursor, SEARCH_PAGE_SIZE)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return result
            }
            results += page.results
            cursor = page.nextCursor
        } while (cursor != null)
        return AppResult.Success(results.toList())
    }

    suspend fun searchPage(
        query: String,
        entityTypes: Set<String> = emptySet(),
        cursor: String? = null,
        pageSize: Int = SEARCH_PAGE_SIZE,
    ) = SearchQueryNormalizer.normalize(query)?.let { normalized ->
        use {
            it.searchPage(
                normalized.ftsQuery ?: normalized.normalized,
                pageSize,
                entityTypes,
                cursor,
            )
        }
    } ?: AppResult.Success(com.example.stardewoffline.core.model.Schema5SearchPage(emptyList(), null))

    private suspend fun <T> use(action: suspend (Schema5ContentDatabase) -> AppResult<T>): AppResult<T> =
        databases.useActiveSchema5(action)

    private companion object { const val SEARCH_PAGE_SIZE = 60 }
}
