package com.example.stardewoffline.core.datastore

data class AppPreferences(
    val activePackageId: String? = null,
    val previousPackageId: String? = null,
    val lastValidatedPackageId: String? = null,
    val themeMode: String = "system",
    val showEnglishName: Boolean = true,
    val showTechnicalFields: Boolean = false,
    val searchHistoryEnabled: Boolean = true,
    val listLayoutMode: String = "list",
)
