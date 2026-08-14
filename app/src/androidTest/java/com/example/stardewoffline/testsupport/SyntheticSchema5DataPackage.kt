package com.example.stardewoffline.testsupport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import com.example.stardewoffline.core.datapackage.DataPackageContract
import com.example.stardewoffline.core.model.ArtifactMetadata
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.ManifestCapabilities
import com.example.stardewoffline.core.model.ManifestContent
import com.example.stardewoffline.core.model.ManifestDatabase
import com.example.stardewoffline.core.model.ManifestEntityType
import com.example.stardewoffline.core.model.ManifestQuality
import com.example.stardewoffline.core.model.ManifestTranslationQuality
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Small publishable schema-5 fixture used by typed App instrumentation tests. */
class SyntheticSchema5DataPackage(private val root: File) : AutoCloseable {
    val archive = File(root, "package.svdata")

    override fun close() {
        root.deleteRecursively()
    }
}

class SyntheticSchema5DataPackageFactory(private val context: Context) {
    fun create(
        variant: SyntheticPackageVariant,
        searchStorage: SyntheticSearchStorage = SyntheticSearchStorage.Fts4,
    ): SyntheticSchema5DataPackage {
        val root = File(context.filesDir, "schema5-fixture-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "无法创建 schema 5 测试目录" }
        }
        writeImage(root)
        val database = createDatabase(root, variant, searchStorage)
        writeManifest(root, database, variant)
        writeReleaseEvidence(root, database, variant)
        bindManifestArtifacts(root)
        writeArchive(root)
        return SyntheticSchema5DataPackage(root)
    }

