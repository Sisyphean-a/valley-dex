package com.example.stardewoffline.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DataManifest(
    val format: String,
    val schemaVersion: Int,
    val builderVersion: String,
    val gameVersion: String,
    val language: String,
    val generatedAt: String,
    val sourceHash: String,
    val publishable: Boolean,
    val database: ManifestDatabase,
    val content: ManifestContent,
    val quality: ManifestQuality,
)

@Serializable
data class ManifestDatabase(
    val file: String,
    val sha256: String,
)

@Serializable
data class ManifestContent(
    val entities: Int,
    val objects: Int = 0,
    val crops: Int = 0,
    val fish: Int = 0,
    val villagers: Int = 0,
    val extraCounts: Map<String, Int> = emptyMap(),
    @SerialName("missingTranslations") val missingTranslations: Int = 0,
    val entityTypes: List<ManifestEntityType> = emptyList(),
)

@Serializable
data class ManifestEntityType(
    val id: String,
    val displayName: String,
    val count: Int,
)

@Serializable
data class ManifestQuality(
    val status: String,
    val translations: ManifestTranslationQuality,
    val dataErrors: Int,
    val unlabeledEntityTypes: List<String>,
)

@Serializable
data class ManifestTranslationQuality(
    val complete: Int,
    val missing: Int,
    val invalid: Int,
    val notApplicable: Int,
    val unusable: Int,
)

@Serializable
data class ArtifactMetadata(
    val schemaVersion: Int,
    val builderVersion: String,
    val language: String,
    val generatedAt: String,
    val gameVersion: String,
    val sourceHash: String,
    val publishable: Boolean,
    val content: ManifestContent,
    val quality: ManifestQuality,
)
