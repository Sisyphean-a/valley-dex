package com.example.stardewoffline.data

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseManager
import com.example.stardewoffline.core.model.SearchDocument
import com.example.stardewoffline.core.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(private val databases: ContentDatabaseManager) {
    suspend fun search(raw: String): AppResult<List<SearchResult>> {
        val query = SearchQueryNormalizer.normalize(raw) ?: return AppResult.Success(emptyList())
        val open = databases.openActive()
        val database = open.getOrNull() ?: return AppResult.Failure((open as? AppResult.Failure)?.error ?: AppError.NoDataPackage)
        val prefix = when (val result = database.searchPrefix(query, LIMIT)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val aliases = when (val result = database.searchAliases(query.normalized, LIMIT)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val fts = query.ftsQuery?.let { ftsQuery ->
            when (val result = database.searchFts(ftsQuery, LIMIT)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> return result
            }
        }.orEmpty()
        return AppResult.Success(score(prefix, aliases, fts, query.normalized))
    }

    private fun score(prefix: List<SearchDocument>, aliases: List<com.example.stardewoffline.core.model.EntitySummary>, fts: List<com.example.stardewoffline.core.model.EntitySummary>, query: String): List<SearchResult> {
        val results = mutableMapOf<String, SearchResult>()
        prefix.forEach { document -> putBest(results, scoreDocument(document, query)) }
        aliases.forEach { putBest(results, SearchResult(it, 850, "别名")) }
        fts.forEach { putBest(results, SearchResult(it, 500, "全文")) }
        return results.values.sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.summary.sortKey ?: it.summary.nameZh }).take(LIMIT)
    }

    private fun scoreDocument(document: SearchDocument, query: String): SearchResult {
        val summary = document.summary
        return when {
            summary.nameZh == query -> SearchResult(summary, 1000, "中文名")
            summary.nameZh.startsWith(query) -> SearchResult(summary, 900, "中文名前缀")
            summary.nameEn.equals(query, true) -> SearchResult(summary, 800, "英文名")
            summary.nameEn?.startsWith(query, true) == true -> SearchResult(summary, 750, "英文名前缀")
            document.initials == query -> SearchResult(summary, 700, "拼音首字母")
            document.initials?.startsWith(query) == true -> SearchResult(summary, 650, "拼音首字母")
            else -> SearchResult(summary, 600, "拼音")
        }
    }

    private fun putBest(results: MutableMap<String, SearchResult>, candidate: SearchResult) {
        if ((results[candidate.summary.id]?.score ?: Int.MIN_VALUE) < candidate.score) results[candidate.summary.id] = candidate
    }

    private companion object { const val LIMIT = 60 }
}
