package com.example.stardewoffline.core.model

data class BuildMeta(
    val schemaVersion: Int,
    val builderVersion: String,
    val locale: String,
    val generatedAt: String,
    val entityCount: Int,
    val gameVersion: String,
    val sourceHash: String,
    val artifactMetadata: ArtifactMetadata,
)
