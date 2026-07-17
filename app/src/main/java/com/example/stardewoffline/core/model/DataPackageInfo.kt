package com.example.stardewoffline.core.model

data class DataPackageInfo(
    val id: String,
    val manifest: DataManifest,
    val buildMeta: BuildMeta,
    val missingImageCount: Int,
)
