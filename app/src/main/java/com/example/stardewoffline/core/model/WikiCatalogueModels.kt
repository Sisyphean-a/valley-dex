package com.example.stardewoffline.core.model

data class WikiSection(
    val id: String,
    val title: String,
    val categories: List<WikiCategory>,
)

data class WikiCategory(
    val id: String,
    val title: String,
    val entityTypes: Set<String>,
    val entryCount: Int,
    val cover: CategoryCover,
)

data class CategoryCover(val assetKey: String)

data class CatalogueQuery(
    val categoryId: String,
    val keyword: String? = null,
    val entryCategory: String? = null,
    val displayMode: CatalogueDisplayMode = CatalogueDisplayMode.List,
)

enum class CatalogueDisplayMode { List, Grid }

data class CataloguePage(
    val category: WikiCategory,
    val entries: List<WikiEntrySummary>,
    val availableEntryCategories: List<String>,
)

data class WikiEntrySummary(
    val id: String,
    val title: String,
    val englishTitle: String?,
    val categoryLabel: String,
    val filterCategory: String?,
    val image: EntryImage,
)

data class WikiSearchQuery(
    val text: String,
    val entityTypes: Set<String> = emptySet(),
)

data class WikiSearchHit(
    val entry: WikiEntrySummary,
    val entityTypeId: String,
    val reason: String,
)

data class WikiEntry(
    val id: String,
    val title: String,
    val englishTitle: String?,
    val categoryLabel: String,
    val image: EntryImage,
    val summary: String?,
    val sections: List<EntrySection>,
    val relations: List<EntryRelation>,
)

data class EntrySection(val title: String, val facts: List<EntryFact>)

data class EntryFact(val label: String, val value: String)

data class EntryRelation(
    val section: String,
    val label: String,
    val details: List<EntryFact>,
    val target: RelationTarget,
)

sealed interface RelationTarget {
    data class Entry(val id: String, val title: String) : RelationTarget
    data class ReadableText(val value: String) : RelationTarget
    data class Unavailable(val message: String) : RelationTarget
}

sealed interface EntryImage {
    data class Packaged(val relativePath: String) : EntryImage
    data object Missing : EntryImage
}
