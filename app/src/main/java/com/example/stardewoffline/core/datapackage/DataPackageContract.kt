package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.model.DataManifest

object DataPackageContract {
    const val FORMAT = "stardew-offline-data"
    const val LANGUAGE = "zh-CN"
    val supportedSchemaVersions = setOf(2)

    fun validateManifest(manifest: DataManifest): AppError? = when {
        manifest.format != FORMAT -> AppError.InvalidPackageFormat("format 不匹配")
        manifest.schemaVersion !in supportedSchemaVersions -> AppError.UnsupportedSchema(manifest.schemaVersion)
        manifest.language != LANGUAGE -> AppError.InvalidManifest("language 必须为 $LANGUAGE")
        manifest.database.file.isBlank() -> AppError.InvalidManifest("database.file 为空")
        !SHA256_PATTERN.matches(manifest.database.sha256) -> AppError.InvalidManifest("database.sha256 非法")
        manifest.content.entities <= 0 -> AppError.InvalidManifest("entities 数量无效")
        else -> null
    }

    private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
}
