package com.example.stardewoffline.testsupport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import com.example.stardewoffline.core.datapackage.DataPackageContract
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.ArtifactMetadata
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

enum class SyntheticPackageVariant { A, B }

enum class SyntheticSearchStorage { Fts4, PlainTable }

enum class SyntheticPackageFailure {
    UnsupportedSchema,
    LegacySchema,
    InvalidFormat,
    NotPublishable,
    QualityFailed,
    InvalidJson,
    MissingDatabase,
    HashMismatch,
    MissingBuildMeta,
    MissingSearchIndex,
    MismatchedEntityCount,
    MetadataMismatch,
    MissingImage,
    InvalidEntityTypeCatalog,
}

class SyntheticDataPackage(private val archiveRoot: File) : AutoCloseable {
    val archive = File(archiveRoot, "package.svdata")

    override fun close() {
        archiveRoot.deleteRecursively()
    }
}

class SyntheticDataPackageFactory(private val context: Context) {
    fun create(
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure? = null,
        searchStorage: SyntheticSearchStorage = SyntheticSearchStorage.Fts4,
    ): SyntheticDataPackage {
        val fixtures = File(context.filesDir, "test-fixtures")
        check(fixtures.isDirectory || fixtures.mkdirs()) { "无法创建测试夹具目录" }
        val root = File(fixtures, "wiki-fixture-${UUID.randomUUID()}")
        check(root.mkdirs()) { "无法创建测试数据包目录" }
        val database = createDatabase(root, variant, failure, searchStorage)
        if (failure != SyntheticPackageFailure.MissingImage) writeImage(root)
        writeManifest(root, database, variant, failure)
        return SyntheticDataPackage(root).also { writeArchive(root, it.archive, failure) }
    }

