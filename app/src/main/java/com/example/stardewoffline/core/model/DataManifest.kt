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
    val database: ManifestDatabase,
    val content: ManifestContent,
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
    @SerialName("missingTranslations") val missingTranslations: Int = 0,
)
