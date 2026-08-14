package com.example.stardewoffline.core.database.content

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.model.ArtifactMetadata
import com.example.stardewoffline.core.model.BuildMeta
import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.model.EntityTypeCount
import com.example.stardewoffline.core.model.SearchDocument
import com.example.stardewoffline.core.model.SearchQuery
import com.example.stardewoffline.core.model.TranslationStatus
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.decodeFromString

/** Schema-4 recovery validator adapter; never used by the schema-5 product path. */
internal class ContentDatabase internal constructor(
    val packageRoot: File,
    private val database: SQLiteDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun quickCheck(): AppResult<Unit> = query("PRAGMA quick_check") { cursor ->
        val results = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        if (results == listOf("ok")) AppResult.Success(Unit)
        else AppResult.Failure(AppError.DatabaseCorrupted(results.joinToString()))
    }

    suspend fun getBuildMeta(): AppResult<BuildMeta> = query("SELECT key, value FROM build_meta") { cursor ->
        val values = buildMap {
            while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
        }
        val required = listOf("schema_version", "builder_version", "locale", "generated_at", "entity_count", "game_version", "source_hash", "artifact_metadata")
        val missing = required.filterNot(values::containsKey)
        if (missing.isNotEmpty()) return@query AppResult.Failure(AppError.DatabaseCorrupted("缺少元数据：${missing.joinToString()}"))
        AppResult.Success(
            BuildMeta(
                schemaVersion = values.getValue("schema_version").toIntOrNull()
                    ?: return@query AppResult.Failure(AppError.DatabaseCorrupted("schema_version 不是数字")),
                builderVersion = values.getValue("builder_version"),
                locale = values.getValue("locale"),
                generatedAt = values.getValue("generated_at"),
                entityCount = values.getValue("entity_count").toIntOrNull()
                    ?: return@query AppResult.Failure(AppError.DatabaseCorrupted("entity_count 不是数字")),
                gameVersion = values.getValue("game_version"),
                sourceHash = values.getValue("source_hash"),
                artifactMetadata = parseArtifactMetadata(values.getValue("artifact_metadata"))
                    ?: return@query AppResult.Failure(AppError.DatabaseCorrupted("artifact_metadata 无效")),
            ),
        )
    }

    suspend fun entityCount(): AppResult<Int> = count("entities")

    suspend fun searchCount(): AppResult<Int> = count("entity_search")

    suspend fun imagePaths(): AppResult<List<String>> = query("SELECT image_path FROM entities WHERE image_path IS NOT NULL") { cursor ->
        AppResult.Success(buildList { while (cursor.moveToNext()) add(cursor.getString(0)) })
    }

    suspend fun typeCounts(): AppResult<List<EntityTypeCount>> = query(
        "SELECT entity_type, COUNT(*) FROM entities GROUP BY entity_type ORDER BY entity_type",
    ) { cursor ->
        AppResult.Success(buildList { while (cursor.moveToNext()) add(EntityTypeCount(cursor.getString(0), cursor.getInt(1))) })
    }

    suspend fun summary(id: String): AppResult<EntitySummary?> = query(SUMMARY_BY_ID, arrayOf(id)) { cursor ->
        AppResult.Success(if (cursor.moveToFirst()) cursor.toSummary() else null)
    }

    suspend fun summariesByType(type: String): AppResult<List<EntitySummary>> = query(SUMMARIES_BY_TYPE, arrayOf(type)) { cursor ->
        AppResult.Success(readSummaries(cursor))
    }

    suspend fun summariesByTypes(types: Set<String>): AppResult<Map<String, List<EntitySummary>>> {
        val sortedTypes = types.toList().sorted()
        if (sortedTypes.isEmpty()) return AppResult.Success(emptyMap())
        if (sortedTypes.size > MAX_BIND_PARAMETERS) return AppResult.Failure(AppError.DatabaseQueryFailed("分类实体类型过多"))
        val placeholders = sortedTypes.joinToString(",") { "?" }
        return query(
            "$SUMMARY_COLUMNS WHERE e.entity_type IN ($placeholders) ORDER BY e.entity_type, CASE WHEN s.pinyin IS NULL OR s.pinyin = '' THEN 1 ELSE 0 END, s.pinyin COLLATE NOCASE, e.name_zh COLLATE NOCASE",
            sortedTypes.toTypedArray(),
        ) { cursor ->
            AppResult.Success(readSummaries(cursor).groupBy(EntitySummary::entityType))
        }
    }

    suspend fun detailsByType(type: String): AppResult<List<EntityDetail>> = query(
        "SELECT * FROM entities WHERE entity_type = ? ORDER BY id COLLATE NOCASE",
        arrayOf(type),
    ) { cursor -> readDetails(cursor) }

    suspend fun detailsByIds(ids: List<String>): AppResult<List<EntityDetail>> {
        if (ids.isEmpty()) return AppResult.Success(emptyList())
        val placeholders = ids.joinToString(",") { "?" }
        return query(
            "SELECT * FROM entities WHERE id IN ($placeholders) ORDER BY id COLLATE NOCASE",
            ids.toTypedArray(),
        ) { cursor -> readDetails(cursor) }
    }

    /**
     * Flow: use indexed identity/source columns to find only this villager's support rows;
     * detail JSON is read separately in one parameterized batch.
     */
    suspend fun supportIds(sourceId: String): AppResult<List<String>> {
        if (sourceId.isBlank()) return AppResult.Success(emptyList())
        val names = listOf(sourceId, "LeoMainland".takeIf { sourceId.equals("Leo", ignoreCase = true) })
            .filterNotNull().distinct()
        if (names.isEmpty()) return AppResult.Success(emptyList())
        val predicates = names.flatMap { name ->
            val scheduleId = "npc_schedule:$name"
            val giftId = "villager_gift:$name"
            listOf(
                "(entity_type = 'npc_schedule' AND (id = ? OR id LIKE ? OR id LIKE ? OR id LIKE ? OR source_file = ? OR source_file LIKE ?))",
                "(entity_type = 'villager_gift' AND (id = ? OR id LIKE ? OR id LIKE ? OR id LIKE ?))",
            )
        }
        val args = names.flatMap { name ->
            val scheduleId = "npc_schedule:$name"
            val giftId = "villager_gift:$name"
            listOf(
                scheduleId, "$scheduleId:%", "$scheduleId/%", "$scheduleId|%",
                "Characters/schedules/$name.json", "%/schedules/$name.json",
                giftId, "$giftId:%", "$giftId/%", "$giftId|%",
            )
        }
        return query(
            "SELECT id FROM entities WHERE ${predicates.joinToString(" OR ")} ORDER BY id COLLATE NOCASE",
            args.toTypedArray(),
        ) { cursor -> AppResult.Success(buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }) }
    }

    suspend fun summariesByIds(ids: List<String>): AppResult<Map<String, EntitySummary>> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return AppResult.Success(emptyMap())
        val summaries = linkedMapOf<String, EntitySummary>()
        for (chunk in distinctIds.chunked(MAX_BIND_PARAMETERS)) {
            val placeholders = chunk.joinToString(",") { "?" }
            when (val result = query("$SUMMARY_COLUMNS WHERE e.id IN ($placeholders)", chunk.toTypedArray()) { cursor ->
                AppResult.Success(readSummaries(cursor))
            }) {
                is AppResult.Success -> result.value.forEach { summaries[it.id] = it }
                is AppResult.Failure -> return result
            }
        }
        return AppResult.Success(summaries)
    }

    suspend fun detail(id: String): AppResult<EntityDetail?> = query("SELECT * FROM entities WHERE id = ? LIMIT 1", arrayOf(id)) { cursor ->
        if (!cursor.moveToFirst()) return@query AppResult.Success(null)
        val extra = runCatching { Json.Default.parseToJsonElement(cursor.string("extra_json")) as JsonObject }
            .getOrElse { return@query AppResult.Failure(AppError.JsonParseFailed(it.message ?: "extra_json 无效")) }
        AppResult.Success(
            EntityDetail(
                id = cursor.string("id"), entityType = cursor.string("entity_type"), gameId = cursor.optional("game_id"),
                internalName = cursor.optional("internal_name"), nameZh = cursor.string("name_zh"), nameEn = cursor.optional("name_en"),
                descriptionZh = cursor.optional("description_zh"), descriptionEn = cursor.optional("description_en"),
                category = cursor.optional("category"), translationStatus = cursor.optional("translation_status").toTranslationStatus(),
                imagePath = cursor.optional("image_path"), extraJson = extra, sourceFile = cursor.optional("source_file"),
                createdAt = cursor.string("created_at"),
            ),
        )
    }

    suspend fun aliases(id: String): AppResult<List<String>> = query("SELECT alias FROM entity_aliases WHERE entity_id = ? ORDER BY alias", arrayOf(id)) { cursor ->
        AppResult.Success(buildList { while (cursor.moveToNext()) add(cursor.getString(0)) })
    }

    suspend fun categories(type: String): AppResult<List<String>> = query(
        "SELECT DISTINCT category FROM entities WHERE entity_type = ? AND category IS NOT NULL AND category != '' ORDER BY category",
        arrayOf(type),
    ) { cursor -> AppResult.Success(buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }) }

    suspend fun searchPrefix(query: SearchQuery, limit: Int, entityTypes: Set<String> = emptySet()): AppResult<List<SearchDocument>> {
        val like = "${query.normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        return when (val statement = filteredSearchStatement(SEARCH_PREFIX, arrayOf(like, like, like, like, like), limit, entityTypes)) {
            is AppResult.Success -> query(statement.value.sql, statement.value.args) { cursor ->
                AppResult.Success(buildList { while (cursor.moveToNext()) add(cursor.toSearchDocument()) })
            }
            is AppResult.Failure -> statement
        }
    }

    suspend fun searchAliases(query: String, limit: Int, entityTypes: Set<String> = emptySet()): AppResult<List<EntitySummary>> =
        when (val statement = filteredSearchStatement(SEARCH_ALIAS, arrayOf(query), limit, entityTypes)) {
            is AppResult.Success -> query(statement.value.sql, statement.value.args) { cursor -> AppResult.Success(readSummaries(cursor)) }
            is AppResult.Failure -> statement
        }

    suspend fun searchFts(ftsQuery: String, limit: Int, entityTypes: Set<String> = emptySet()): AppResult<List<EntitySummary>> =
        when (val statement = filteredSearchStatement(SEARCH_FTS, arrayOf(ftsQuery), limit, entityTypes)) {
            is AppResult.Success -> query(statement.value.sql, statement.value.args) { cursor -> AppResult.Success(readSummaries(cursor)) }
            is AppResult.Failure -> statement
        }

    fun close() = database.close()

    private fun filteredSearchStatement(
        baseSql: String,
        initialArgs: Array<String>,
        limit: Int,
        entityTypes: Set<String>,
    ): AppResult<SearchStatement> {
        val types = entityTypes.toList().sorted()
        if (types.size > MAX_BIND_PARAMETERS - initialArgs.size - 1) {
            return AppResult.Failure(AppError.DatabaseQueryFailed("搜索筛选类型过多"))
        }
        val typeClause = types.takeIf { it.isNotEmpty() }
            ?.let { " AND e.entity_type IN (${it.joinToString(",") { "?" }})" }
            .orEmpty()
        return AppResult.Success(SearchStatement("$baseSql$typeClause LIMIT ?", initialArgs + types + limit.toString()))
    }

    private suspend fun count(table: String): AppResult<Int> = query("SELECT COUNT(*) FROM $table") { cursor ->
        if (!cursor.moveToFirst()) AppResult.Failure(AppError.DatabaseQueryFailed("无法读取 $table 数量"))
        else AppResult.Success(cursor.getInt(0))
    }

    private suspend fun <T> query(sql: String, args: Array<String>? = null, block: (Cursor) -> AppResult<T>): AppResult<T> = withContext(ioDispatcher) {
        runCatching { database.rawQuery(sql, args).use(block) }
            .getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "查询失败")) }
    }

    private fun readSummaries(cursor: Cursor) = buildList { while (cursor.moveToNext()) add(cursor.toSummary()) }

    private fun readDetails(cursor: Cursor): AppResult<List<EntityDetail>> {
        val details = buildList {
            while (cursor.moveToNext()) {
                val extra = runCatching { Json.Default.parseToJsonElement(cursor.string("extra_json")) as JsonObject }
                    .getOrElse { return AppResult.Failure(AppError.JsonParseFailed(it.message ?: "extra_json 无效")) }
                add(
                    EntityDetail(
                        id = cursor.string("id"), entityType = cursor.string("entity_type"), gameId = cursor.optional("game_id"),
                        internalName = cursor.optional("internal_name"), nameZh = cursor.string("name_zh"), nameEn = cursor.optional("name_en"),
                        descriptionZh = cursor.optional("description_zh"), descriptionEn = cursor.optional("description_en"),
                        category = cursor.optional("category"), translationStatus = cursor.optional("translation_status").toTranslationStatus(),
                        imagePath = cursor.optional("image_path"), extraJson = extra, sourceFile = cursor.optional("source_file"),
                        createdAt = cursor.string("created_at"),
                    ),
                )
            }
        }
        return AppResult.Success(details)
    }

    private fun parseArtifactMetadata(raw: String): ArtifactMetadata? = runCatching {
        Json.Default.decodeFromString<ArtifactMetadata>(raw)
    }.getOrNull()
    private fun Cursor.toSearchDocument() = SearchDocument(toSummary(), optional("pinyin"), optional("pinyin_initials"))
    private fun Cursor.toSummary() = EntitySummary(
        id = string("id"),
        entityType = string("entity_type"),
        nameZh = string("name_zh"),
        nameEn = optional("name_en"),
        category = optional("category"),
        imagePath = optional("image_path"),
        sortKey = optional("sort_key"),
        extraJson = string("extra_json"),
    )
    private fun Cursor.string(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.optional(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
    private fun String?.toTranslationStatus() = when (this) { "complete" -> TranslationStatus.COMPLETE; "missing" -> TranslationStatus.MISSING; "not_applicable" -> TranslationStatus.NOT_APPLICABLE; else -> TranslationStatus.UNKNOWN }

    private companion object {
        const val SUMMARY_SELECT = "SELECT e.id, e.entity_type, e.name_zh, e.name_en, e.category, e.image_path, e.extra_json, s.pinyin AS sort_key"
        const val SUMMARY_FROM = " FROM entities e LEFT JOIN entity_search s ON s.entity_id = e.id"
        const val SUMMARY_COLUMNS = "$SUMMARY_SELECT$SUMMARY_FROM"
        const val SUMMARY_BY_ID = "$SUMMARY_COLUMNS WHERE e.id = ? LIMIT 1"
        const val SUMMARIES_BY_TYPE = "$SUMMARY_COLUMNS WHERE e.entity_type = ? ORDER BY CASE WHEN s.pinyin IS NULL OR s.pinyin = '' THEN 1 ELSE 0 END, s.pinyin COLLATE NOCASE, e.name_zh COLLATE NOCASE"
        const val SEARCH_PREFIX = "$SUMMARY_SELECT, s.pinyin, s.pinyin_initials$SUMMARY_FROM WHERE (e.name_zh LIKE ? ESCAPE '\\' OR e.name_en LIKE ? ESCAPE '\\' COLLATE NOCASE OR s.pinyin LIKE ? ESCAPE '\\' OR REPLACE(s.pinyin, ' ', '') LIKE ? ESCAPE '\\' OR s.pinyin_initials LIKE ? ESCAPE '\\')"
        const val SEARCH_ALIAS = "$SUMMARY_COLUMNS JOIN entity_aliases a ON a.entity_id = e.id WHERE a.alias = ?"
        const val SEARCH_FTS = "SELECT e.id, e.entity_type, e.name_zh, e.name_en, e.category, e.image_path, e.extra_json, s.pinyin AS sort_key FROM entity_search s JOIN entities e ON e.id = s.entity_id WHERE entity_search MATCH ?"
        const val MAX_BIND_PARAMETERS = 900

        private data class SearchStatement(val sql: String, val args: Array<String>)
    }
}
