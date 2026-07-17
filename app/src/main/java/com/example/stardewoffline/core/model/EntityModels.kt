package com.example.stardewoffline.core.model

import kotlinx.serialization.json.JsonObject

data class EntitySummary(
    val id: String,
    val entityType: String,
    val nameZh: String,
    val nameEn: String?,
    val category: String?,
    val imagePath: String?,
    val sortKey: String?,
)

data class EntityDetail(
    val id: String,
    val entityType: String,
    val gameId: String?,
    val internalName: String?,
    val nameZh: String,
    val nameEn: String?,
    val descriptionZh: String?,
    val descriptionEn: String?,
    val category: String?,
    val translationStatus: TranslationStatus,
    val imagePath: String?,
    val extraJson: JsonObject,
    val sourceFile: String?,
    val createdAt: String,
)

data class EntityTypeCount(val type: String, val count: Int)

enum class TranslationStatus { COMPLETE, MISSING, NOT_APPLICABLE, UNKNOWN }