    private fun createDatabase(
        root: File,
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure?,
        searchStorage: SyntheticSearchStorage,
    ): File {
        val databaseFile = File(root, DATABASE_FILE)
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            createTables(database, failure, searchStorage)
            insertEntities(database, variant, failure)
            insertMetadata(database, variant, failure)
            if (failure != SyntheticPackageFailure.MissingSearchIndex) insertSearchDocuments(database, variant)
        } finally {
            database.close()
        }
        return databaseFile
    }

    private fun createTables(
        database: SQLiteDatabase,
        failure: SyntheticPackageFailure?,
        searchStorage: SyntheticSearchStorage,
    ) {
        if (failure != SyntheticPackageFailure.MissingBuildMeta) {
            database.execSQL("CREATE TABLE build_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        database.execSQL("CREATE TABLE entities (id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, game_id TEXT, internal_name TEXT, name_zh TEXT NOT NULL, name_en TEXT, description_zh TEXT, description_en TEXT, category TEXT, translation_status TEXT, image_path TEXT, extra_json TEXT NOT NULL, source_file TEXT, created_at TEXT NOT NULL)")
        database.execSQL("CREATE INDEX idx_entities_type ON entities(entity_type)")
        database.execSQL("CREATE INDEX idx_entities_name ON entities(name_zh)")
        database.execSQL("CREATE INDEX idx_entities_game_id ON entities(game_id)")
        database.execSQL("CREATE TABLE entity_aliases (id INTEGER PRIMARY KEY AUTOINCREMENT, entity_id TEXT NOT NULL, alias TEXT NOT NULL, alias_type TEXT NOT NULL)")
        if (failure != SyntheticPackageFailure.MissingSearchIndex) {
            val columns = "entity_id TEXT, name_zh TEXT, name_en TEXT, pinyin TEXT, pinyin_initials TEXT, aliases TEXT, keywords TEXT"
            val tableSql = if (searchStorage == SyntheticSearchStorage.Fts4) {
                "CREATE VIRTUAL TABLE entity_search USING fts4(${columns.replace(" TEXT", "")})"
            } else {
                "CREATE TABLE entity_search ($columns)"
            }
            database.execSQL(tableSql)
        }
    }

    private fun insertEntities(
        database: SQLiteDatabase,
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure?,
    ) {
        fixtureEntities(variant, failure).forEach { entity ->
            database.execSQL(
                "INSERT INTO entities VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entity.values(),
            )
        }
        database.execSQL("INSERT INTO entity_aliases(entity_id, alias, alias_type) VALUES (?, ?, ?)", arrayOf("object:1", "根菜", "synonym"))
    }

    private fun insertMetadata(
        database: SQLiteDatabase,
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure?,
    ) {
        if (failure == SyntheticPackageFailure.MissingBuildMeta) return
        val content = contentFor(variant, failure)
        val entityCount = content.entities - if (failure == SyntheticPackageFailure.MismatchedEntityCount) 1 else 0
        val sourceHash = if (failure == SyntheticPackageFailure.MetadataMismatch) "b".repeat(64) else SOURCE_HASH
        val quality = qualityFor(failure)
        val values = mapOf(
            "schema_version" to SCHEMA_VERSION.toString(),
            "builder_version" to "test-builder-1",
            "locale" to DataPackageContract.LANGUAGE,
            "generated_at" to GENERATED_AT,
            "entity_count" to entityCount.toString(),
            "game_version" to "test-${variant.name.lowercase()}",
            "source_hash" to sourceHash,
            "artifact_metadata" to json.encodeToString(
                ArtifactMetadata(
                    schemaVersion = SCHEMA_VERSION,
                    builderVersion = "test-builder-1",
                    language = DataPackageContract.LANGUAGE,
                    generatedAt = GENERATED_AT,
                    gameVersion = "test-${variant.name.lowercase()}",
                    sourceHash = sourceHash,
                    publishable = failure != SyntheticPackageFailure.NotPublishable,
                    content = content,
                    quality = quality,
                ),
            ),
        )
        values.forEach { (key, value) ->
            database.execSQL("INSERT INTO build_meta(key, value) VALUES (?, ?)", arrayOf(key, value))
        }
    }

    private fun insertSearchDocuments(database: SQLiteDatabase, variant: SyntheticPackageVariant) {
        fixtureEntities(variant, null).forEach { entity ->
            database.execSQL(
                "INSERT INTO entity_search VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(entity.id, entity.nameZh, entity.nameEn, entity.pinyin, entity.initials, entity.alias, entity.keywords),
            )
        }
    }

    private fun writeImage(root: File) {
        val image = File(root, IMAGE_FILE)
        image.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(203, 123, 78)) }
        image.outputStream().use { output ->
            @Suppress("DEPRECATION")
            check(bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)) { "无法写入 WebP 测试图片" }
        }
        bitmap.recycle()
    }

    private fun writeManifest(
        root: File,
        database: File,
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure?,
    ) {
        val manifest = manifestFor(database, variant, failure)
        val target = File(root, MANIFEST_FILE)
        target.writeText(if (failure == SyntheticPackageFailure.InvalidJson) "{" else json.encodeToString(manifest))
    }

    private fun manifestFor(
        database: File,
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure?,
    ): DataManifest {
        val content = contentFor(variant, failure)
        return DataManifest(
            format = if (failure == SyntheticPackageFailure.InvalidFormat) "invalid-format" else DataPackageContract.FORMAT,
            schemaVersion = when (failure) {
                SyntheticPackageFailure.UnsupportedSchema -> 999
                SyntheticPackageFailure.LegacySchema -> 2
                else -> SCHEMA_VERSION
            },
            builderVersion = "test-builder-1",
            gameVersion = "test-${variant.name.lowercase()}",
            language = DataPackageContract.LANGUAGE,
            generatedAt = GENERATED_AT,
            sourceHash = SOURCE_HASH,
            publishable = failure != SyntheticPackageFailure.NotPublishable,
            database = ManifestDatabase(
                file = if (failure == SyntheticPackageFailure.MissingDatabase) "missing.db" else DATABASE_FILE,
                sha256 = if (failure == SyntheticPackageFailure.HashMismatch) "0".repeat(64) else sha256(database),
            ),
            content = content,
            quality = qualityFor(failure),
        )
    }

    private fun writeArchive(root: File, target: File, failure: SyntheticPackageFailure?) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            addEntry(zip, File(root, MANIFEST_FILE), MANIFEST_FILE)
            if (failure != SyntheticPackageFailure.MissingDatabase) addEntry(zip, File(root, DATABASE_FILE), DATABASE_FILE)
            File(root, IMAGE_FILE).takeIf(File::isFile)?.let { addEntry(zip, it, IMAGE_FILE) }
        }
    }

    private fun addEntry(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class FixtureEntity(
        val id: String,
        val type: String,
        val nameZh: String,
        val nameEn: String,
        val pinyin: String,
        val initials: String,
        val alias: String,
        val keywords: String,
        val imagePath: String?,
        val extraJson: String,
    ) {
        fun values(): Array<Any?> = arrayOf(
            id, type, id.substringAfter(':'), id.substringAfter(':'), nameZh, nameEn,
            "$nameZh 的测试描述", "$nameEn test description", categoryLabel(), "complete", imagePath,
            extraJson, "fixture.json", GENERATED_AT,
        )

        private fun categoryLabel(): String = when (type) {
            "object" -> "蔬菜"
            "crop" -> "种子"
            "fish" -> "鱼类"
            "villager" -> "村民"
            else -> "其他"
        }
    }

    private fun contentFor(
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure?,
    ): ManifestContent {
        val entities = fixtureEntities(variant, failure)
        val catalog = entities.groupBy(FixtureEntity::type).map { (type, values) ->
            ManifestEntityType(type, displayName = typeDisplayName(type), count = values.size)
        }.toMutableList()
        if (failure == SyntheticPackageFailure.InvalidEntityTypeCatalog) {
            catalog[0] = catalog[0].copy(displayName = "")
        }
        return ManifestContent(
            entities = entities.size,
            objects = 1,
            crops = 1,
            fish = 1,
            villagers = if (variant == SyntheticPackageVariant.A) 1 else 0,
            entityTypes = catalog,
        )
    }

    private fun qualityFor(failure: SyntheticPackageFailure?): ManifestQuality = ManifestQuality(
        status = if (failure == SyntheticPackageFailure.QualityFailed) "failed" else DataPackageContract.QUALITY_PASSED,
        translations = ManifestTranslationQuality(
            complete = 4,
            missing = if (failure == SyntheticPackageFailure.QualityFailed) 1 else 0,
            invalid = 0,
            notApplicable = 0,
            unusable = 0,
        ),
        dataErrors = if (failure == SyntheticPackageFailure.QualityFailed) 1 else 0,
        unlabeledEntityTypes = emptyList(),
    )

    private fun typeDisplayName(type: String): String = when (type) {
        "object" -> "物品"
        "crop" -> "作物"
        "fish" -> "鱼类"
        "villager" -> "村民"
        else -> "测试分类"
    }

    private fun fixtureEntities(
        variant: SyntheticPackageVariant,
        failure: SyntheticPackageFailure? = null,
    ): List<FixtureEntity> = buildList {
        add(FixtureEntity("object:1", "object", "萝卜", "Turnip", "luo bo", "lb", "根菜", "蔬菜", null, "{}"))
        val cropImage = if (failure == SyntheticPackageFailure.MissingImage) "images/missing.webp" else IMAGE_FILE
        add(FixtureEntity("crop:1", "crop", "萝卜种子", "Turnip Seeds", "luo bo zhong zi", "lbzz", "萝卜种", "种子", cropImage, "{\"officialDerived\":{\"harvestItemId\":\"object:1\"}}"))
        add(FixtureEntity("fish:1", "fish", "测试鱼", "Test Fish", "ce shi yu", "csy", "试验鱼", "水域专用词", null, "{}"))
        if (variant == SyntheticPackageVariant.A) add(FixtureEntity("villager:Alice", "villager", "测试村民", "Alice", "ce shi cun min", "cscm", "爱丽丝", "村民", null, "{}"))
    }

    private companion object {
        const val SCHEMA_VERSION = 4
        const val GENERATED_AT = "2026-07-18T00:00:00Z"
        const val SOURCE_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val DATABASE_FILE = "stardew.db"
        const val MANIFEST_FILE = "manifest.json"
        const val IMAGE_FILE = "images/turnip.webp"
        val json = Json { explicitNulls = false }
    }
}
