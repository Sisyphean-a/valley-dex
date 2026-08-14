package com.example.stardewoffline.core.datapackage

import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.common.IoDispatcher
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.content.ContentDatabaseFactory
import com.example.stardewoffline.core.model.ArtifactMetadata
import com.example.stardewoffline.core.model.BuildMeta
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.DataPackageInfo
import com.example.stardewoffline.core.model.ManifestContent
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
private data class Schema5Conformance(
    val status: String,
    val manifestVersion: Int,
    val schemaVersion: Int,
    val contentContract: String,
    val publishable: Boolean,
    val databaseSha256: String,
    val schemaFingerprint: String? = null,
)

private val CORE_FACT_SLOTS = mapOf(
    "object" to setOf("sell_price"),
    "mineral" to setOf("sell_price"),
    "ring" to setOf("sell_price"),
    "crop" to setOf(
        "seasons", "first_harvest_days", "regrow_days", "needs_watering",
        "seed_item_id", "harvest_item_id", "sell_price", "seed_purchase_price",
    ),
    "fish" to setOf(
        "difficulty", "behavior", "min_size", "max_size", "fishing_time",
        "seasons", "weather", "sell_price", "fishing_locations",
    ),
    "villager" to setOf("residence_region", "birthday", "gender", "can_be_romanced"),
    "big_craftable" to setOf("purchase_price", "crafting_material_id", "crafting_material_quantity"),
    "tool" to setOf("purchase_price", "upgrade_material_id", "upgrade_price"),
    "weapon" to setOf("sell_price", "purchase_price", "acquisition"),
    "monster" to setOf("locations", "drops"),
)