    private fun createDatabase(
        root: File,
        variant: SyntheticPackageVariant,
        searchStorage: SyntheticSearchStorage,
    ): File {
        val databaseFile = File(root, DATABASE_FILE)
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            database.execSQL("PRAGMA foreign_keys = ON")
            createTables(database, searchStorage)
            insertRows(database, variant, root)
            database.execSQL("PRAGMA user_version = 5")
        } finally {
            database.close()
        }
        return databaseFile
    }

    private fun createTables(database: SQLiteDatabase, searchStorage: SyntheticSearchStorage) {
        database.execSQL("CREATE TABLE build_meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        database.execSQL("CREATE TABLE package_capabilities (capability TEXT PRIMARY KEY NOT NULL, requirement TEXT NOT NULL)")
        database.execSQL("CREATE TABLE entities (id TEXT PRIMARY KEY NOT NULL, entity_type TEXT NOT NULL, game_id TEXT, internal_name TEXT, name_zh TEXT NOT NULL, name_en TEXT, description_zh TEXT, description_en TEXT, category TEXT, translation_status TEXT NOT NULL, created_at TEXT NOT NULL, UNIQUE(entity_type, game_id))")
        database.execSQL("CREATE TABLE entity_aliases (id INTEGER PRIMARY KEY, entity_id TEXT NOT NULL, alias TEXT NOT NULL, alias_type TEXT NOT NULL)")
        database.execSQL("CREATE TABLE id_aliases (alias_id TEXT PRIMARY KEY NOT NULL, entity_id TEXT NOT NULL, reason TEXT NOT NULL)")
        database.execSQL("CREATE TABLE condition_sets (id TEXT PRIMARY KEY NOT NULL, completeness TEXT NOT NULL, player_summary TEXT, original_text TEXT)")
        database.execSQL("CREATE TABLE condition_terms (id TEXT PRIMARY KEY NOT NULL, condition_set_id TEXT NOT NULL, ordinal INTEGER NOT NULL, kind TEXT NOT NULL, value_text TEXT, value_integer INTEGER, value_real REAL)")
        database.execSQL("CREATE TABLE source_documents (id TEXT PRIMARY KEY NOT NULL, source_kind TEXT NOT NULL, title TEXT NOT NULL, game_version TEXT, content_hash TEXT, revision TEXT, source_url TEXT, revision_at TEXT, platform TEXT, language TEXT, reviewed_at TEXT, review_status TEXT NOT NULL, expires_at TEXT, conflict_status TEXT NOT NULL)")
        database.execSQL("CREATE TABLE source_locators (id TEXT PRIMARY KEY NOT NULL, source_document_id TEXT NOT NULL, source_file TEXT, json_path TEXT, record_key TEXT)")
        database.execSQL("CREATE TABLE fact_slots (id TEXT PRIMARY KEY NOT NULL, entity_id TEXT NOT NULL, slot_key TEXT NOT NULL, status TEXT NOT NULL, value_type TEXT, text_value TEXT, integer_value INTEGER, real_value REAL, boolean_value INTEGER, unit TEXT, condition_set_id TEXT, UNIQUE(entity_id, slot_key))")
        database.execSQL("CREATE TABLE fact_items (id TEXT PRIMARY KEY NOT NULL, slot_id TEXT NOT NULL, ordinal INTEGER NOT NULL, value_type TEXT NOT NULL, text_value TEXT, integer_value INTEGER, real_value REAL, boolean_value INTEGER, unit TEXT, scope_id TEXT, condition_set_id TEXT, UNIQUE(slot_id, ordinal))")
        database.execSQL("CREATE TABLE relation_groups (id TEXT PRIMARY KEY NOT NULL, entity_id TEXT NOT NULL, family TEXT NOT NULL, status TEXT NOT NULL, condition_set_id TEXT, UNIQUE(entity_id, family))")
        database.execSQL("CREATE TABLE relations (id TEXT PRIMARY KEY NOT NULL, relation_group_id TEXT NOT NULL, subject_entity_id TEXT NOT NULL, predicate TEXT NOT NULL, object_entity_id TEXT NOT NULL, original_direction TEXT NOT NULL, label TEXT, condition_set_id TEXT, UNIQUE(subject_entity_id, predicate, object_entity_id))")
        database.execSQL("CREATE TABLE evidence (id TEXT PRIMARY KEY NOT NULL, source_locator_id TEXT NOT NULL, evidence_kind TEXT NOT NULL, transformation_rule TEXT, input_claim_id TEXT)")
        database.execSQL("CREATE TABLE claim_evidence (claim_id TEXT NOT NULL, evidence_id TEXT NOT NULL, claim_type TEXT NOT NULL, PRIMARY KEY(claim_id, evidence_id))")
        database.execSQL("CREATE TABLE visuals (id TEXT PRIMARY KEY NOT NULL, entity_id TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL, relative_path TEXT, sha256 TEXT, source_entity_id TEXT, crop_rect TEXT, rule_version TEXT, reuse_reason TEXT, UNIQUE(entity_id, role))")
        database.execSQL("CREATE TABLE entity_cards (entity_id TEXT PRIMARY KEY NOT NULL, identity_summary TEXT, action_summary_1 TEXT, action_summary_2 TEXT, category_label TEXT, sort_key TEXT NOT NULL)")
        database.execSQL("CREATE TABLE browse_facet_groups (id TEXT PRIMARY KEY NOT NULL, entity_id TEXT NOT NULL, family TEXT NOT NULL, status TEXT NOT NULL, UNIQUE(entity_id, family))")
        database.execSQL("CREATE TABLE browse_facets (id TEXT PRIMARY KEY NOT NULL, group_id TEXT NOT NULL, scope_family TEXT NOT NULL, scope_id TEXT NOT NULL, value_type TEXT NOT NULL, text_value TEXT, integer_value INTEGER, real_value REAL, boolean_value INTEGER, range_min REAL, range_max REAL, unit TEXT, claim_status TEXT NOT NULL, condition_set_id TEXT)")
        if (searchStorage == SyntheticSearchStorage.Fts4) {
            database.execSQL("CREATE VIRTUAL TABLE entity_search USING fts4(entity_id, name_zh, name_en, aliases, keywords, action_summaries, search_text)")
        } else {
            database.execSQL("CREATE TABLE entity_search (entity_id TEXT, name_zh TEXT, name_en TEXT, aliases TEXT, keywords TEXT, action_summaries TEXT, search_text TEXT)")
        }

        listOf(
            "index_entities_type" to "entities(entity_type)",
            "index_entities_name_zh" to "entities(name_zh)",
            "index_entities_game_id" to "entities(game_id)",
            "index_entity_aliases_entity" to "entity_aliases(entity_id)",
            "index_entity_aliases_alias" to "entity_aliases(alias)",
            "index_id_aliases_entity" to "id_aliases(entity_id)",
            "index_condition_terms_set" to "condition_terms(condition_set_id, ordinal)",
            "index_source_locators_document" to "source_locators(source_document_id)",
            "index_fact_slots_entity" to "fact_slots(entity_id, slot_key)",
            "index_fact_slots_key_status" to "fact_slots(slot_key, status)",
            "index_fact_slots_condition" to "fact_slots(condition_set_id)",
            "index_fact_items_slot" to "fact_items(slot_id, ordinal)",
            "index_fact_items_scope" to "fact_items(scope_id)",
            "index_fact_items_condition" to "fact_items(condition_set_id)",
            "index_relation_groups_entity" to "relation_groups(entity_id, family)",
            "index_relations_subject" to "relations(subject_entity_id, predicate, id)",
            "index_relations_object" to "relations(object_entity_id, predicate, id)",
            "index_relations_group" to "relations(relation_group_id)",
            "index_evidence_locator" to "evidence(source_locator_id)",
            "index_claim_evidence_evidence" to "claim_evidence(evidence_id)",
            "index_claim_evidence_claim" to "claim_evidence(claim_id, claim_type)",
            "index_visuals_entity" to "visuals(entity_id, role)",
            "index_visuals_status" to "visuals(status)",
            "index_entity_cards_sort" to "entity_cards(sort_key, entity_id)",
            "index_browse_facet_groups_entity" to "browse_facet_groups(entity_id, family)",
            "index_browse_facets_group" to "browse_facets(group_id, scope_family, scope_id)",
            "index_browse_facets_text" to "browse_facets(scope_family, text_value)",
            "index_browse_facets_integer" to "browse_facets(scope_family, integer_value)",
            "index_browse_facets_range" to "browse_facets(scope_family, range_min, range_max)",
        ).forEach { (name, expression) -> database.execSQL("CREATE INDEX $name ON $expression") }
    }

    private fun insertRows(database: SQLiteDatabase, variant: SyntheticPackageVariant, root: File) {
        val generatedAt = GENERATED_AT
        database.execSQL("INSERT INTO condition_sets(id, completeness, player_summary, original_text) VALUES ('condition:fixture:fish', 'complete', '春季可用', 'Season=Spring')")
        database.execSQL("INSERT INTO condition_terms(id, condition_set_id, ordinal, kind, value_text) VALUES ('condition-term:fixture:fish:season', 'condition:fixture:fish', 0, 'season', 'Spring')")
        database.execSQL("INSERT INTO source_documents(id, source_kind, title, game_version, review_status, conflict_status) VALUES ('source:fixture', 'official_direct', 'schema 5 instrumentation fixture', 'test', 'not_required', 'none')")
        database.execSQL("INSERT INTO source_locators(id, source_document_id, source_file, json_path, record_key) VALUES ('locator:fixture', 'source:fixture', 'fixture.json', '$.entities', 'fixture')")
        val entities = buildList {
            add(EntityRow("object:1", "object", "萝卜", "Turnip", "蔬菜", "object:1"))
            add(EntityRow("crop:1", "crop", "萝卜种子", "Turnip Seeds", "种子", "crop:1"))
            add(EntityRow("fish:1", "fish", "测试鱼", "Test Fish", "鱼类", "fish:1"))
            if (variant == SyntheticPackageVariant.A) add(EntityRow("villager:Alice", "villager", "测试村民", "Alice", "村民", "villager:Alice"))
        }
        entities.forEach { entity ->
            database.execSQL(
                "INSERT INTO entities(id, entity_type, game_id, internal_name, name_zh, name_en, description_zh, description_en, category, translation_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(entity.id, entity.type, entity.gameId, entity.gameId, entity.nameZh, entity.nameEn, "${entity.nameZh} 的测试资料", "${entity.nameEn} test entry", entity.category, "complete", generatedAt),
            )
            database.execSQL(
                "INSERT INTO entity_cards(entity_id, identity_summary, action_summary_1, action_summary_2, category_label, sort_key) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(entity.id, "${entity.nameZh} 的测试资料", "可作为测试条目浏览", null, entity.category, entity.nameZh),
            )
            val visualPath = if (entity.id == "crop:1") IMAGE_FILE else null
            val visualHash = visualPath?.let { sha256(File(root, IMAGE_FILE)) }
            val cropRect = visualPath?.let { "[0,0,1,1]" }
            database.execSQL(
                "INSERT INTO visuals(id, entity_id, role, status, relative_path, sha256, source_entity_id, crop_rect, rule_version, reuse_reason) VALUES (?, ?, 'entity', ?, ?, ?, NULL, ?, ?, NULL)",
                arrayOf("visual:${entity.id}:entity", entity.id, if (visualPath == null) "official_none" else "official_own", visualPath, visualHash, cropRect, if (visualPath == null) null else "fixture-visual-v1"),
            )
            database.execSQL(
                "INSERT INTO fact_slots(id, entity_id, slot_key, status, value_type, text_value, integer_value, real_value, boolean_value, unit, condition_set_id) VALUES (?, ?, ?, 'fixed', 'text', ?, NULL, NULL, NULL, NULL, NULL)",
                arrayOf("fact:${entity.id}:fixture", entity.id, "fixture_answer", "测试资料"),
            )
            database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES (?, 'locator:fixture', 'direct', NULL, NULL)", arrayOf("evidence:fact:${entity.id}"))
            database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES (?, ?, 'fact_slot')", arrayOf("fact:${entity.id}:fixture", "evidence:fact:${entity.id}"))
            if (entity.id == "villager:Alice") {
                database.execSQL(
                    "INSERT INTO fact_slots(id, entity_id, slot_key, status, value_type) VALUES (?, ?, 'schedule', 'fixed', 'text')",
                    arrayOf("fact:villager:Alice:schedule", entity.id),
                )
                database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES ('evidence:fact-slot:villager:Alice:schedule', 'locator:fixture', 'derived', 'fixture-schedule-v1', 'villager:Alice')")
                database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES ('fact:villager:Alice:schedule', 'evidence:fact-slot:villager:Alice:schedule', 'fact_slot')")
                database.execSQL(
                    "INSERT INTO fact_items(id, slot_id, ordinal, value_type, text_value, scope_id) VALUES (?, ?, 0, 'text', ?, ?)",
                    arrayOf("fact-item:villager:Alice:schedule:0", "fact:villager:Alice:schedule", "时间：09:00；地点：小镇", "schedule:fixture:0"),
                )
                database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES ('evidence:fact-item:villager:Alice:schedule:0', 'locator:fixture', 'derived', 'fixture-schedule-v1', 'villager:Alice')")
                database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES ('fact-item:villager:Alice:schedule:0', 'evidence:fact-item:villager:Alice:schedule:0', 'fact_item')")
                database.execSQL(
                    "INSERT INTO fact_slots(id, entity_id, slot_key, status, value_type) VALUES (?, ?, 'gift_preferences', 'fixed', 'text')",
                    arrayOf("fact:villager:Alice:gift_preferences", entity.id),
                )
                database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES ('evidence:fact-slot:villager:Alice:gift', 'locator:fixture', 'derived', 'fixture-gift-v1', 'villager:Alice')")
                database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES ('fact:villager:Alice:gift_preferences', 'evidence:fact-slot:villager:Alice:gift', 'fact_slot')")
                database.execSQL(
                    "INSERT INTO fact_items(id, slot_id, ordinal, value_type, text_value, scope_id) VALUES (?, ?, 0, 'text', ?, ?)",
                    arrayOf("fact-item:villager:Alice:gift:0", "fact:villager:Alice:gift_preferences", "object:1", "gift:fixture:loved:0"),
                )
                database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES ('evidence:fact-item:villager:Alice:gift:0', 'locator:fixture', 'derived', 'fixture-gift-v1', 'villager:Alice')")
                database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES ('fact-item:villager:Alice:gift:0', 'evidence:fact-item:villager:Alice:gift:0', 'fact_item')")
            }
            if (entity.id == "fish:1") {
                database.execSQL(
                    "INSERT INTO fact_items(id, slot_id, ordinal, value_type, text_value, integer_value, real_value, boolean_value, unit, scope_id, condition_set_id) VALUES (?, ?, 0, 'text', ?, NULL, NULL, NULL, NULL, ?, ?)",
                    arrayOf("fact-item:fish:1:fixture", "fact:${entity.id}:fixture", "海滩", "fishing:fish:1:beach", "condition:fixture:fish"),
                )
                database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES ('evidence:fact-item:fish:1:fixture', 'locator:fixture', 'direct', NULL, NULL)")
                database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES ('fact-item:fish:1:fixture', 'evidence:fact-item:fish:1:fixture', 'fact_item')")
            }
            database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES (?, 'locator:fixture', 'derived', 'fixture-visual-v1', NULL)", arrayOf("evidence:visual:${entity.id}"))
            database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES (?, ?, 'visual')", arrayOf("visual:${entity.id}:entity", "evidence:visual:${entity.id}"))
            database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES (?, 'locator:fixture', 'direct', NULL, NULL)", arrayOf("evidence:card:${entity.id}"))
            database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES (?, ?, 'card')", arrayOf(entity.id, "evidence:card:${entity.id}"))
            database.execSQL("INSERT INTO entity_search(entity_id, name_zh, name_en, aliases, keywords, action_summaries, search_text) VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf(entity.id, entity.nameZh, entity.nameEn, if (entity.id == "object:1") "根菜" else "", if (entity.id == "fish:1") "水域专用词" else entity.category, "可作为测试条目浏览", listOf(entity.nameZh, entity.nameEn, if (entity.id == "object:1") "根菜" else "", if (entity.id == "fish:1") "水域专用词" else entity.category, "可作为测试条目浏览", if (entity.id == "object:1") "lb" else "").filter { it.isNotBlank() }.joinToString(" ")))
        }
        insertCoreFactSlots(database, entities)
        database.execSQL("INSERT INTO id_aliases(alias_id, entity_id, reason) VALUES ('legacy:object:1', 'object:1', 'fixture migration alias')")
        database.execSQL("INSERT INTO entity_aliases(entity_id, alias, alias_type) VALUES ('object:1', '根菜', 'synonym')")
        database.execSQL("INSERT INTO browse_facet_groups(id, entity_id, family, status) VALUES ('facet-group:crop:season', 'crop:1', 'season', 'fixed')")
        database.execSQL("INSERT INTO browse_facets(id, group_id, scope_family, scope_id, value_type, text_value, claim_status) VALUES ('facet:crop:season:spring', 'facet-group:crop:season', 'season', 'season:crop:1', 'text', '春季', 'fixed')")
        database.execSQL("INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES ('evidence:facet:crop-season', 'locator:fixture', 'derived', 'fixture-facet-v1', NULL)")
        database.execSQL("INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES ('facet:crop:season:spring', 'evidence:facet:crop-season', 'facet')")
        val capabilities = DataPackageContract.requiredV5Capabilities.sorted()
        capabilities.forEach { database.execSQL("INSERT INTO package_capabilities(capability, requirement) VALUES (?, 'required')", arrayOf(it)) }
        val meta = mapOf(
            "schema_version" to "5", "manifest_version" to "2", "content_contract" to DataPackageContract.CONTENT_CONTRACT,
            "schema_fingerprint" to schemaFingerprint(database), "builder_version" to BUILDER_VERSION, "locale" to "zh-CN",
            "generated_at" to generatedAt, "entity_count" to entities.size.toString(), "game_version" to "test-${variant.name.lowercase()}",
            "source_hash" to sourceHash(variant),
        )
        val manifest = manifestFor(entities.size, variant, meta["schema_fingerprint"]!!, meta["source_hash"]!!)
        val artifact = ArtifactMetadata(
            schemaVersion = 5, builderVersion = BUILDER_VERSION, language = "zh-CN", generatedAt = generatedAt,
            gameVersion = manifest.gameVersion, sourceHash = manifest.sourceHash, publishable = true,
            content = manifest.content, quality = manifest.quality, manifestVersion = 2,
            contentContract = DataPackageContract.CONTENT_CONTRACT, schemaFingerprint = manifest.schemaFingerprint,
            capabilities = manifest.capabilities, coverage = manifest.coverage,
        )
        val rows = meta + ("artifact_metadata" to json.encodeToString(artifact))
        rows.forEach { (key, value) -> database.execSQL("INSERT INTO build_meta(key, value) VALUES (?, ?)", arrayOf(key, value)) }
    }

    private fun insertCoreFactSlots(database: SQLiteDatabase, entities: List<EntityRow>) {
        val coreSlots = mapOf(
            "object" to listOf("sell_price"),
            "crop" to listOf("seasons", "first_harvest_days", "regrow_days", "needs_watering", "seed_item_id", "harvest_item_id", "sell_price", "seed_purchase_price"),
            "fish" to listOf("difficulty", "behavior", "min_size", "max_size", "fishing_time", "seasons", "weather", "sell_price", "fishing_locations"),
            "villager" to listOf("residence_region", "birthday", "gender", "can_be_romanced"),
        )
        entities.forEach { entity ->
            coreSlots[entity.type].orEmpty().forEach { slotKey ->
                val slotId = "fact:${entity.id}:$slotKey"
                val evidenceId = "evidence:core:${entity.id}:$slotKey"
                database.execSQL(
                    "INSERT INTO fact_slots(id, entity_id, slot_key, status, value_type, text_value) VALUES (?, ?, ?, 'fixed', 'text', 'fixture')",
                    arrayOf(slotId, entity.id, slotKey),
                )
                database.execSQL(
                    "INSERT INTO evidence(id, source_locator_id, evidence_kind, transformation_rule, input_claim_id) VALUES (?, 'locator:fixture', 'direct', NULL, NULL)",
                    arrayOf(evidenceId),
                )
                database.execSQL(
                    "INSERT INTO claim_evidence(claim_id, evidence_id, claim_type) VALUES (?, ?, 'fact_slot')",
                    arrayOf(slotId, evidenceId),
                )
            }
        }
    }

    private fun writeManifest(root: File, database: File, variant: SyntheticPackageVariant) {
        val fingerprint = SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> schemaFingerprint(db) }
        val manifest = manifestFor(entityCount(variant), variant, fingerprint, sourceHash(variant)).copy(
            database = ManifestDatabase(DATABASE_FILE, sha256(database), fingerprint),
        )
        File(root, MANIFEST_FILE).writeText(json.encodeToString(manifest))
    }

    private fun manifestFor(count: Int, variant: SyntheticPackageVariant, fingerprint: String, sourceHash: String) = DataManifest(
        format = DataPackageContract.FORMAT, schemaVersion = 5, builderVersion = BUILDER_VERSION,
        gameVersion = "test-${variant.name.lowercase()}", language = "zh-CN", generatedAt = GENERATED_AT,
        sourceHash = sourceHash, publishable = true,
        database = ManifestDatabase(DATABASE_FILE, "0".repeat(64), fingerprint),
        content = ManifestContent(
            entities = count, objects = 1, crops = 1, fish = 1, villagers = if (variant == SyntheticPackageVariant.A) 1 else 0,
            entityTypes = buildList {
                add(ManifestEntityType("object", "物品", 1)); add(ManifestEntityType("crop", "作物", 1)); add(ManifestEntityType("fish", "鱼类", 1));
                if (variant == SyntheticPackageVariant.A) add(ManifestEntityType("villager", "村民", 1))
            },
        ),
        quality = ManifestQuality("passed", ManifestTranslationQuality(count, 0, 0, 0, 0), 0, emptyList()),
        manifestVersion = 2, contentContract = DataPackageContract.CONTENT_CONTRACT,
        schemaFingerprint = fingerprint,
        capabilities = ManifestCapabilities(required = DataPackageContract.requiredV5Capabilities.sorted()),
        coverage = releaseCoverage(variant),
    )

    private fun releaseCoverage(variant: SyntheticPackageVariant) = mapOf(
        "release" to json.parseToJsonElement(
            buildString {
                append("{\"core\":{\"bySlot\":{")
                val slots = buildList {
                    add("object:sell_price")
                    addAll(listOf("seasons", "first_harvest_days", "regrow_days", "needs_watering", "seed_item_id", "harvest_item_id", "sell_price", "seed_purchase_price").map { "crop:$it" })
                    addAll(listOf("difficulty", "behavior", "min_size", "max_size", "fishing_time", "seasons", "weather", "sell_price", "fishing_locations").map { "fish:$it" })
                    if (variant == SyntheticPackageVariant.A) {
                        addAll(listOf("residence_region", "birthday", "gender", "can_be_romanced").map { "villager:$it" })
                    }
                }
                append(slots.joinToString(",") { key ->
                    "\"$key\":{\"answeredRate\":1.0,\"notCollectedRate\":0.0,\"minimumAnsweredRate\":1.0,\"maximumNotCollectedRate\":0.0}"
                })
                append("},\"relationGroups\":{\"eligible\":0,\"answeredRate\":1.0,\"notCollectedRate\":0.0},\"conditions\":{\"complete\":1,\"partial\":0,\"opaque\":0,\"completeRate\":1.0,\"opaqueRate\":0.0}}")
            },
        ),
    )

    private fun writeReleaseEvidence(root: File, database: File, variant: SyntheticPackageVariant) {
        val fingerprint = SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { schemaFingerprint(it) }
        File(root, CONFORMANCE_FILE).writeText(
            """
            {"status":"release","manifestVersion":2,"schemaVersion":5,"contentContract":"${DataPackageContract.CONTENT_CONTRACT}","publishable":true,"databaseSha256":"${sha256(database)}","schemaFingerprint":"$fingerprint"}
            """.trimIndent(),
        )
        val reports = File(root, REPORTS_DIR).apply { mkdirs() }
        File(reports, "build-summary.json").writeText(
            "{\"status\":\"passed\",\"variant\":\"${variant.name}\"}",
        )
    }

    private fun bindManifestArtifacts(root: File) {
        val manifest = json.decodeFromString<DataManifest>(File(root, MANIFEST_FILE).readText())
        val conformance = File(root, CONFORMANCE_FILE)
        val reports = File(root, REPORTS_DIR)
        val digest = MessageDigest.getInstance("SHA-256")
        reports.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?.forEach { file -> digest.update("${file.name}:${sha256(file)}\\n".toByteArray()) }
        val reportHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val updated = manifest.copy(
            artifacts = mapOf(
                "conformanceSha256" to sha256(conformance),
                "reportsSha256" to reportHash,
            ),
        )
        File(root, MANIFEST_FILE).writeText(json.encodeToString(updated))
    }

    private fun writeArchive(root: File) {
        ZipOutputStream(File(root, "package.svdata").outputStream().buffered()).use { zip ->
            listOf(MANIFEST_FILE, DATABASE_FILE, IMAGE_FILE, CONFORMANCE_FILE, "$REPORTS_DIR/build-summary.json").forEach { name ->
                zip.putNextEntry(ZipEntry(name)); File(root, name).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    private fun writeImage(root: File) {
        val file = File(root, IMAGE_FILE).apply { parentFile?.mkdirs() }
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(203, 123, 78)) }
        file.outputStream().use { output ->
            @Suppress("DEPRECATION") check(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output))
        }
        bitmap.recycle()
    }

    private fun entityCount(variant: SyntheticPackageVariant) = if (variant == SyntheticPackageVariant.A) 4 else 3

    private fun schemaFingerprint(database: SQLiteDatabase): String {
        val digest = MessageDigest.getInstance("SHA-256")
        database.rawQuery("SELECT type, name, COALESCE(sql, '') FROM sqlite_master WHERE type IN ('table', 'index', 'trigger', 'view') AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'entity_search_%' ORDER BY type, name", null).use { cursor ->
            var first = true
            while (cursor.moveToNext()) {
                if (!first) digest.update('\n'.code.toByte()) else first = false
                digest.update(cursor.getString(0).toByteArray()); digest.update(0x1f.toByte())
                digest.update(cursor.getString(1).toByteArray()); digest.update(0x1f.toByte())
                digest.update(cursor.getString(2).toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun sourceHash(variant: SyntheticPackageVariant) = (if (variant == SyntheticPackageVariant.A) "a" else "b").repeat(64)

    private data class EntityRow(val id: String, val type: String, val nameZh: String, val nameEn: String, val category: String, val gameId: String)

    private companion object {
        const val DATABASE_FILE = "stardew.db"
        const val MANIFEST_FILE = "manifest.json"
        const val CONFORMANCE_FILE = "schema5-conformance.json"
        const val REPORTS_DIR = "reports"
        const val IMAGE_FILE = "images/turnip.webp"
        const val BUILDER_VERSION = "schema5-test-builder"
        const val GENERATED_AT = "2026-08-14T00:00:00Z"
        val json = Json { explicitNulls = false }
    }
}
