package com.example.stardewoffline.core.database.content

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.EntityTypeCount
import com.example.stardewoffline.core.model.Schema5BrowsePage
import com.example.stardewoffline.core.model.Schema5Condition
import com.example.stardewoffline.core.model.Schema5EntityCard
import com.example.stardewoffline.core.model.Schema5EntityDetail
import com.example.stardewoffline.core.model.Schema5EntitySummary
import com.example.stardewoffline.core.model.Schema5Facet
import com.example.stardewoffline.core.model.Schema5Fact
import com.example.stardewoffline.core.model.Schema5FactStatus
import com.example.stardewoffline.core.model.Schema5ValueType
import com.example.stardewoffline.core.model.Schema5FactItem
import com.example.stardewoffline.core.model.Schema5Relation
import com.example.stardewoffline.core.model.Schema5RelationGroup
import com.example.stardewoffline.core.model.Schema5SearchPage
import com.example.stardewoffline.core.model.Schema5SearchResult
import com.example.stardewoffline.core.model.Schema5SourceSummary
import com.example.stardewoffline.core.model.Schema5TypedValue
import com.example.stardewoffline.core.model.Schema5Visual
import com.example.stardewoffline.core.model.schema5ConditionCompleteness
import com.example.stardewoffline.core.model.schema5FactStatus
import com.example.stardewoffline.core.model.schema5ValueType
import com.example.stardewoffline.core.model.schema5VisualStatus
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Read-only adapter for the typed schema-5 player-facts-v1 database. */
class Schema5ContentDatabase internal constructor(
    val packageRoot: File,
    private val database: SQLiteDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun typeCounts(): AppResult<List<EntityTypeCount>> = query(
        "SELECT entity_type, COUNT(*) FROM entities GROUP BY entity_type ORDER BY entity_type",
    ) { cursor ->
        AppResult.Success(buildList {
            while (cursor.moveToNext()) add(EntityTypeCount(cursor.getString(0), cursor.getInt(1)))
        })
    }

    private fun readSummaries(cursor: Cursor): List<Schema5EntitySummary> = buildList {
        while (cursor.moveToNext()) add(cursor.toSummary())
    }

    suspend fun summary(id: String): AppResult<Schema5EntitySummary?> = when (val canonical = canonicalId(id)) {
        is AppResult.Failure -> canonical
        is AppResult.Success -> query(
            summarySql("WHERE e.id = ? LIMIT 1"), arrayOf(canonical.value),
        ) { cursor -> AppResult.Success(cursor.takeIf { it.moveToFirst() }?.toSummary()) }
    }

    suspend fun summariesByType(type: String): AppResult<List<Schema5EntitySummary>> = query(
        summarySql("WHERE e.entity_type = ? ORDER BY c.sort_key COLLATE NOCASE, e.id COLLATE NOCASE"),
        arrayOf(type),
    ) { cursor -> AppResult.Success(readSummaries(cursor)) }

    suspend fun summariesByTypes(
        types: Set<String>,
        facetValues: Set<String> = emptySet(),
    ): AppResult<Map<String, List<Schema5EntitySummary>>> {
        val sortedTypes = types.toList().sorted()
        val sortedFacetValues = facetValues.toList().sorted()
        if (sortedTypes.isEmpty()) return AppResult.Success(emptyMap())
        if (sortedTypes.size + sortedFacetValues.size > MAX_BIND_PARAMETERS) {
            return AppResult.Failure(AppError.DatabaseQueryFailed("分类筛选条件过多"))
        }
        val clauses = mutableListOf(
            "e.entity_type IN (${sortedTypes.joinToString(",") { "?" }})",
        )
        if (sortedFacetValues.isNotEmpty()) {
            clauses += "EXISTS (SELECT 1 FROM browse_facets f " +
                "JOIN browse_facet_groups fg ON fg.id = f.group_id " +
                "WHERE fg.entity_id = e.id AND f.text_value IN " +
                "(${sortedFacetValues.joinToString(",") { "?" }}))"
        }
        val args = (sortedTypes + sortedFacetValues).toTypedArray()
        return query(
            summarySql("WHERE ${clauses.joinToString(" AND ")} ORDER BY e.entity_type, c.sort_key COLLATE NOCASE, e.id COLLATE NOCASE"),
            args,
        ) { cursor -> AppResult.Success(readSummaries(cursor).groupBy(Schema5EntitySummary::entityType)) }
    }

    suspend fun browseByTypes(
        types: Set<String>,
        facetFilters: Map<String, Set<String>> = emptyMap(),
        ftsQuery: String? = null,
        cursor: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): AppResult<Schema5BrowsePage> = withContext(ioDispatcher) {
        runCatching {
            val sortedTypes = types.toList().sorted()
            if (sortedTypes.isEmpty()) {
                return@runCatching AppResult.Success(Schema5BrowsePage(emptyMap(), null))
            }
            if (pageSize !in 1..MAX_PAGE_SIZE) {
                return@runCatching AppResult.Failure(
                    AppError.DatabaseQueryFailed("分页大小无效")
                )
            }
            val filters = facetFilters
                .mapValues { (_, values) -> values.filter(String::isNotBlank).toSet() }
                .filterValues(Set<String>::isNotEmpty)
            val valueCount = filters.values.sumOf(Set<String>::size)
            val familyCount = filters.keys.count { it != ANY_FACET_FAMILY }
            val queryBindCount = sortedTypes.size + valueCount + familyCount +
                (if (!ftsQuery.isNullOrBlank()) 1 else 0) +
                (if (cursor != null) 3 else 0) + 1
            if (queryBindCount > MAX_BIND_PARAMETERS) {
                return@runCatching AppResult.Failure(
                    AppError.DatabaseQueryFailed("分类筛选条件过多")
                )
            }
            val browseContext = browseCursorContext(sortedTypes, filters, ftsQuery)
            val clauses = mutableListOf(
                "e.entity_type IN (${sortedTypes.joinToString(",") { "?" }})",
            )
            val args = mutableListOf<String>().apply { addAll(sortedTypes) }
            if (!ftsQuery.isNullOrBlank()) {
                clauses += "EXISTS (SELECT 1 FROM entity_search search " +
                    "WHERE search.entity_id = e.id AND search.search_text MATCH ?)"
                args += ftsQuery
            }
            appendFacetFilterClauses(clauses, args, filters)
            val decodedCursor = cursor?.let { decodeBrowseCursor(it) }
            if (decodedCursor != null) {
                val fingerprint = cursorFingerprint()
                if (decodedCursor.fingerprint != fingerprint || decodedCursor.context != browseContext) {
                    return@runCatching AppResult.Failure(
                        AppError.DatabaseQueryFailed("查询游标已失效")
                    )
                }
                clauses += "(c.sort_key COLLATE NOCASE > ? OR " +
                    "(c.sort_key COLLATE NOCASE = ? AND e.id COLLATE NOCASE > ?))"
                args += decodedCursor.sortKey
                args += decodedCursor.sortKey
                args += decodedCursor.id
            }
            args += (pageSize + 1).toString()
            val rows = database.rawQuery(
                summarySql(
                    "WHERE ${clauses.joinToString(" AND ")} " +
                        "ORDER BY c.sort_key COLLATE NOCASE, e.id COLLATE NOCASE LIMIT ?",
                ),
                args.toTypedArray(),
            ).use(::readSummaries)
            val hasMore = rows.size > pageSize
            val pageRows = rows.take(pageSize)
            val nextCursor = if (hasMore) {
                val last = pageRows.last()
                encodeBrowseCursor(
                    cursorFingerprint(), browseContext, last.card.sortKey, last.id
                )
            } else {
                null
            }
            AppResult.Success(
                Schema5BrowsePage(
                    pageRows.groupBy(Schema5EntitySummary::entityType),
                    nextCursor,
                )
            )
        }.getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "查询失败")) }
    }

    suspend fun summariesByIds(ids: List<String>): AppResult<Map<String, Schema5EntitySummary>> {
        val requested = when (val canonical = canonicalIds(ids)) {
            is AppResult.Failure -> return canonical
            is AppResult.Success -> canonical.value
        }
        if (requested.isEmpty()) return AppResult.Success(emptyMap())
        val result = linkedMapOf<String, Schema5EntitySummary>()
        for (chunk in requested.chunked(MAX_BIND_PARAMETERS)) {
            val placeholders = chunk.joinToString(",") { "?" }
            runCatching {
                readBatchSummaries(chunk, placeholders).values.forEach { result[it.id] = it }
            }.onFailure {
                return AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "批量摘要查询失败"))
            }
        }
        return AppResult.Success(result)
    }

    suspend fun detail(id: String): AppResult<Schema5EntityDetail?> = when (val canonical = canonicalId(id)) {
        is AppResult.Failure -> canonical
        is AppResult.Success -> withContext(ioDispatcher) {
            runCatching {
                val exists = database.rawQuery(
                    "SELECT 1 FROM entities WHERE id = ? LIMIT 1",
                    arrayOf(canonical.value),
                ).use { cursor -> cursor.moveToFirst() }
                if (!exists) AppResult.Success(null) else readDetail(canonical.value)
            }.getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "查询失败")) }
        }
    }

    suspend fun detailsByIds(ids: List<String>): AppResult<List<Schema5EntityDetail>> =
        readDetails(ids)

    suspend fun detailsByType(type: String): AppResult<List<Schema5EntityDetail>> = withContext(ioDispatcher) {
        runCatching {
            val ids = database.rawQuery(
                "SELECT id FROM entities WHERE entity_type = ? ORDER BY id COLLATE NOCASE",
                arrayOf(type),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            readDetails(ids)
        }.getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "查询失败")) }
    }

    suspend fun aliases(id: String): AppResult<List<String>> = when (val canonical = canonicalId(id)) {
        is AppResult.Failure -> canonical
        is AppResult.Success -> query(
            "SELECT alias FROM entity_aliases WHERE entity_id = ? ORDER BY alias COLLATE NOCASE",
            arrayOf(canonical.value),
        ) { cursor -> AppResult.Success(buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }) }
    }

    suspend fun searchPage(
        ftsQuery: String,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        entityTypes: Set<String> = emptySet(),
        cursor: String? = null,
    ): AppResult<Schema5SearchPage> = withContext(ioDispatcher) {
        runCatching {
            if (ftsQuery.isBlank()) return@runCatching AppResult.Success(Schema5SearchPage(emptyList(), null))
            if (pageSize !in 1..MAX_PAGE_SIZE) {
                return@runCatching AppResult.Failure(AppError.DatabaseQueryFailed("分页大小无效"))
            }
            val sortedTypes = entityTypes.toList().sorted()
            if (sortedTypes.size + 15 > MAX_BIND_PARAMETERS) {
                return@runCatching AppResult.Failure(AppError.DatabaseQueryFailed("搜索条件过多"))
            }
            val searchContext = searchCursorContext(sortedTypes, ftsQuery)
            val clauses = mutableListOf("s.search_text MATCH ?")
            val reasonQuery = ftsQuery.replace("*", "").replace('"', ' ').trim()
            val args = MutableList(10) { reasonQuery }.apply { add(ftsQuery) }
            if (sortedTypes.isNotEmpty()) {
                clauses += "e.entity_type IN (${sortedTypes.joinToString(",") { "?" }})"
                args += sortedTypes
            }
            val decodedCursor = cursor?.let { decodeBrowseCursor(it) }
            if (decodedCursor != null) {
                if (
                    decodedCursor.fingerprint != cursorFingerprint() ||
                    decodedCursor.context != searchContext
                ) {
                    return@runCatching AppResult.Failure(AppError.DatabaseQueryFailed("搜索游标已失效"))
                }
                clauses += "(c.sort_key COLLATE NOCASE > ? OR " +
                    "(c.sort_key COLLATE NOCASE = ? AND e.id COLLATE NOCASE > ?))"
                args += decodedCursor.sortKey
                args += decodedCursor.sortKey
                args += decodedCursor.id
            }
            args += (pageSize + 1).toString()
            val rows = database.rawQuery(
                "SELECT CASE " +
                    "WHEN instr(lower(COALESCE(s.name_zh, '')), lower(?)) > 0 THEN 1000 " +
                    "WHEN instr(lower(COALESCE(s.name_en, '')), lower(?)) > 0 THEN 950 " +
                    "WHEN instr(lower(COALESCE(s.aliases, '')), lower(?)) > 0 THEN 900 " +
                    "WHEN instr(lower(COALESCE(s.keywords, '')), lower(?)) > 0 THEN 850 " +
                    "WHEN instr(lower(COALESCE(s.action_summaries, '')), lower(?)) > 0 THEN 800 " +
                    "ELSE 500 END AS search_score, " +
                    "CASE " +
                    "WHEN instr(lower(COALESCE(s.name_zh, '')), lower(?)) > 0 THEN '名称' " +
                    "WHEN instr(lower(COALESCE(s.name_en, '')), lower(?)) > 0 THEN '英文名' " +
                    "WHEN instr(lower(COALESCE(s.aliases, '')), lower(?)) > 0 THEN '别名' " +
                    "WHEN instr(lower(COALESCE(s.keywords, '')), lower(?)) > 0 THEN '分类/用途' " +
                    "WHEN instr(lower(COALESCE(s.action_summaries, '')), lower(?)) > 0 THEN '行动信息' " +
                    "ELSE '全文' END AS search_reason, " +
                    "e.id, e.entity_type, e.game_id, e.internal_name, e.name_zh, e.name_en, " +
                    "e.description_zh, e.description_en, e.category, e.translation_status, " +
                    "c.identity_summary, c.action_summary_1, c.action_summary_2, c.category_label, c.sort_key, " +
                    "v.id AS visual_id, v.entity_id AS visual_entity_id, v.role AS visual_role, " +
                    "v.status AS visual_status, v.relative_path AS visual_relative_path, v.sha256 AS visual_sha256, " +
                    "v.source_entity_id AS visual_source_entity_id, v.crop_rect AS visual_crop_rect, " +
                    "v.rule_version AS visual_rule_version, v.reuse_reason AS visual_reuse_reason " +
                    "FROM entity_search s JOIN entities e ON e.id = s.entity_id " +
                    "JOIN entity_cards c ON c.entity_id = e.id " +
                    "LEFT JOIN visuals v ON v.entity_id = e.id AND v.role = 'entity' " +
                    "WHERE ${clauses.joinToString(" AND ")} " +
                    "ORDER BY search_score DESC, c.sort_key COLLATE NOCASE, e.id COLLATE NOCASE LIMIT ?",
                args.toTypedArray(),
            ).use { cursorRows ->
                buildList {
                    while (cursorRows.moveToNext()) {
                        add(
                            Schema5SearchResult(
                                cursorRows.toSummary(),
                                cursorRows.getInt(cursorRows.getColumnIndexOrThrow("search_score")),
                                cursorRows.getString(cursorRows.getColumnIndexOrThrow("search_reason")),
                            )
                        )
                    }
                }
            }
            val hasMore = rows.size > pageSize
            val pageRows = rows.take(pageSize)
            val nextCursor = if (hasMore) {
                val last = pageRows.last().summary
                encodeBrowseCursor(
                    cursorFingerprint(), searchContext, last.card.sortKey, last.id
                )
            } else {
                null
            }
            AppResult.Success(Schema5SearchPage(pageRows, nextCursor))
        }.getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "搜索失败")) }
    }

    suspend fun reverseRelations(entityId: String): AppResult<List<Schema5Relation>> = when (val canonical = canonicalId(entityId)) {
        is AppResult.Failure -> canonical
        is AppResult.Success -> query(
            "SELECT r.id, r.relation_group_id, r.subject_entity_id, r.predicate, r.object_entity_id, " +
                "r.original_direction, r.label, r.condition_set_id, g.family " +
                "FROM relations r JOIN relation_groups g ON g.id = r.relation_group_id " +
                "WHERE r.object_entity_id = ? ORDER BY r.predicate, r.subject_entity_id, r.id",
            arrayOf(canonical.value),
        ) { cursor ->
            AppResult.Success(buildList {
                while (cursor.moveToNext()) {
                    add(
                        Schema5Relation(
                            id = cursor.getString(0),
                            relationGroupId = cursor.getString(1),
                            subjectEntityId = cursor.getString(2),
                            predicate = cursor.getString(3),
                            objectEntityId = cursor.getString(4),
                            originalDirection = cursor.getString(5),
                            label = cursor.optional("label"),
                            condition = cursor.optional("condition_set_id")?.let(::readCondition),
                            sources = readSources(cursor.getString(0)),
                            family = cursor.getString(8),
                        )
                    )
                }
            })
        }
    }

    suspend fun packageRoot(): File = packageRoot

    internal fun close() = database.close()

    private suspend fun canonicalId(id: String): AppResult<String> = query(
        "SELECT COALESCE((SELECT entity_id FROM id_aliases WHERE alias_id = ?), ?)",
        arrayOf(id, id),
    ) { cursor ->
        AppResult.Success(if (cursor.moveToFirst()) cursor.getString(0) else id)
    }

    private suspend fun canonicalIds(ids: List<String>): AppResult<List<String>> {
        val requested = ids.distinct()
        if (requested.isEmpty()) return AppResult.Success(emptyList())
        val aliases = linkedMapOf<String, String>()
        for (chunk in requested.chunked(MAX_BIND_PARAMETERS)) {
            val placeholders = chunk.joinToString(",") { "?" }
            when (val result = query(
                "SELECT alias_id, entity_id FROM id_aliases WHERE alias_id IN ($placeholders)",
                chunk.toTypedArray(),
            ) { cursor ->
                AppResult.Success(buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
                })
            }) {
                is AppResult.Failure -> return result
                is AppResult.Success -> aliases.putAll(result.value)
            }
        }
        return AppResult.Success(requested.map { aliases[it] ?: it })
    }

    private suspend fun readDetail(id: String): AppResult<Schema5EntityDetail> {
        val summary = when (val result = summary(id)) {
            is AppResult.Success -> result.value ?: return AppResult.Failure(AppError.DatabaseQueryFailed("当前数据包中未找到此条目"))
            is AppResult.Failure -> return result
        }
        val aliases = when (val result = aliases(id)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val facts = when (val result = readFacts(id)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val relationGroups = when (val result = readRelationGroups(id)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        val visuals = when (val result = readVisuals(id)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return result
        }
        return AppResult.Success(Schema5EntityDetail(summary, createdAt(id), aliases, facts, relationGroups, visuals))
    }

    private suspend fun readDetails(ids: List<String>): AppResult<List<Schema5EntityDetail>> = withContext(ioDispatcher) {
        val requested = when (val canonical = canonicalIds(ids)) {
            is AppResult.Failure -> return@withContext canonical
            is AppResult.Success -> canonical.value
        }
        if (requested.isEmpty()) return@withContext AppResult.Success(emptyList())
        val result = linkedMapOf<String, Schema5EntityDetail>()
        for (chunk in requested.chunked(MAX_BIND_PARAMETERS)) {
            when (val batch = readDetailsChunk(chunk)) {
                is AppResult.Success -> batch.value.forEach { result[it.id] = it }
                is AppResult.Failure -> return@withContext batch
            }
        }
        AppResult.Success(requested.mapNotNull(result::get))
    }

    private fun readDetailsChunk(ids: List<String>): AppResult<List<Schema5EntityDetail>> =
        runCatching {
            val placeholders = ids.joinToString(",") { "?" }
            val summaries = readBatchSummaries(ids, placeholders)
            val aliases = readAliasesBatch(ids, placeholders)
            val createdAt = readCreatedAtBatch(ids, placeholders)
            val facts = readFactsBatch(ids, placeholders)
            val relationGroups = readRelationGroupsBatch(ids, placeholders)
            val visuals = readVisualsBatch(ids, placeholders)
            AppResult.Success(
                ids.mapNotNull { id ->
                    val summary = summaries[id] ?: return@mapNotNull null
                    Schema5EntityDetail(
                        summary = summary,
                        createdAt = createdAt[id].orEmpty(),
                        aliases = aliases[id].orEmpty(),
                        facts = facts[id].orEmpty(),
                        relationGroups = relationGroups[id].orEmpty(),
                        visuals = visuals[id].orEmpty(),
                    )
                },
            )
        }.getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "批量详情查询失败")) }

    private data class RawFactSlot(
        val id: String,
        val entityId: String,
        val slotKey: String,
        val status: Schema5FactStatus,
        val value: Schema5TypedValue?,
        val conditionId: String?,
    )

    private data class RawFactItem(
        val id: String,
        val slotId: String,
        val ordinal: Int,
        val value: Schema5TypedValue,
        val scopeId: String?,
        val conditionId: String?,
    )

    private data class RawRelationGroup(
        val id: String,
        val entityId: String,
        val family: String,
        val status: Schema5FactStatus,
        val conditionId: String?,
    )

    private data class RawRelation(
        val id: String,
        val relationGroupId: String,
        val subjectEntityId: String,
        val predicate: String,
        val objectEntityId: String,
        val originalDirection: String,
        val label: String?,
        val conditionId: String?,
    )

    private data class RawFacet(
        val id: String,
        val groupId: String,
        val scopeFamily: String,
        val scopeId: String,
        val valueType: Schema5ValueType,
        val value: Schema5TypedValue,
        val claimStatus: Schema5FactStatus,
        val conditionId: String?,
        val entityId: String,
    )

    private fun readFacetsBatch(
        ids: List<String>,
        placeholders: String,
    ): Map<String, List<Schema5Facet>> {
        val rows = database.rawQuery(
            "SELECT f.id, f.group_id, f.scope_family, f.scope_id, f.value_type, f.text_value, f.integer_value, " +
                "f.real_value, f.boolean_value, f.range_min, f.range_max, f.unit, f.claim_status, f.condition_set_id, g.entity_id " +
                "FROM browse_facets f JOIN browse_facet_groups g ON g.id = f.group_id " +
                "WHERE g.entity_id IN ($placeholders) ORDER BY g.entity_id, f.scope_family, f.scope_id, f.id",
            ids.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val valueType = schema5ValueType(cursor.getString(4)) ?: continue
                    val value = cursor.facetValue(valueType) ?: continue
                    add(
                        RawFacet(
                            id = cursor.getString(0), groupId = cursor.getString(1),
                            scopeFamily = cursor.getString(2), scopeId = cursor.getString(3),
                            valueType = valueType, value = value,
                            claimStatus = schema5FactStatus(cursor.getString(12)),
                            conditionId = cursor.optional("condition_set_id"), entityId = cursor.getString(14),
                        )
                    )
                }
            }
        }
        val conditions = readConditionsBatch(rows.mapNotNull(RawFacet::conditionId).distinct())
        val sources = readSourcesBatch(rows.map(RawFacet::id))
        return rows.groupBy(RawFacet::entityId).mapValues { (_, facets) ->
            facets.map { facet ->
                Schema5Facet(
                    id = facet.id,
                    groupId = facet.groupId,
                    scopeFamily = facet.scopeFamily,
                    scopeId = facet.scopeId,
                    valueType = facet.valueType,
                    value = facet.value,
                    claimStatus = facet.claimStatus,
                    condition = facet.conditionId?.let(conditions::get),
                    sources = sources[facet.id].orEmpty(),
                )
            }
        }
    }

    private fun readBatchSummaries(
        ids: List<String>,
        placeholders: String,
    ): Map<String, Schema5EntitySummary> {
        val facets = readFacetsBatch(ids, placeholders)
        return database.rawQuery(
            summarySql("WHERE e.id IN ($placeholders) ORDER BY c.sort_key COLLATE NOCASE, e.id COLLATE NOCASE"),
            ids.toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    put(id, cursor.toSummary(facets[id].orEmpty()))
                }
            }
        }
    }

    private fun readAliasesBatch(ids: List<String>, placeholders: String): Map<String, List<String>> =
        database.rawQuery(
            "SELECT entity_id, alias FROM entity_aliases WHERE entity_id IN ($placeholders) ORDER BY entity_id, alias COLLATE NOCASE",
            ids.toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val entityId = cursor.getString(0)
                    put(entityId, (get(entityId).orEmpty() + cursor.getString(1)))
                }
            }
        }

    private fun readCreatedAtBatch(ids: List<String>, placeholders: String): Map<String, String> =
        database.rawQuery(
            "SELECT id, created_at FROM entities WHERE id IN ($placeholders)",
            ids.toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
            }
        }

    private fun readFactsBatch(ids: List<String>, placeholders: String): Map<String, List<Schema5Fact>> {
        val slots = database.rawQuery(
            "SELECT id, entity_id, slot_key, status, value_type, text_value, integer_value, real_value, boolean_value, unit, condition_set_id " +
                "FROM fact_slots WHERE entity_id IN ($placeholders) ORDER BY entity_id, slot_key, id",
            ids.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    RawFactSlot(
                        id = cursor.getString(0), entityId = cursor.getString(1), slotKey = cursor.getString(2),
                        status = schema5FactStatus(cursor.getString(3)),
                        value = cursor.typedValue(4, 5, 6, 7, 8, 9),
                        conditionId = cursor.optional("condition_set_id"),
                    )
                )
            }
        }
        val slotIds = slots.map(RawFactSlot::id)
        val items = slotIds.chunked(MAX_BIND_PARAMETERS).flatMap { slotChunk ->
            if (slotChunk.isEmpty()) return@flatMap emptyList()
            val itemPlaceholders = slotChunk.joinToString(",") { "?" }
            database.rawQuery(
                "SELECT id, slot_id, ordinal, value_type, text_value, integer_value, real_value, boolean_value, unit, scope_id, condition_set_id " +
                    "FROM fact_items WHERE slot_id IN ($itemPlaceholders) ORDER BY slot_id, ordinal, id",
                slotChunk.toTypedArray(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(
                        RawFactItem(
                            id = cursor.getString(0), slotId = cursor.getString(1), ordinal = cursor.getInt(2),
                            value = requireNotNull(cursor.typedValue(3, 4, 5, 6, 7, 8)),
                            scopeId = cursor.optional("scope_id"), conditionId = cursor.optional("condition_set_id"),
                        )
                    )
                }
            }
        }
        val conditionIds = (slots.mapNotNull(RawFactSlot::conditionId) + items.mapNotNull(RawFactItem::conditionId)).distinct()
        val conditions = readConditionsBatch(conditionIds)
        val sourceClaims = slots.map(RawFactSlot::id) + items.map(RawFactItem::id)
        val sources = readSourcesBatch(sourceClaims)
        val itemsBySlot = items.groupBy(RawFactItem::slotId)
        return slots.groupBy(RawFactSlot::entityId).mapValues { (_, entitySlots) ->
            entitySlots.map { slot ->
                Schema5Fact(
                    id = slot.id,
                    entityId = slot.entityId,
                    slotKey = slot.slotKey,
                    status = slot.status,
                    value = slot.value,
                    condition = slot.conditionId?.let(conditions::get),
                    sources = sources[slot.id].orEmpty(),
                    items = itemsBySlot[slot.id].orEmpty().map { item ->
                        Schema5FactItem(
                            id = item.id,
                            slotId = item.slotId,
                            ordinal = item.ordinal,
                            value = item.value,
                            scopeId = item.scopeId,
                            condition = item.conditionId?.let(conditions::get),
                            sources = sources[item.id].orEmpty(),
                        )
                    },
                )
            }
        }
    }

    private fun readRelationGroupsBatch(
        ids: List<String>,
        placeholders: String,
    ): Map<String, List<Schema5RelationGroup>> {
        val groups = database.rawQuery(
            "SELECT id, entity_id, family, status, condition_set_id FROM relation_groups WHERE entity_id IN ($placeholders) ORDER BY entity_id, family, id",
            ids.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    RawRelationGroup(
                        id = cursor.getString(0), entityId = cursor.getString(1), family = cursor.getString(2),
                        status = schema5FactStatus(cursor.getString(3)), conditionId = cursor.optional("condition_set_id"),
                    )
                )
            }
        }
        val groupIds = groups.map(RawRelationGroup::id)
        val relations = groupIds.chunked(MAX_BIND_PARAMETERS).flatMap { groupChunk ->
            if (groupChunk.isEmpty()) return@flatMap emptyList()
            val groupPlaceholders = groupChunk.joinToString(",") { "?" }
            database.rawQuery(
                "SELECT id, relation_group_id, subject_entity_id, predicate, object_entity_id, original_direction, label, condition_set_id " +
                    "FROM relations WHERE relation_group_id IN ($groupPlaceholders) ORDER BY relation_group_id, id",
                groupChunk.toTypedArray(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(
                        RawRelation(
                            id = cursor.getString(0), relationGroupId = cursor.getString(1), subjectEntityId = cursor.getString(2),
                            predicate = cursor.getString(3), objectEntityId = cursor.getString(4), originalDirection = cursor.getString(5),
                            label = cursor.optional("label"), conditionId = cursor.optional("condition_set_id"),
                        )
                    )
                }
            }
        }
        val conditionIds = (groups.mapNotNull(RawRelationGroup::conditionId) + relations.mapNotNull(RawRelation::conditionId)).distinct()
        val conditions = readConditionsBatch(conditionIds)
        val sources = readSourcesBatch(groups.map(RawRelationGroup::id) + relations.map(RawRelation::id))
        val relationsByGroup = relations.groupBy(RawRelation::relationGroupId)
        return groups.groupBy(RawRelationGroup::entityId).mapValues { (_, entityGroups) ->
            entityGroups.map { group ->
                Schema5RelationGroup(
                    id = group.id,
                    entityId = group.entityId,
                    family = group.family,
                    status = group.status,
                    condition = group.conditionId?.let(conditions::get),
                    relations = relationsByGroup[group.id].orEmpty().map { relation ->
                        Schema5Relation(
                            id = relation.id,
                            relationGroupId = relation.relationGroupId,
                            subjectEntityId = relation.subjectEntityId,
                            predicate = relation.predicate,
                            objectEntityId = relation.objectEntityId,
                            originalDirection = relation.originalDirection,
                            label = relation.label,
                            condition = relation.conditionId?.let(conditions::get),
                            sources = sources[relation.id].orEmpty(),
                        )
                    },
                )
            }
        }
    }

    private fun readVisualsBatch(ids: List<String>, placeholders: String): Map<String, List<Schema5Visual>> =
        database.rawQuery(
            "SELECT id, entity_id, role, status, relative_path, sha256, source_entity_id, crop_rect, rule_version, reuse_reason " +
                "FROM visuals WHERE entity_id IN ($placeholders) ORDER BY entity_id, role, id",
            ids.toTypedArray(),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toVisual()?.let(::add) }
                .groupBy(Schema5Visual::entityId)
        }

    private fun readConditionsBatch(ids: List<String>): Map<String, Schema5Condition> {
        val result = linkedMapOf<String, Schema5Condition>()
        for (chunk in ids.distinct().chunked(MAX_BIND_PARAMETERS)) {
            if (chunk.isEmpty()) continue
            val placeholders = chunk.joinToString(",") { "?" }
            database.rawQuery(
                "SELECT id, completeness, player_summary, original_text FROM condition_sets WHERE id IN ($placeholders)",
                chunk.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) result[cursor.getString(0)] = Schema5Condition(
                    id = cursor.getString(0),
                    completeness = schema5ConditionCompleteness(cursor.getString(1)),
                    playerSummary = cursor.optional("player_summary"),
                    originalText = cursor.optional("original_text"),
                )
            }
        }
        return result
    }

    private fun readSourcesBatch(claimIds: List<String>): Map<String, List<Schema5SourceSummary>> {
        val result = linkedMapOf<String, MutableList<Schema5SourceSummary>>()
        for (chunk in claimIds.distinct().chunked(MAX_BIND_PARAMETERS)) {
            if (chunk.isEmpty()) continue
            val placeholders = chunk.joinToString(",") { "?" }
            database.rawQuery(
                "SELECT ce.claim_id, d.source_kind, d.title, d.game_version, d.revision, d.source_url, d.reviewed_at, " +
                    "e.evidence_kind, e.transformation_rule, d.review_status, d.conflict_status, d.expires_at " +
                    "FROM claim_evidence ce JOIN evidence e ON e.id = ce.evidence_id " +
                    "JOIN source_locators l ON l.id = e.source_locator_id JOIN source_documents d ON d.id = l.source_document_id " +
                    "WHERE ce.claim_id IN ($placeholders) ORDER BY ce.claim_id, d.source_kind, d.id",
                chunk.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val source = Schema5SourceSummary(
                        kind = cursor.getString(1), title = cursor.getString(2),
                        gameVersion = cursor.getStringOrNull(3), revision = cursor.getStringOrNull(4),
                        sourceUrl = cursor.getStringOrNull(5), reviewedAt = cursor.getStringOrNull(6),
                        evidenceKind = cursor.getStringOrNull(7), transformationRule = cursor.getStringOrNull(8),
                        reviewStatus = cursor.getStringOrNull(9), conflictStatus = cursor.getStringOrNull(10),
                        expiresAt = cursor.getStringOrNull(11),
                    )
                    result.getOrPut(cursor.getString(0)) { mutableListOf() }.add(source)
                }
            }
        }
        return result.mapValues { it.value.toList() }
    }

    private suspend fun readFacts(entityId: String): AppResult<List<Schema5Fact>> = query(
        "SELECT id, entity_id, slot_key, status, value_type, text_value, integer_value, real_value, " +
            "boolean_value, unit, condition_set_id FROM fact_slots WHERE entity_id = ? ORDER BY slot_key, id",
        arrayOf(entityId),
    ) { cursor ->
        val facts = mutableListOf<Schema5Fact>()
        while (cursor.moveToNext()) {
            val condition = cursor.optional("condition_set_id")?.let { readCondition(it) }
            val items = readFactItems(cursor.getString(0))
            facts += Schema5Fact(
                id = cursor.getString(0), entityId = cursor.getString(1), slotKey = cursor.getString(2),
                status = schema5FactStatus(cursor.getString(3)), value = cursor.typedValue(4, 5, 6, 7, 8, 9),
                condition = condition, sources = readSources(cursor.getString(0)), items = items,
            )
        }
        AppResult.Success(facts)
    }

    private fun readFactItems(slotId: String): List<Schema5FactItem> = database.rawQuery(
        "SELECT id, slot_id, ordinal, value_type, text_value, integer_value, real_value, boolean_value, unit, scope_id, condition_set_id " +
            "FROM fact_items WHERE slot_id = ? ORDER BY ordinal, id", arrayOf(slotId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                Schema5FactItem(
                    id = cursor.getString(0), slotId = cursor.getString(1), ordinal = cursor.getInt(2),
                    value = requireNotNull(cursor.typedValue(3, 4, 5, 6, 7, 8)) { "事实项缺少类型化值" },
                    scopeId = cursor.optional("scope_id"),
                    condition = cursor.optional("condition_set_id")?.let { readCondition(it) },
                    sources = readSources(cursor.getString(0)),
                ),
            )
        }
    }

    private suspend fun readRelationGroups(entityId: String): AppResult<List<Schema5RelationGroup>> = query(
        "SELECT id, entity_id, family, status, condition_set_id FROM relation_groups WHERE entity_id = ? ORDER BY family, id",
        arrayOf(entityId),
    ) { cursor ->
        val groups = mutableListOf<Schema5RelationGroup>()
        while (cursor.moveToNext()) {
            val groupId = cursor.getString(0)
            groups += Schema5RelationGroup(
                id = groupId, entityId = cursor.getString(1), family = cursor.getString(2),
                status = schema5FactStatus(cursor.getString(3)), condition = cursor.optional("condition_set_id")?.let { readCondition(it) },
                relations = readRelations(groupId),
            )
        }
        AppResult.Success(groups)
    }

    private fun readRelations(groupId: String): List<Schema5Relation> = database.rawQuery(
        "SELECT id, relation_group_id, subject_entity_id, predicate, object_entity_id, original_direction, label, condition_set_id " +
            "FROM relations WHERE relation_group_id = ? ORDER BY id", arrayOf(groupId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                Schema5Relation(
                    id = cursor.getString(0), relationGroupId = cursor.getString(1), subjectEntityId = cursor.getString(2),
                    predicate = cursor.getString(3), objectEntityId = cursor.getString(4), originalDirection = cursor.getString(5),
                    label = cursor.optional("label"), condition = cursor.optional("condition_set_id")?.let { readCondition(it) },
                    sources = readSources(cursor.getString(0)),
                ),
            )
        }
    }

    private suspend fun readVisuals(entityId: String): AppResult<List<Schema5Visual>> = query(
        "SELECT id, entity_id, role, status, relative_path, sha256, source_entity_id, crop_rect, rule_version, reuse_reason " +
            "FROM visuals WHERE entity_id = ? ORDER BY role, id", arrayOf(entityId),
    ) { cursor -> AppResult.Success(readVisualRows(cursor)) }

    private fun readVisualRows(cursor: Cursor): List<Schema5Visual> = buildList {
        while (cursor.moveToNext()) cursor.toVisual()?.let(::add)
    }

    private fun readSources(claimId: String): List<Schema5SourceSummary> = database.rawQuery(
        """
        SELECT d.source_kind, d.title, d.game_version, d.revision, d.source_url, d.reviewed_at,
               e.evidence_kind, e.transformation_rule, d.review_status, d.conflict_status, d.expires_at
        FROM claim_evidence ce
        JOIN evidence e ON e.id = ce.evidence_id
        JOIN source_locators l ON l.id = e.source_locator_id
        JOIN source_documents d ON d.id = l.source_document_id
        WHERE ce.claim_id = ?
        ORDER BY d.source_kind, d.id
        """.trimIndent(), arrayOf(claimId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                Schema5SourceSummary(
                    kind = cursor.getString(0), title = cursor.getString(1),
                    gameVersion = cursor.getStringOrNull(2), revision = cursor.getStringOrNull(3),
                    sourceUrl = cursor.getStringOrNull(4), reviewedAt = cursor.getStringOrNull(5),
                    evidenceKind = cursor.getStringOrNull(6), transformationRule = cursor.getStringOrNull(7),
                    reviewStatus = cursor.getStringOrNull(8), conflictStatus = cursor.getStringOrNull(9),
                    expiresAt = cursor.getStringOrNull(10),
                ),
            )
        }
    }

    private fun readCondition(id: String): Schema5Condition? = database.rawQuery(
        "SELECT id, completeness, player_summary, original_text FROM condition_sets WHERE id = ? LIMIT 1", arrayOf(id),
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else Schema5Condition(
            id = cursor.getString(0), completeness = schema5ConditionCompleteness(cursor.getString(1)),
            playerSummary = cursor.optional("player_summary"), originalText = cursor.optional("original_text"),
        )
    }

    private fun createdAt(id: String): String = database.rawQuery(
        "SELECT created_at FROM entities WHERE id = ? LIMIT 1", arrayOf(id),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" }

    private fun appendFacetFilterClauses(
        clauses: MutableList<String>,
        args: MutableList<String>,
        filters: Map<String, Set<String>>,
    ) {
        filters[ANY_FACET_FAMILY]?.toList()?.sorted()?.let { values ->
            clauses += "EXISTS (SELECT 1 FROM browse_facets f " +
                "JOIN browse_facet_groups fg ON fg.id = f.group_id " +
                "WHERE fg.entity_id = e.id AND f.text_value IN " +
                "(${values.joinToString(",") { "?" }}))"
            args += values
        }
        val scopedFilters = filters.filterKeys { it != ANY_FACET_FAMILY }
        if (scopedFilters.isEmpty()) return
        val scopeClauses = scopedFilters.toList().sortedBy { it.first }.map { (family, values) ->
            val sortedValues = values.toList().sorted()
            args += family
            args += sortedValues
            "EXISTS (SELECT 1 FROM browse_facets f " +
                "JOIN browse_facet_groups fg ON fg.id = f.group_id " +
                "WHERE fg.entity_id = e.id AND f.scope_id = anchor.scope_id " +
                "AND f.scope_family = ? AND f.text_value IN " +
                "(${sortedValues.joinToString(",") { "?" }}))"
        }
        clauses += "EXISTS (SELECT 1 FROM browse_facets anchor " +
            "JOIN browse_facet_groups anchor_group ON anchor_group.id = anchor.group_id " +
            "WHERE anchor_group.entity_id = e.id AND " +
            scopeClauses.joinToString(" AND ") + ")"
    }

    private val packageDatabaseIdentity: String by lazy {
        val manifest = packageRoot.resolve("manifest.json")
        require(manifest.isFile) { "缺少 manifest.json，无法绑定查询游标" }
        Json.decodeFromString<DataManifest>(manifest.readText()).database.sha256
    }

    private fun cursorFingerprint(): String = "${readSchemaFingerprint()}|$packageDatabaseIdentity"

    private fun readSchemaFingerprint(): String = database.rawQuery(
        "SELECT value FROM build_meta WHERE key = 'schema_fingerprint' LIMIT 1",
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else throw IllegalStateException("缺少 schema 指纹")
    }

    private fun browseCursorContext(
        types: List<String>,
        filters: Map<String, Set<String>>,
        ftsQuery: String?,
    ): String = listOf(
        "browse-v1",
        types.joinToString(","),
        canonicalFilters(filters),
        ftsQuery.orEmpty(),
    ).joinToString("|")

    private fun searchCursorContext(types: List<String>, ftsQuery: String): String =
        listOf("search-v1", types.joinToString(","), ftsQuery).joinToString("|")

    private fun canonicalFilters(filters: Map<String, Set<String>>): String =
        filters.toSortedMap().entries.joinToString(";") { (family, values) ->
            "$family=${values.toList().sorted().joinToString(",")}"
        }

    private fun encodeBrowseCursor(
        fingerprint: String,
        context: String,
        sortKey: String,
        id: String,
    ): String = listOf(fingerprint, context, sortKey, id).joinToString(".") {
        Base64.encodeToString(it.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun decodeBrowseCursor(value: String): BrowseCursor {
        val parts = value.split(".").map {
            String(Base64.decode(it, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        }
        require(parts.size == 4 && parts.all(String::isNotEmpty)) { "查询游标格式无效" }
        return BrowseCursor(parts[0], parts[1], parts[2], parts[3])
    }

    private data class BrowseCursor(
        val fingerprint: String,
        val context: String,
        val sortKey: String,
        val id: String,
    )

    private fun summarySql(suffix: String): String =
        "SELECT e.id, e.entity_type, e.game_id, e.internal_name, e.name_zh, e.name_en, " +
            "e.description_zh, e.description_en, e.category, e.translation_status, " +
            "c.identity_summary, c.action_summary_1, c.action_summary_2, c.category_label, c.sort_key, " +
            "v.id AS visual_id, v.entity_id AS visual_entity_id, v.role AS visual_role, " +
            "v.status AS visual_status, v.relative_path AS visual_relative_path, v.sha256 AS visual_sha256, " +
            "v.source_entity_id AS visual_source_entity_id, v.crop_rect AS visual_crop_rect, " +
            "v.rule_version AS visual_rule_version, v.reuse_reason AS visual_reuse_reason " +
            "FROM entities e JOIN entity_cards c ON c.entity_id = e.id " +
            "LEFT JOIN visuals v ON v.entity_id = e.id AND v.role = 'entity' $suffix"

    private fun Cursor.toSummary(facets: List<Schema5Facet>? = null): Schema5EntitySummary {
        val card = Schema5EntityCard(
            entityId = getString(0), identitySummary = optional("identity_summary"), actionSummary1 = optional("action_summary_1"),
            actionSummary2 = optional("action_summary_2"), categoryLabel = optional("category_label"), sortKey = string("sort_key"),
        )
        return Schema5EntitySummary(
            id = string("id"), entityType = string("entity_type"), gameId = optional("game_id"), internalName = optional("internal_name"),
            nameZh = string("name_zh"), nameEn = optional("name_en"), descriptionZh = optional("description_zh"),
            descriptionEn = optional("description_en"),
            category = optional("category"),
            translationStatus = optional("translation_status").toTranslationStatus(),
            card = card, visual = toVisual(), facets = facets ?: readFacets(getString(0)),
        )
    }

    private fun Cursor.toVisual(): Schema5Visual? = optional("visual_id")?.let {
        Schema5Visual(
            id = it,
            entityId = string("visual_entity_id"),
            role = string("visual_role"),
            status = schema5VisualStatus(string("visual_status")),
            relativePath = optional("visual_relative_path"),
            sha256 = optional("visual_sha256"),
            sourceEntityId = optional("visual_source_entity_id"),
            cropRect = optional("visual_crop_rect"),
            ruleVersion = optional("visual_rule_version"),
            reuseReason = optional("visual_reuse_reason"),
        )
    }

    private fun readFacets(entityId: String): List<Schema5Facet> = database.rawQuery(
        "SELECT f.id, f.group_id, f.scope_family, f.scope_id, f.value_type, f.text_value, f.integer_value, " +
            "f.real_value, f.boolean_value, f.range_min, f.range_max, f.unit, " +
            "f.claim_status, f.condition_set_id " +
            "FROM browse_facets f JOIN browse_facet_groups g ON g.id = f.group_id " +
            "WHERE g.entity_id = ? ORDER BY f.scope_family, f.scope_id, f.id",
        arrayOf(entityId),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val valueType = schema5ValueType(cursor.getString(4)) ?: continue
                val value = cursor.facetValue(valueType) ?: continue
                add(
                    Schema5Facet(
                        id = cursor.getString(0), groupId = cursor.getString(1), scopeFamily = cursor.getString(2),
                        scopeId = cursor.getString(3), valueType = valueType, value = value,
                        claimStatus = schema5FactStatus(cursor.getString(12)),
                        condition = cursor.getStringOrNull(13)?.let(::readCondition),
                        sources = readSources(cursor.getString(0)),
                    ),
                )
            }
        }
    }

    private fun Cursor.facetValue(type: Schema5ValueType): Schema5TypedValue? = when (type) {
        Schema5ValueType.TEXT -> getStringOrNull(5)?.let { Schema5TypedValue(type, text = it, unit = getStringOrNull(11)) }
        Schema5ValueType.INTEGER -> getLongOrNull(6)?.let { Schema5TypedValue(type, integer = it, unit = getStringOrNull(11)) }
        Schema5ValueType.REAL -> getDoubleOrNull(7)?.let { Schema5TypedValue(type, real = it, unit = getStringOrNull(11)) }
        Schema5ValueType.BOOLEAN -> getIntOrNull(8)?.let { Schema5TypedValue(type, boolean = it != 0, unit = getStringOrNull(11)) }
        Schema5ValueType.RANGE -> if (!isNull(9) || !isNull(10)) {
            Schema5TypedValue(type, unit = getStringOrNull(11), rangeMin = getDoubleOrNull(9), rangeMax = getDoubleOrNull(10))
        } else null
    }

    private fun Cursor.typedValue(
        typeIndex: Int,
        textIndex: Int,
        integerIndex: Int,
        realIndex: Int,
        booleanIndex: Int,
        unitIndex: Int,
    ): Schema5TypedValue? {
        val type = schema5ValueType(getString(typeIndex)) ?: return null
        return Schema5TypedValue(
            type = type, text = getStringOrNull(textIndex), integer = getLongOrNull(integerIndex), real = getDoubleOrNull(realIndex),
            boolean = getIntOrNull(booleanIndex)?.let { it != 0 }, unit = getStringOrNull(unitIndex),
            rangeMin = getColumnIndex("range_min").takeIf { it >= 0 }?.let { getDoubleOrNull(it) },
            rangeMax = getColumnIndex("range_max").takeIf { it >= 0 }?.let { getDoubleOrNull(it) },
        )
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.optional(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
    private fun Cursor.getStringOrNull(index: Int): String? = index.takeIf { it >= 0 && !isNull(it) }?.let(::getString)
    private fun Cursor.getLongOrNull(index: Int): Long? = index.takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
    private fun Cursor.getDoubleOrNull(index: Int): Double? = index.takeIf { it >= 0 && !isNull(it) }?.let(::getDouble)
    private fun Cursor.getIntOrNull(index: Int): Int? = index.takeIf { it >= 0 && !isNull(it) }?.let(::getInt)
    private fun String?.toTranslationStatus() = when (this) {
        "complete" -> com.example.stardewoffline.core.model.TranslationStatus.COMPLETE
        "missing" -> com.example.stardewoffline.core.model.TranslationStatus.MISSING
        "not_applicable" -> com.example.stardewoffline.core.model.TranslationStatus.NOT_APPLICABLE
        else -> com.example.stardewoffline.core.model.TranslationStatus.UNKNOWN
    }

    private suspend fun <T> query(
        sql: String,
        args: Array<String>? = null,
        block: (Cursor) -> AppResult<T>,
    ): AppResult<T> = withContext(ioDispatcher) {
        runCatching { database.rawQuery(sql, args).use(block) }
            .getOrElse { AppResult.Failure(AppError.DatabaseQueryFailed(it.message ?: "查询失败")) }
    }

    private companion object {
        const val MAX_BIND_PARAMETERS = 900
        const val DEFAULT_PAGE_SIZE = 60
        const val MAX_PAGE_SIZE = 100
        const val ANY_FACET_FAMILY = "_any"
    }
}