@Singleton
class DataPackageValidator @Inject constructor(
    private val json: Json,
    private val hashUtils: HashUtils,
    private val databaseFactory: ContentDatabaseFactory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun validate(packageRoot: File): AppResult<DataPackageInfo> = withContext(ioDispatcher) {
        val manifestResult = readManifest(packageRoot)
        val manifest = manifestResult.getOrNull()
            ?: return@withContext AppResult.Failure(manifestResult.failureOrNull() ?: AppError.InvalidManifest("无法读取 manifest.json"))
        DataPackageContract.validateInstallManifest(manifest)?.let { return@withContext AppResult.Failure(it) }
        val databaseFile = resolveInside(packageRoot, manifest.database.file)
            ?: return@withContext AppResult.Failure(AppError.InvalidManifest("数据库路径越界"))
        if (!databaseFile.isFile) return@withContext AppResult.Failure(AppError.InvalidManifest("数据库文件不存在"))
        if (!hashUtils.sha256(databaseFile).equals(manifest.database.sha256, ignoreCase = true)) {
            return@withContext AppResult.Failure(AppError.HashMismatch)
        }
        if (manifest.manifestVersion == DataPackageContract.MANIFEST_VERSION) {
            validateV5ReleaseArtifacts(packageRoot, manifest)?.let {
                return@withContext AppResult.Failure(it)
            }
            return@withContext validateV5Database(packageRoot, databaseFile, manifest)
        }
        validateDatabase(packageRoot, databaseFile, manifest)
    }

    fun readManifest(packageRoot: File): AppResult<DataManifest> = runCatching {
        val file = File(packageRoot, "manifest.json")
        AppResult.Success(json.decodeFromString<DataManifest>(file.readText()))
    }.getOrElse { AppResult.Failure(AppError.InvalidManifest(it.message ?: "无法解析 JSON")) }

    private suspend fun validateV5Database(
        root: File,
        databaseFile: File,
        manifest: DataManifest,
    ): AppResult<DataPackageInfo> {
        val validationCopy = File.createTempFile("stardew-v5-validation-", ".db")
        databaseFile.copyTo(validationCopy, overwrite = true)
        val database = runCatching {
            SQLiteDatabase.openDatabase(
                validationCopy.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
        }.getOrElse {
            validationCopy.delete()
            return AppResult.Failure(AppError.DatabaseOpenFailed(it.message ?: "schema 5 打开失败"))
        }
        return try {
            database.execSQL("PRAGMA foreign_keys = ON")
            val quickCheck = database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok"
            }
            if (!quickCheck) return AppResult.Failure(AppError.DatabaseCorrupted("quick_check 未通过"))
            val foreignKeysClean = database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                !cursor.moveToFirst()
            }
            if (!foreignKeysClean) return AppResult.Failure(AppError.DatabaseCorrupted("foreign_key_check 未通过"))
            if (database.getVersion() != DataPackageContract.V5_SCHEMA_VERSION) {
                return AppResult.Failure(AppError.MetadataMismatch("PRAGMA user_version"))
            }

            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('table', 'view')",
                null,
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            val requiredTables = setOf(
                "build_meta", "package_capabilities", "entities", "entity_aliases", "id_aliases",
                "fact_slots", "fact_items", "relation_groups", "relations", "condition_sets",
                "condition_terms", "source_documents", "source_locators", "evidence", "claim_evidence",
                "visuals", "entity_cards", "browse_facet_groups", "browse_facets", "entity_search",
            )
            if (!tables.containsAll(requiredTables)) {
                return AppResult.Failure(AppError.DatabaseCorrupted("schema 5 缺少必需表"))
            }
            val entitySearchColumns = database.rawQuery("PRAGMA table_info(entity_search)", null).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            if (!entitySearchColumns.containsAll(
                    setOf(
                        "entity_id", "name_zh", "name_en", "aliases", "keywords",
                        "action_summaries", "search_text",
                    )
                )
            ) {
                return AppResult.Failure(AppError.DatabaseCorrupted("schema 5 搜索索引列不完整"))
            }
            val factItemColumns = database.rawQuery("PRAGMA table_info(fact_items)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            if (!factItemColumns.containsAll(
                    setOf(
                        "id", "slot_id", "ordinal", "value_type", "text_value",
                        "integer_value", "real_value", "boolean_value", "unit", "scope_id",
                        "condition_set_id",
                    )
                )
            ) {
                return AppResult.Failure(AppError.DatabaseCorrupted("schema 5 事实项列不完整"))
            }
            val indexes = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'index'",
                null,
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            val requiredIndexes = setOf(
                "index_entities_type", "index_entities_name_zh", "index_entities_game_id",
                "index_entity_aliases_entity", "index_entity_aliases_alias", "index_id_aliases_entity",
                "index_condition_terms_set", "index_source_locators_document", "index_fact_slots_entity",
                "index_fact_slots_key_status", "index_fact_slots_condition", "index_fact_items_slot",
                "index_fact_items_scope", "index_fact_items_condition", "index_relation_groups_entity", "index_relations_subject",
                "index_relations_object", "index_relations_group", "index_evidence_locator",
                "index_claim_evidence_evidence", "index_claim_evidence_claim", "index_visuals_entity",
                "index_visuals_status", "index_entity_cards_sort", "index_browse_facet_groups_entity",
                "index_browse_facets_group", "index_browse_facets_text", "index_browse_facets_integer",
                "index_browse_facets_range",
            )
            if (!indexes.containsAll(requiredIndexes)) {
                return AppResult.Failure(AppError.DatabaseCorrupted("schema 5 缺少必需索引"))
            }
            val entityColumns = database.rawQuery("PRAGMA table_info(entities)", null).use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            if ("extra_json" in entityColumns || "legacyFields" in entityColumns) {
                return AppResult.Failure(AppError.DatabaseCorrupted("检测到 schema 4 公共字段"))
            }
            if (validateV5SchemaFingerprint(database) != manifest.schemaFingerprint) {
                return AppResult.Failure(AppError.MetadataMismatch("schemaFingerprint"))
            }
            val capabilityRows = database.rawQuery(
                "SELECT capability, requirement FROM package_capabilities ORDER BY capability, requirement",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                }
            }
            val declaredCapabilities = buildList {
                manifest.capabilities.required.sorted().forEach { add(it to "required") }
                manifest.capabilities.optional.sorted().forEach { add(it to "optional") }
            }
            if (capabilityRows != declaredCapabilities) {
                return AppResult.Failure(AppError.MetadataMismatch("package_capabilities"))
            }
            val schemaSql = database.rawQuery(
                "SELECT lower(COALESCE(sql, '')) FROM sqlite_master WHERE sql IS NOT NULL",
                null,
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            if (schemaSql.any { "officialderived" in it || "legacyfields" in it }) {
                return AppResult.Failure(AppError.DatabaseCorrupted("检测到 schema 4 双语义字段"))
            }

            val meta = readRawMeta(database)
            validateV5Meta(meta, manifest)?.let { return AppResult.Failure(it) }
            val actualCount = rawCount(database, "entities")
            if (actualCount != manifest.content.entities) {
                return AppResult.Failure(AppError.DatabaseCorrupted("实体数量与清单不一致"))
            }
            validateRawEntityTypes(database, manifest.content)?.let { return AppResult.Failure(it) }
            validatePublishedCoverage(database, manifest)?.let { return AppResult.Failure(it) }
            validateV5Sources(database)?.let { return AppResult.Failure(it) }
            if (
                rawCount(database, "entity_cards") != actualCount ||
                rawCount(database, "entity_search") != actualCount
            ) {
                return AppResult.Failure(AppError.DatabaseCorrupted("schema 5 查询投影数量不一致"))
            }
            if (manifest.publishable) {
                validatePublishedV5Facts(database)?.let { return AppResult.Failure(it) }
            }
            val visualError = validateV5Visuals(root, database, manifest)
            if (visualError != null) return AppResult.Failure(visualError)
            val buildMeta = buildMetaFromRaw(meta)
            AppResult.Success(DataPackageInfo(manifest.database.sha256, manifest, buildMeta, missingImageCount = 0))
        } finally {
            database.close()
            validationCopy.delete()
        }
    }

    private fun validatePublishedCoverage(database: SQLiteDatabase, manifest: DataManifest): AppError? {
        if (!manifest.publishable) return null
        val release = manifest.coverage["release"] as? JsonObject
            ?: return AppError.MetadataMismatch("coverage.release")
        val core = release["core"] as? JsonObject
            ?: return AppError.MetadataMismatch("coverage.release.core")
        val bySlot = core["bySlot"] as? JsonObject
            ?: return AppError.MetadataMismatch("coverage.release.core.bySlot")
        val entityTypes = database.rawQuery(
            "SELECT DISTINCT entity_type FROM entities",
            null,
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val expectedSlots = entityTypes.flatMap { entityType ->
            CORE_FACT_SLOTS[entityType].orEmpty().map { slotKey -> "$entityType:$slotKey" }
        }.toSet()
        if (!bySlot.keys.containsAll(expectedSlots)) {
            return AppError.MetadataMismatch("coverage.release.core.bySlot")
        }
        for (key in expectedSlots) {
            val row = bySlot[key] as? JsonObject
                ?: return AppError.MetadataMismatch("coverage.$key")
            val answered = (row["answeredRate"] as? JsonPrimitive)?.doubleOrNull
                ?: return AppError.MetadataMismatch("coverage.$key.answeredRate")
            val notCollected = (row["notCollectedRate"] as? JsonPrimitive)?.doubleOrNull
                ?: return AppError.MetadataMismatch("coverage.$key.notCollectedRate")
            val minimum = (row["minimumAnsweredRate"] as? JsonPrimitive)?.doubleOrNull
                ?: return AppError.MetadataMismatch("coverage.$key.minimumAnsweredRate")
            val maximumMissing = (row["maximumNotCollectedRate"] as? JsonPrimitive)?.doubleOrNull
                ?: return AppError.MetadataMismatch("coverage.$key.maximumNotCollectedRate")
            if (answered !in 0.0..1.0 || notCollected !in 0.0..1.0 ||
                minimum !in 0.0..1.0 || maximumMissing !in 0.0..1.0 ||
                answered < minimum || notCollected > maximumMissing
            ) {
                return AppError.DatabaseCorrupted("核心事实覆盖未达发布门槛：$key")
            }
            val separator = key.indexOf(':')
            val entityType = key.substring(0, separator)
            val slotKey = key.substring(separator + 1)
            val missingRows = database.rawQuery(
                "SELECT COUNT(*) FROM entities e WHERE e.entity_type = ? " +
                    "AND NOT EXISTS (SELECT 1 FROM fact_slots f WHERE f.entity_id = e.id AND f.slot_key = ?)",
                arrayOf(entityType, slotKey),
            ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
            if (missingRows) return AppError.DatabaseCorrupted("核心事实槽缺失：$key")
        }
        val relationGroups = release["relationGroups"] as? JsonObject
            ?: return AppError.MetadataMismatch("coverage.release.relationGroups")
        val relationEligible = (relationGroups["eligible"] as? JsonPrimitive)?.intOrNull
            ?: return AppError.MetadataMismatch("coverage.relationGroups.eligible")
        val relationAnswered = (relationGroups["answeredRate"] as? JsonPrimitive)?.doubleOrNull
            ?: return AppError.MetadataMismatch("coverage.relationGroups.answeredRate")
        val relationMissing = (relationGroups["notCollectedRate"] as? JsonPrimitive)?.doubleOrNull
            ?: return AppError.MetadataMismatch("coverage.relationGroups.notCollectedRate")
        if (relationEligible < 0 || relationAnswered !in 0.0..1.0 || relationMissing !in 0.0..1.0 ||
            (relationEligible > 0 && (relationAnswered < 0.90 || relationMissing > 0.05))
        ) {
            return AppError.DatabaseCorrupted("人物关系组覆盖未达发布门槛")
        }
        val conditions = release["conditions"] as? JsonObject
            ?: return AppError.MetadataMismatch("coverage.release.conditions")
        val conditionTotal = listOf("complete", "partial", "opaque").sumOf { key ->
            (conditions[key] as? JsonPrimitive)?.intOrNull
                ?: return AppError.MetadataMismatch("coverage.conditions.$key")
        }
        val completeRate = (conditions["completeRate"] as? JsonPrimitive)?.doubleOrNull
            ?: return AppError.MetadataMismatch("coverage.conditions.completeRate")
        val opaqueRate = (conditions["opaqueRate"] as? JsonPrimitive)?.doubleOrNull
            ?: return AppError.MetadataMismatch("coverage.conditions.opaqueRate")
        if (conditionTotal < 0 || completeRate !in 0.0..1.0 || opaqueRate !in 0.0..1.0 ||
            (conditionTotal > 0 && (completeRate < 0.95 || opaqueRate > 0.01))
        ) {
            return AppError.DatabaseCorrupted("条件完整性未达发布门槛")
        }
        return null
    }

    private fun validateV5Sources(database: SQLiteDatabase): AppError? {
        val rows = database.rawQuery(
            "SELECT source_kind, source_url, revision, revision_at, game_version, platform, " +
                "language, reviewed_at, review_status, expires_at, conflict_status FROM source_documents",
            null,
        )
        rows.use { cursor ->
            while (cursor.moveToNext()) {
                val kind = cursor.getString(0)
                val sourceUrl = cursor.getString(1).takeIf { !cursor.isNull(1) }
                val revision = cursor.getString(2).takeIf { !cursor.isNull(2) }
                val revisionAt = cursor.getString(3).takeIf { !cursor.isNull(3) }
                val gameVersion = cursor.getString(4).takeIf { !cursor.isNull(4) }
                val platform = cursor.getString(5).takeIf { !cursor.isNull(5) }
                val language = cursor.getString(6).takeIf { !cursor.isNull(6) }
                val reviewedAt = cursor.getString(7).takeIf { !cursor.isNull(7) }
                val reviewStatus = cursor.getString(8)
                val expiresAt = cursor.getString(9).takeIf { !cursor.isNull(9) }
                val conflictStatus = cursor.getString(10)
                if (kind !in setOf("official_direct", "official_derived", "supplemental", "display_override")) {
                    return AppError.DatabaseCorrupted("来源类型无效：$kind")
                }
                if (kind == "supplemental") {
                    if (listOf(sourceUrl, revision, revisionAt, gameVersion, platform, language, reviewedAt)
                            .any { it.isNullOrBlank() } || reviewStatus != "approved" || conflictStatus != "none"
                    ) {
                        return AppError.DatabaseCorrupted("补充来源未通过审核")
                    }
                } else if (reviewStatus != "not_required" || conflictStatus != "none") {
                    return AppError.DatabaseCorrupted("官方来源审核状态无效")
                }
                if (expiresAt != null && runCatching { Instant.parse(expiresAt) }.getOrNull()?.let {
                        it <= Instant.now()
                    } != false
                ) {
                    return AppError.DatabaseCorrupted("来源已过期或时间格式无效")
                }
            }
        }
        return null
    }

    private suspend fun validateV5ReleaseArtifacts(root: File, manifest: DataManifest): AppError? {
        val conformance = resolveInside(root, "schema5-conformance.json")
            ?: return AppError.UnsafeArchiveEntry("schema5-conformance.json")
        val reports = resolveInside(root, "reports")
            ?: return AppError.UnsafeArchiveEntry("reports")
        if (!conformance.isFile || !reports.isDirectory) {
            return AppError.InvalidManifest("schema 5 发布证据文件缺失")
        }
        val conformanceDocument = runCatching {
            json.decodeFromString<Schema5Conformance>(conformance.readText())
        }.getOrNull() ?: return AppError.InvalidManifest("schema 5 conformance JSON 无效")
        if (
            conformanceDocument.status != "release" ||
            conformanceDocument.manifestVersion != manifest.manifestVersion ||
            conformanceDocument.schemaVersion != manifest.schemaVersion ||
            conformanceDocument.contentContract != manifest.contentContract ||
            conformanceDocument.publishable != manifest.publishable ||
            conformanceDocument.databaseSha256 != manifest.database.sha256 ||
            conformanceDocument.schemaFingerprint != manifest.schemaFingerprint
        ) {
            return AppError.MetadataMismatch("schema5-conformance")
        }
        val conformanceHash = hashUtils.sha256(conformance)
        val reportFiles = reports.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (reportFiles.isEmpty()) return AppError.InvalidManifest("schema 5 reports 文件缺失")
        if (reportFiles.any { runCatching { json.parseToJsonElement(it.readText()) }.isFailure }) {
            return AppError.InvalidManifest("schema 5 reports JSON 无效")
        }
        val reportDigest = MessageDigest.getInstance("SHA-256")
        reportFiles.forEach { file ->
            reportDigest.update("${file.name}:${hashUtils.sha256(file)}\n".toByteArray())
        }
        val reportHash = reportDigest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        if (manifest.artifacts["conformanceSha256"]?.equals(conformanceHash, ignoreCase = true) != true) {
            return AppError.HashMismatch
        }
        if (manifest.artifacts["reportsSha256"]?.equals(reportHash, ignoreCase = true) != true) {
            return AppError.HashMismatch
        }
        return null
    }

    private fun readRawMeta(database: SQLiteDatabase): Map<String, String> = database.rawQuery(
        "SELECT key, value FROM build_meta",
        null,
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
        }
    }

    private fun validateV5Meta(meta: Map<String, String>, manifest: DataManifest): AppError? {
        val required = listOf(
            "schema_version", "manifest_version", "content_contract", "schema_fingerprint",
            "builder_version", "locale", "generated_at", "entity_count", "game_version",
            "source_hash", "artifact_metadata",
        )
        val missing = required.filterNot(meta::containsKey)
        if (missing.isNotEmpty()) return AppError.DatabaseCorrupted("缺少元数据：${missing.joinToString()}")
        if (meta["schema_version"] != manifest.schemaVersion.toString()) {
            return AppError.MetadataMismatch("schema_version")
        }
        if (meta["manifest_version"] != manifest.manifestVersion.toString()) {
            return AppError.MetadataMismatch("manifest_version")
        }
        if (meta["content_contract"] != manifest.contentContract) {
            return AppError.MetadataMismatch("content_contract")
        }
        if (meta["schema_fingerprint"] != manifest.schemaFingerprint) {
            return AppError.MetadataMismatch("schema_fingerprint")
        }
        if (meta["locale"] != manifest.language) return AppError.MetadataMismatch("locale")
        if (meta["entity_count"] != manifest.content.entities.toString()) {
            return AppError.MetadataMismatch("entity_count")
        }
        if (meta["builder_version"] != manifest.builderVersion) return AppError.MetadataMismatch("builder_version")
        if (meta["generated_at"] != manifest.generatedAt) return AppError.MetadataMismatch("generated_at")
        if (meta["game_version"] != manifest.gameVersion) return AppError.MetadataMismatch("game_version")
        if (meta["source_hash"] != manifest.sourceHash) return AppError.MetadataMismatch("source_hash")
        val artifact = runCatching {
            json.decodeFromString<ArtifactMetadata>(meta.getValue("artifact_metadata"))
        }.getOrNull() ?: return AppError.DatabaseCorrupted("artifact_metadata 无效")
        return when {
            artifact.schemaVersion != manifest.schemaVersion ->
                AppError.MetadataMismatch("artifact_metadata.schemaVersion")
            artifact.manifestVersion != manifest.manifestVersion ->
                AppError.MetadataMismatch("artifact_metadata.manifestVersion")
            artifact.contentContract != manifest.contentContract ->
                AppError.MetadataMismatch("artifact_metadata.contentContract")
            artifact.schemaFingerprint != manifest.schemaFingerprint ->
                AppError.MetadataMismatch("artifact_metadata.schemaFingerprint")
            artifact.language != manifest.language -> AppError.MetadataMismatch("artifact_metadata.language")
            artifact.builderVersion != manifest.builderVersion ->
                AppError.MetadataMismatch("artifact_metadata.builderVersion")
            artifact.generatedAt != manifest.generatedAt ->
                AppError.MetadataMismatch("artifact_metadata.generatedAt")
            artifact.gameVersion != manifest.gameVersion ->
                AppError.MetadataMismatch("artifact_metadata.gameVersion")
            artifact.sourceHash != manifest.sourceHash ->
                AppError.MetadataMismatch("artifact_metadata.sourceHash")
            artifact.publishable != manifest.publishable ->
                AppError.MetadataMismatch("artifact_metadata.publishable")
            artifact.content != manifest.content -> AppError.MetadataMismatch("artifact_metadata.content")
            artifact.quality != manifest.quality -> AppError.MetadataMismatch("artifact_metadata.quality")
            artifact.capabilities != manifest.capabilities ->
                AppError.MetadataMismatch("artifact_metadata.capabilities")
            artifact.coverage != manifest.coverage ->
                AppError.MetadataMismatch("artifact_metadata.coverage")
            else -> null
        }
    }

    private fun validateRawEntityTypes(
        database: SQLiteDatabase,
        content: ManifestContent,
    ): AppError? {
        val declared = declaredTypeCounts(content) ?: return AppError.InvalidEntityTypeCatalog("声明统计不一致")
        val actual = database.rawQuery(
            "SELECT entity_type, COUNT(*) FROM entities GROUP BY entity_type",
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
            }
        }
        return if (actual == declared) null else AppError.InvalidEntityTypeCatalog("类型统计与数据库不一致")
    }

    private fun validatePublishedV5Facts(database: SQLiteDatabase): AppError? {
        if (rawCount(database, "fact_slots") == 0) return AppError.DatabaseCorrupted("发布包缺少事实槽")
        val invalidStatuses = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_slots
            WHERE status IS NULL OR status NOT IN
                ('fixed', 'conditional', 'dynamic_rule', 'unknown', 'not_collected', 'not_applicable')
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidStatuses) return AppError.DatabaseCorrupted("事实槽状态无效")
        val invalidSlotTypes = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_slots
            WHERE value_type IS NOT NULL AND value_type NOT IN ('text', 'integer', 'real', 'boolean')
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidSlotTypes) return AppError.DatabaseCorrupted("事实槽值类型无效")
        val invalidKnownValues = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_slots f
            WHERE f.status IN ('fixed', 'conditional', 'dynamic_rule')
              AND (
                f.value_type IS NULL OR
                NOT (
                    (
                        f.text_value IS NULL AND f.integer_value IS NULL AND
                        f.real_value IS NULL AND f.boolean_value IS NULL AND
                        EXISTS (SELECT 1 FROM fact_items i WHERE i.slot_id = f.id)
                    ) OR
                    (
                        (f.value_type = 'text' AND f.text_value IS NOT NULL AND f.integer_value IS NULL AND f.real_value IS NULL AND f.boolean_value IS NULL) OR
                        (f.value_type = 'integer' AND f.text_value IS NULL AND f.integer_value IS NOT NULL AND f.real_value IS NULL AND f.boolean_value IS NULL) OR
                        (f.value_type = 'real' AND f.text_value IS NULL AND f.integer_value IS NULL AND f.real_value IS NOT NULL AND f.boolean_value IS NULL) OR
                        (f.value_type = 'boolean' AND f.text_value IS NULL AND f.integer_value IS NULL AND f.real_value IS NULL AND f.boolean_value IS NOT NULL)
                    )
                )
              )
              OR f.status IN ('unknown', 'not_collected', 'not_applicable') AND
                 (f.text_value IS NOT NULL OR f.integer_value IS NOT NULL OR f.real_value IS NOT NULL OR f.boolean_value IS NOT NULL)
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidKnownValues) return AppError.DatabaseCorrupted("已知事实类型化值无效")
        val invalidItems = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_items
            WHERE value_type IS NULL OR value_type NOT IN ('text', 'integer', 'real', 'boolean')
               OR (value_type = 'text' AND (text_value IS NULL OR integer_value IS NOT NULL OR real_value IS NOT NULL OR boolean_value IS NOT NULL))
               OR (value_type = 'integer' AND (integer_value IS NULL OR text_value IS NOT NULL OR real_value IS NOT NULL OR boolean_value IS NOT NULL))
               OR (value_type = 'real' AND (real_value IS NULL OR text_value IS NOT NULL OR integer_value IS NOT NULL OR boolean_value IS NOT NULL))
               OR (value_type = 'boolean' AND (boolean_value IS NULL OR text_value IS NOT NULL OR integer_value IS NOT NULL OR real_value IS NOT NULL))
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidItems) return AppError.DatabaseCorrupted("事实项类型化值无效")
        val invalidItemSlotTypes = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_items i
            JOIN fact_slots f ON f.id = i.slot_id
            WHERE i.value_type != f.value_type
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidItemSlotTypes) return AppError.DatabaseCorrupted("事实项与事实槽值类型不一致")
        val invalidConditionalFacts = database.rawQuery(
            "SELECT COUNT(*) FROM fact_slots WHERE status = 'conditional' AND condition_set_id IS NULL",
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidConditionalFacts) return AppError.DatabaseCorrupted("条件事实缺少条件集合")
        val invalidItemsState = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_items i
            JOIN fact_slots f ON f.id = i.slot_id
            WHERE f.status IN ('unknown', 'not_collected', 'not_applicable')
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidItemsState) return AppError.DatabaseCorrupted("缺失事实不能包含事实项")
        val invalidItemConditions = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_items i
            JOIN condition_sets c ON c.id = i.condition_set_id
            WHERE c.completeness NOT IN ('complete', 'partial', 'opaque')
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidItemConditions) return AppError.DatabaseCorrupted("事实项条件完整性无效")
        val invalidRelations = database.rawQuery(
            """
            SELECT COUNT(*) FROM relations r
            JOIN relation_groups g ON g.id = r.relation_group_id
            WHERE r.subject_entity_id != g.entity_id
               OR r.original_direction IS NULL OR trim(r.original_direction) = ''
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidRelations) return AppError.DatabaseCorrupted("关系方向或主体无效")
        val invalidRelationGroups = database.rawQuery(
            """
            SELECT COUNT(*) FROM relation_groups g
            WHERE (g.status IN ('fixed', 'conditional') AND NOT EXISTS (
                SELECT 1 FROM relations r WHERE r.relation_group_id = g.id
            ))
               OR (g.status IN ('unknown', 'not_collected', 'not_applicable') AND EXISTS (
                SELECT 1 FROM relations r WHERE r.relation_group_id = g.id
            ))
               OR (g.status = 'conditional' AND g.condition_set_id IS NULL)
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidRelationGroups) return AppError.DatabaseCorrupted("关系组状态或边无效")
        val incompleteFixedConditions = database.rawQuery(
            """
            SELECT COUNT(*) FROM fact_slots f
            JOIN condition_sets c ON c.id = f.condition_set_id
            WHERE f.status = 'fixed' AND c.completeness != 'complete'
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (incompleteFixedConditions) return AppError.DatabaseCorrupted("固定事实引用不完整条件")
        val invalidEvidenceInputs = database.rawQuery(
            """
            SELECT COUNT(*) FROM evidence e
            WHERE e.input_claim_id IS NOT NULL
              AND NOT EXISTS (
                SELECT 1 FROM fact_slots f WHERE f.id = e.input_claim_id
                UNION ALL SELECT 1 FROM fact_items i WHERE i.id = e.input_claim_id
                UNION ALL SELECT 1 FROM relation_groups g WHERE g.id = e.input_claim_id
                UNION ALL SELECT 1 FROM relations r WHERE r.id = e.input_claim_id
                UNION ALL SELECT 1 FROM visuals v WHERE v.id = e.input_claim_id
                UNION ALL SELECT 1 FROM entity_cards c WHERE c.entity_id = e.input_claim_id
                UNION ALL SELECT 1 FROM browse_facets b WHERE b.id = e.input_claim_id
              )
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidEvidenceInputs) return AppError.DatabaseCorrupted("证据输入 claim 不存在")
        val invalidClaimTypes = database.rawQuery(
            """
            SELECT COUNT(*) FROM claim_evidence e
            WHERE NOT EXISTS (
                SELECT 1 FROM fact_slots f
                WHERE f.id = e.claim_id AND e.claim_type = 'fact_slot'
                UNION ALL SELECT 1 FROM fact_items i
                WHERE i.id = e.claim_id AND e.claim_type = 'fact_item'
                UNION ALL SELECT 1 FROM relation_groups g
                WHERE g.id = e.claim_id AND e.claim_type = 'relation_group'
                UNION ALL SELECT 1 FROM relations r
                WHERE r.id = e.claim_id AND e.claim_type = 'relation'
                UNION ALL SELECT 1 FROM visuals v
                WHERE v.id = e.claim_id AND e.claim_type = 'visual'
                UNION ALL SELECT 1 FROM entity_cards c
                WHERE c.entity_id = e.claim_id AND e.claim_type = 'card'
                UNION ALL SELECT 1 FROM browse_facets b
                WHERE b.id = e.claim_id AND e.claim_type = 'facet'
            )
            """.trimIndent(),
            null,
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) > 0 }
        if (invalidClaimTypes) return AppError.DatabaseCorrupted("claim 类型与对象不一致")
        val missingEvidence = database.rawQuery(
            """
            SELECT claim_id, claim_type
            FROM (
                SELECT f.id AS claim_id, 'fact_slot' AS claim_type
                FROM fact_slots f
                UNION ALL SELECT id, 'fact_item' FROM fact_items
                UNION ALL SELECT id, 'relation_group' FROM relation_groups
                UNION ALL SELECT id, 'relation' FROM relations
                UNION ALL SELECT id, 'visual' FROM visuals
                UNION ALL SELECT entity_id, 'card' FROM entity_cards
                UNION ALL SELECT id, 'facet' FROM browse_facets
            ) claims
            LEFT JOIN claim_evidence e ON e.claim_id = claims.claim_id AND e.claim_type = claims.claim_type
            WHERE e.claim_id IS NULL
            LIMIT 1
            """.trimIndent(),
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (missingEvidence != null) return AppError.DatabaseCorrupted("发布 claim 缺少证据：$missingEvidence")
        return null
    }

    private suspend fun validateV5Visuals(root: File, database: SQLiteDatabase, manifest: DataManifest): AppError? {
        val entityCount = rawCount(database, "entities")
        val visualEntityCount = database.rawQuery(
            "SELECT COUNT(DISTINCT entity_id) FROM visuals WHERE role = 'entity'",
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        if (manifest.publishable && visualEntityCount != entityCount) {
            return AppError.DatabaseCorrupted("视觉实体覆盖不完整")
        }
        if (manifest.publishable) {
            val invalidVisualState = database.rawQuery(
                "SELECT status FROM visuals WHERE status IN ('pending_review', 'package_error') LIMIT 1",
                null,
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            if (invalidVisualState != null) return AppError.DatabaseCorrupted("视觉状态未通过：$invalidVisualState")
        }
        val rows = database.rawQuery(
            "SELECT status, role, relative_path, sha256, source_entity_id, reuse_reason, crop_rect, rule_version FROM visuals",
            null,
        )
        rows.use { cursor ->
            while (cursor.moveToNext()) {
                val status = cursor.getString(0)
                val role = cursor.getString(1)
                val path = cursor.getString(2).takeIf { !cursor.isNull(2) }
                val expectedHash = cursor.getString(3).takeIf { !cursor.isNull(3) }
                val sourceEntityId = cursor.getString(4).takeIf { !cursor.isNull(4) }
                val reuseReason = cursor.getString(5).takeIf { !cursor.isNull(5) }
                val cropRect = cursor.getString(6).takeIf { !cursor.isNull(6) }
                val ruleVersion = cursor.getString(7).takeIf { !cursor.isNull(7) }
                if (manifest.publishable && (status == "pending_review" || status == "package_error")) {
                    return AppError.DatabaseCorrupted("视觉状态未通过：$status")
                }
                if (manifest.publishable && status == "proxy" && role != "proxy") {
                    return AppError.DatabaseCorrupted("代理视觉角色无效")
                }
                if (manifest.publishable && status == "official_reuse" &&
                    (sourceEntityId == null || reuseReason.isNullOrBlank())
                ) {
                    return AppError.DatabaseCorrupted("官方复用视觉绑定无效")
                }
                if (manifest.publishable && status in setOf("official_own", "official_reuse", "proxy") &&
                    (cropRect.isNullOrBlank() || ruleVersion.isNullOrBlank())
                ) {
                    return AppError.DatabaseCorrupted("视觉裁切规则绑定无效")
                }
                if (cropRect != null && !Regex("""\[\s*-?\d+\s*,\s*-?\d+\s*,\s*\d+\s*,\s*\d+\s*\]""").matches(cropRect)) {
                    return AppError.DatabaseCorrupted("视觉裁切矩形无效")
                }
                if (path == null) {
                    if (status != "official_none" && !(status == "package_error" && !manifest.publishable)) {
                        return AppError.DatabaseCorrupted("视觉路径为空")
                    }
                    continue
                }
                val imageFile = resolveInside(root, path)
                    ?: return AppError.UnsafeArchiveEntry(path)
                if (!imageFile.isFile) return AppError.ImageMissing(path)
                if (manifest.publishable &&
                    (expectedHash == null || !Regex("[a-fA-F0-9]{64}").matches(expectedHash) ||
                        !hashUtils.sha256(imageFile).equals(expectedHash, ignoreCase = true))
                ) {
                    return AppError.HashMismatch
                }
                validateImage(imageFile, path)?.let { return it }
            }
        }
        if (manifest.publishable) {
            val missingEntityVisual = database.rawQuery(
                """
                SELECT e.id FROM entities e
                LEFT JOIN visuals v ON v.entity_id = e.id AND v.role = 'entity'
                WHERE v.id IS NULL OR v.status IN ('pending_review', 'package_error')
                LIMIT 1
                """.trimIndent(),
                null,
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            if (missingEntityVisual != null) {
                return AppError.DatabaseCorrupted("实体视觉缺失：$missingEntityVisual")
            }
        }
        return null
    }

    private fun rawCount(database: SQLiteDatabase, table: String): Int = database.rawQuery(
        "SELECT COUNT(*) FROM $table",
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun buildMetaFromRaw(meta: Map<String, String>): BuildMeta = BuildMeta(
        schemaVersion = meta.getValue("schema_version").toInt(),
        builderVersion = meta.getValue("builder_version"),
        locale = meta.getValue("locale"),
        generatedAt = meta.getValue("generated_at"),
        entityCount = meta.getValue("entity_count").toInt(),
        gameVersion = meta.getValue("game_version"),
        sourceHash = meta.getValue("source_hash"),
        artifactMetadata = json.decodeFromString(meta.getValue("artifact_metadata")),
    )

    private fun validateV5SchemaFingerprint(database: SQLiteDatabase): String {
        val digest = MessageDigest.getInstance("SHA-256")
        database.rawQuery(
            """
            SELECT type, name, COALESCE(sql, '')
            FROM sqlite_master
            WHERE type IN ('table', 'index', 'trigger', 'view')
              AND name NOT LIKE 'sqlite_%'
              AND name NOT LIKE 'entity_search_%'
            ORDER BY type, name
            """.trimIndent(),
            null,
        ).use { cursor ->
            var first = true
            while (cursor.moveToNext()) {
                if (!first) digest.update('\n'.code.toByte()) else first = false
                digest.update(cursor.getString(0).toByteArray())
                digest.update(0x1f.toByte())
                digest.update(cursor.getString(1).toByteArray())
                digest.update(0x1f.toByte())
                digest.update(cursor.getString(2).toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    suspend fun validateLegacyRecovery(packageRoot: File): DataPackageInfo? = withContext(ioDispatcher) {
        val manifest = readManifest(packageRoot).getOrNull() ?: return@withContext null
        if (DataPackageContract.validateManifest(manifest) != null || manifest.schemaVersion != 4) {
            return@withContext null
        }
        val databaseFile = resolveInside(packageRoot, manifest.database.file) ?: return@withContext null
        if (!databaseFile.isFile || !hashUtils.sha256(databaseFile).equals(manifest.database.sha256, true)) {
            return@withContext null
        }
        val validationCopy = File.createTempFile("stardew-v4-recovery-", ".db")
        databaseFile.copyTo(validationCopy, overwrite = true)
        val database = databaseFactory.openForValidation(packageRoot, validationCopy).getOrNull()
            ?: run { validationCopy.delete(); return@withContext null }
        return@withContext try {
            database.quickCheck().failureOrNull()?.let { return@withContext null }
            val meta = database.getBuildMeta().getOrNull() ?: return@withContext null
            validateMeta(meta, manifest)?.let { return@withContext null }
            val count = database.entityCount().getOrNull() ?: return@withContext null
            if (count != meta.entityCount || count != manifest.content.entities) return@withContext null
            validateEntityTypes(database, manifest.content)?.let { return@withContext null }
            val searchCount = database.searchCount().getOrNull() ?: return@withContext null
            if (searchCount < count * MIN_SEARCH_INDEX_RATIO) return@withContext null
            val paths = database.imagePaths().getOrNull() ?: return@withContext null
            paths.forEach { imagePath ->
                val imageFile = resolveInside(packageRoot, imagePath) ?: return@withContext null
                if (!imageFile.isFile || validateImage(imageFile, imagePath) != null) return@withContext null
            }
            DataPackageInfo(manifest.database.sha256, manifest, meta, missingImageCount = 0)
        } finally {
            database.close()
            validationCopy.delete()
        }
    }

    private suspend fun validateDatabase(
        root: File,
        databaseFile: File,
        manifest: DataManifest,
    ): AppResult<DataPackageInfo> {
        val validationCopy = File.createTempFile("stardew-validation-", ".db")
        databaseFile.copyTo(validationCopy, overwrite = true)
        val databaseResult = databaseFactory.openForValidation(root, validationCopy)
        val database = databaseResult.getOrNull()
            ?: run {
                validationCopy.delete()
                return AppResult.Failure(databaseResult.failureOrNull() ?: AppError.DatabaseOpenFailed("校验打开失败"))
            }
        return try {
            database.quickCheck().failureOrNull()?.let { return AppResult.Failure(it) }
            val meta = database.getBuildMeta().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取 build_meta"))
            validateMeta(meta, manifest)?.let { return AppResult.Failure(it) }
            val count = database.entityCount().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取实体数量"))
            if (count != meta.entityCount || count != manifest.content.entities) {
                return AppResult.Failure(AppError.DatabaseCorrupted("实体数量与清单不一致"))
            }
            validateEntityTypes(database, manifest.content)?.let { return AppResult.Failure(it) }
            val searchCount = database.searchCount().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法读取搜索索引数量"))
            if (searchCount < count * MIN_SEARCH_INDEX_RATIO) {
                return AppResult.Failure(AppError.DatabaseCorrupted("搜索索引数量异常"))
            }
            val paths = database.imagePaths().getOrNull()
                ?: return AppResult.Failure(AppError.DatabaseCorrupted("无法校验图片路径"))
            paths.forEach { imagePath ->
                val imageFile = resolveInside(root, imagePath)
                    ?: return AppResult.Failure(AppError.UnsafeArchiveEntry(imagePath))
                if (!imageFile.isFile) return AppResult.Failure(AppError.ImageMissing(imagePath))
                validateImage(imageFile, imagePath)?.let { return AppResult.Failure(it) }
            }
            AppResult.Success(DataPackageInfo(manifest.database.sha256, manifest, meta, missingImageCount = 0))
        } finally {
            database.close()
            validationCopy.delete()
        }
    }

    /**
     * Rule: a declared image must decode and contain at least one visible pixel;
     * an existing but blank file is still a broken publish artifact.
     */
    private fun validateImage(file: File, imagePath: String): AppError? {
        val bitmap = BitmapFactory.decodeFile(file.path)
            ?: return AppError.ImageInvalid(imagePath, "无法解码")
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                AppError.ImageInvalid(imagePath, "尺寸无效")
            } else {
                val pixels = IntArray(bitmap.width)
                val visible = (0 until bitmap.height).any { y ->
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                    pixels.any { pixel -> pixel ushr 24 != 0 }
                }
                if (visible) null else AppError.ImageInvalid(imagePath, "内容完全透明")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun validateMeta(
        meta: com.example.stardewoffline.core.model.BuildMeta,
        manifest: DataManifest,
    ): AppError? {
        val artifact = meta.artifactMetadata
        return when {
            meta.schemaVersion != manifest.schemaVersion -> AppError.MetadataMismatch("build_meta.schema_version")
            meta.locale != manifest.language -> AppError.MetadataMismatch("build_meta.locale")
            meta.entityCount != manifest.content.entities -> AppError.MetadataMismatch("build_meta.entity_count")
            meta.builderVersion != manifest.builderVersion -> AppError.MetadataMismatch("build_meta.builder_version")
            meta.generatedAt != manifest.generatedAt -> AppError.MetadataMismatch("build_meta.generated_at")
            meta.gameVersion != manifest.gameVersion -> AppError.MetadataMismatch("build_meta.game_version")
            meta.sourceHash != manifest.sourceHash -> AppError.MetadataMismatch("build_meta.source_hash")
            artifact.schemaVersion != manifest.schemaVersion ->
                AppError.MetadataMismatch("artifact_metadata.schemaVersion")
            artifact.language != manifest.language -> AppError.MetadataMismatch("artifact_metadata.language")
            artifact.builderVersion != manifest.builderVersion ->
                AppError.MetadataMismatch("artifact_metadata.builderVersion")
            artifact.generatedAt != manifest.generatedAt -> AppError.MetadataMismatch("artifact_metadata.generatedAt")
            artifact.gameVersion != manifest.gameVersion -> AppError.MetadataMismatch("artifact_metadata.gameVersion")
            artifact.sourceHash != manifest.sourceHash -> AppError.MetadataMismatch("artifact_metadata.sourceHash")
            artifact.publishable != manifest.publishable -> AppError.MetadataMismatch("artifact_metadata.publishable")
            artifact.content != manifest.content -> AppError.MetadataMismatch("artifact_metadata.content")
            artifact.quality != manifest.quality -> AppError.MetadataMismatch("artifact_metadata.quality")
            else -> null
        }
    }

    private suspend fun validateEntityTypes(
        database: com.example.stardewoffline.core.database.content.ContentDatabase,
        content: ManifestContent,
    ): AppError? {
        val declared = declaredTypeCounts(content) ?: return AppError.InvalidEntityTypeCatalog("声明统计不一致")
        val actual = database.typeCounts().getOrNull()
            ?.associate { it.type to it.count }
            ?: return AppError.DatabaseCorrupted("无法读取实体类型统计")
        return if (actual == declared) null else AppError.InvalidEntityTypeCatalog("类型统计与数据库不一致")
    }

    private fun declaredTypeCounts(content: ManifestContent): Map<String, Int>? {
        val catalog = content.entityTypes
        if (
            catalog.isEmpty() ||
            catalog.any { it.id.isBlank() || it.displayName.isBlank() || it.count <= 0 }
        ) return null
        val catalogCounts = catalog.associate { it.id to it.count }
        if (catalogCounts.size != catalog.size || catalogCounts.values.sum() != content.entities) return null
        val basic = mapOf(
            "object" to content.objects,
            "crop" to content.crops,
            "fish" to content.fish,
            "villager" to content.villagers,
        )
            .filterValues { it > 0 }
        if (content.extraCounts.any { (id, count) -> id.isBlank() || count <= 0 || id in basic }) return null
        return (basic + content.extraCounts).takeIf { it == catalogCounts }
    }

    private fun resolveInside(root: File, path: String): File? = runCatching {
        File(root, path).canonicalFile.takeIf { it.path.startsWith(root.canonicalPath + File.separator) }
    }.getOrNull()

    private fun <T> AppResult<T>.failureOrNull(): AppError? = (this as? AppResult.Failure)?.error

    companion object {
        const val MIN_SEARCH_INDEX_RATIO = 0.8
    }
}
