package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.model.DataManifest

object DataPackageContract {
    const val FORMAT = "stardew-offline-data"
    const val LANGUAGE = "zh-CN"
    const val MANIFEST_VERSION = 2
    const val V5_SCHEMA_VERSION = 5
    const val LEGACY_V4_SCHEMA_VERSION = 4
    const val CONTENT_CONTRACT = "player-facts-v1"
    const val QUALITY_PASSED = "passed"

    // Schema 4 is a recovery-only asset and is never part of ordinary support.
    val supportedSchemaVersions = setOf(V5_SCHEMA_VERSION)
    val legacyRecoverySchemaVersions = setOf(4)
    val requiredV5Capabilities = setOf(
        "entities",
        "fact-slots",
        "relations",
        "conditions",
        "evidence",
        "visuals",
        "entity-cards",
        "browse-facets",
        "search",
        "id-aliases",
    )
    val knownV5Capabilities = requiredV5Capabilities + "supplemental-facts"

    fun validateManifest(manifest: DataManifest): AppError? = when {
        manifest.manifestVersion == MANIFEST_VERSION || manifest.contentContract != null ->
            validateV5Manifest(manifest)
        else -> validateLegacyManifest(manifest)
    }

    fun validateInstallManifest(manifest: DataManifest): AppError? = validateV5Manifest(manifest)

    /** Strict v5 admission used by staging and future package-only callers. */
    fun validateV5Manifest(manifest: DataManifest): AppError? = when {
        manifest.format != FORMAT -> AppError.InvalidPackageFormat("format 不匹配")
        manifest.schemaVersion != V5_SCHEMA_VERSION -> AppError.UnsupportedSchema(manifest.schemaVersion)
        manifest.manifestVersion != MANIFEST_VERSION -> AppError.InvalidManifest("manifestVersion 必须为 $MANIFEST_VERSION")
        manifest.contentContract != CONTENT_CONTRACT -> AppError.InvalidManifest("contentContract 不受支持")
        manifest.language != LANGUAGE -> AppError.InvalidManifest("language 必须为 $LANGUAGE")
        !manifest.publishable -> AppError.NotPublishable
        manifest.quality.status != QUALITY_PASSED || manifest.quality.dataErrors != 0 ||
            manifest.quality.translations.missing != 0 || manifest.quality.translations.invalid != 0 ->
            AppError.QualityFailed(manifest.quality.status, manifest.quality.dataErrors)
        manifest.database.file.isBlank() -> AppError.InvalidManifest("database.file 为空")
        !SHA256_PATTERN.matches(manifest.database.sha256) -> AppError.InvalidManifest("database.sha256 非法")
        !SHA256_PATTERN.matches(manifest.sourceHash) -> AppError.InvalidManifest("sourceHash 非法")
        !SHA256_PATTERN.matches(manifest.schemaFingerprint.orEmpty()) -> AppError.InvalidManifest("schemaFingerprint 非法")
        !SHA256_PATTERN.matches(manifest.database.schemaFingerprint.orEmpty()) -> AppError.InvalidManifest("database.schemaFingerprint 非法")
        manifest.schemaFingerprint != manifest.database.schemaFingerprint -> AppError.MetadataMismatch("schemaFingerprint")
        manifest.capabilities.required.toSet().intersect(manifest.capabilities.optional.toSet()).isNotEmpty() ->
            AppError.InvalidManifest("capabilities 不能同时声明 required 和 optional")
        manifest.capabilities.required.any { it !in knownV5Capabilities } ->
            AppError.InvalidManifest("包含未知必需能力")
        !manifest.capabilities.required.containsAll(requiredV5Capabilities) -> AppError.InvalidManifest("缺少必需能力")
        manifest.capabilities.required.any(String::isBlank) || manifest.capabilities.optional.any(String::isBlank) ->
            AppError.InvalidManifest("capabilities 无效")
        manifest.content.entities <= 0 -> AppError.InvalidManifest("entities 数量无效")
        else -> null
    }

    private fun validateLegacyManifest(manifest: DataManifest): AppError? = when {
        manifest.format != FORMAT -> AppError.InvalidPackageFormat("format 不匹配")
        manifest.schemaVersion != 4 -> AppError.UnsupportedSchema(manifest.schemaVersion)
        manifest.language != LANGUAGE -> AppError.InvalidManifest("language 必须为 $LANGUAGE")
        !manifest.publishable -> AppError.NotPublishable
        manifest.quality.status != QUALITY_PASSED || manifest.quality.dataErrors != 0 ||
            manifest.quality.translations.missing != 0 || manifest.quality.translations.invalid != 0 ->
            AppError.QualityFailed(manifest.quality.status, manifest.quality.dataErrors)
        manifest.database.file.isBlank() -> AppError.InvalidManifest("database.file 为空")
        !SHA256_PATTERN.matches(manifest.database.sha256) -> AppError.InvalidManifest("database.sha256 非法")
        !SHA256_PATTERN.matches(manifest.sourceHash) -> AppError.InvalidManifest("sourceHash 非法")
        manifest.content.entities <= 0 -> AppError.InvalidManifest("entities 数量无效")
        else -> null
    }

    private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
}
