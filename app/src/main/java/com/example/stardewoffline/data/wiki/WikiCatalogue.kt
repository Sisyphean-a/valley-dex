package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.model.CategoryCover
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.ManifestEntityType
import com.example.stardewoffline.core.model.Schema5EntitySummary
import com.example.stardewoffline.core.model.WikiCategory
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySummary
import com.example.stardewoffline.core.model.WikiSearchHit
import com.example.stardewoffline.core.model.WikiSearchPage
import com.example.stardewoffline.core.model.WikiSearchQuery
import com.example.stardewoffline.core.model.WikiSection
import com.example.stardewoffline.core.common.AppResult

private val SUPPORT_ENTITY_TYPES = setOf("npc_schedule", "villager_gift")

interface WikiCatalogue {
    suspend fun sections(): AppResult<List<WikiSection>>
    suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage>
    suspend fun entry(id: String): AppResult<WikiEntry>
    suspend fun summaries(ids: List<String>): AppResult<Map<String, WikiEntrySummary>>
    suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>>

    suspend fun searchPage(query: WikiSearchQuery): AppResult<WikiSearchPage> = when (val result = search(query)) {
        is AppResult.Success -> AppResult.Success(WikiSearchPage(result.value, null))
        is AppResult.Failure -> result
    }
}

internal fun englishTitleForDisplay(title: String, englishTitle: String?): String? =
    englishTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title.trim(), ignoreCase = true) }

internal fun filterLabelsFor(category: WikiCategory, entries: List<WikiEntrySummary>): List<String> = when {
    category.entityTypes == setOf("crop") -> listOf("春季", "夏季", "秋季", "冬季")
    category.entityTypes == setOf("shop") -> listOf("常用", "非常用")
    category.entityTypes == setOf("villager") -> listOf("不可结婚村民", "可结婚女性村民", "可结婚男性村民")
    else -> entries.flatMap(WikiEntrySummary::filterCategories).distinct().sorted()
}

/** 商店类型的玩家筛选标签：常用 = 普通商店，非常用 = 节日/活动商店。 */
internal fun shopFilterLabel(facetText: String): String = when (facetText) {
    "普通商店" -> "常用"
    "节日商店" -> "非常用"
    else -> facetText
}

/** Maps only typed schema-5 facet rows to the list filter labels. */
internal fun browseFiltersFor(summary: Schema5EntitySummary): Set<String> =
    summary.facets.mapNotNull { facet -> facet.value.text?.let(::shopFilterLabel) }.toSet()

/** Shared category registry; support records are not browsable standalone entries. */
object WikiCatalogueConfiguration {
    private val groups = listOf(
        ConfiguredGroup("farm", "农场经营", listOf("object", "crop", "big_craftable", "tool", "furniture")),
        ConfiguredGroup("community", "人物与社区", listOf("villager", "shop")),
        ConfiguredGroup("exploration", "探索与战斗", listOf("monster", "fish", "mineral", "drop", "weapon", "footwear", "ring", "trinket", "ginger_island")),
        ConfiguredGroup("missions", "任务与收集", listOf("achievement", "bundle", "quest", "special_order")),
        ConfiguredGroup("crafting", "料理与制作", listOf("cooking_recipe", "crafting_recipe", "tailoring_recipe")),
    )

    fun sections(types: List<ManifestEntityType>): List<WikiSection> {
        val available = types
            .filter { it.count > 0 && it.id !in SUPPORT_ENTITY_TYPES }
            .associateBy(ManifestEntityType::id)
        val groupedTypes = groups.flatMap(ConfiguredGroup::types).toSet()
        val sections = groups.mapNotNull { group ->
            group.types.mapNotNull(available::get).map(::toTypeCategory)
                .takeIf(List<WikiCategory>::isNotEmpty)
                ?.let { WikiSection("catalogue-${group.id}", group.title, it) }
        }.toMutableList()
        val remaining = available.values
            .filterNot { it.id in groupedTypes }
            .sortedBy(ManifestEntityType::displayName)
            .map(::toTypeCategory)
        if (remaining.isNotEmpty()) sections += WikiSection("catalogue-other", "其他资料", remaining)
        return sections
    }

    private fun toTypeCategory(type: ManifestEntityType) = WikiCategory(
        id = "type:${type.id}",
        title = type.displayName,
        entityTypes = setOf(type.id),
        entryCount = type.count,
        cover = CategoryCover("type-${type.id}"),
    )

    private data class ConfiguredGroup(val id: String, val title: String, val types: List<String>)
}
