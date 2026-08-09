package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.datapackage.DataPackageManager
import com.example.stardewoffline.core.json.DetailPresentationParser
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.CategoryCover
import com.example.stardewoffline.core.model.EntryFact
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.EntryRelation
import com.example.stardewoffline.core.model.EntrySection
import com.example.stardewoffline.core.model.ManifestEntityType
import com.example.stardewoffline.core.model.RelationTarget
import com.example.stardewoffline.core.model.WikiCategory
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySubmenu
import com.example.stardewoffline.core.model.WikiEntrySubmenuGroup
import com.example.stardewoffline.core.model.WikiEntrySubmenuItem
import com.example.stardewoffline.core.model.WikiEntrySummary
import com.example.stardewoffline.core.model.WikiSearchHit
import com.example.stardewoffline.core.model.WikiSearchQuery
import com.example.stardewoffline.core.model.WikiSection
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.EntityRelationResolver
import com.example.stardewoffline.data.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton

private val SUPPORT_ENTITY_TYPES = setOf("npc_schedule", "villager_gift")

interface WikiCatalogue {
    suspend fun sections(): AppResult<List<WikiSection>>
    suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage>
    suspend fun entry(id: String): AppResult<WikiEntry>
    suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>>
}

@Singleton
class DefaultWikiCatalogue @Inject constructor(
    private val packages: DataPackageManager,
    private val content: ContentRepository,
    private val relations: EntityRelationResolver,
    private val search: SearchRepository,
) : WikiCatalogue {
    override suspend fun sections(): AppResult<List<WikiSection>> = when (val result = packageTypes()) {
        is AppResult.Success -> AppResult.Success(WikiCatalogueConfiguration.sections(result.value))
        is AppResult.Failure -> result
    }

    override suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage> {
        val types = packageTypes().getOrNull() ?: return AppResult.Failure(AppError.NoDataPackage)
        val category = WikiCatalogueConfiguration.sections(types).asSequence()
            .flatMap { it.categories }
            .firstOrNull { it.id == query.categoryId }
            ?: return AppResult.Failure(AppError.InvalidManifest("未知图鉴分类：${query.categoryId}"))
        val summaries = loadSummaries(category.entityTypes, types.associate { it.id to it.displayName })
        val entries = summaries.getOrNull() ?: return summaries.failure()
        return AppResult.Success(
            CataloguePage(
                category = category,
                entries = filterEntries(entries, query.keyword, query.entryCategory),
                availableEntryCategories = entries.mapNotNull(WikiEntrySummary::filterCategory).distinct().sorted(),
            ),
        )
    }

    override suspend fun entry(id: String): AppResult<WikiEntry> {
        val entity = content.detail(id).getOrNull()
            ?: return AppResult.Failure(AppError.DatabaseQueryFailed("当前数据包中未找到此条目"))
        if (entity.nameZh.isBlank() || entity.translationStatus == com.example.stardewoffline.core.model.TranslationStatus.MISSING) {
            return AppResult.Failure(AppError.DatabaseCorrupted("条目缺少可读中文名"))
        }
        val typeLabel = packageTypes().getOrNull()?.firstOrNull { it.id == entity.entityType }?.displayName
            ?: return AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：${entity.entityType}"))
        return buildEntry(id, entity, typeLabel)
    }

    override suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>> {
        val labels = packageTypes().getOrNull()?.associate { it.id to it.displayName }
            ?: return AppResult.Failure(AppError.NoDataPackage)
        return when (val result = search.search(query.text)) {
            is AppResult.Success -> mapSearchHits(result.value, labels, query.entityTypes)
            is AppResult.Failure -> result
        }
    }

    private suspend fun buildEntry(
        id: String,
        entity: com.example.stardewoffline.core.model.EntityDetail,
        typeLabel: String,
    ): AppResult<WikiEntry> {
        val presentation = DetailPresentationParser.present(entity)
        val targets = relations.resolve(presentation.relationGroups.flatMap { it.relations })
        val aliases = content.aliases(id).getOrNull().orEmpty()
        val submenus = if (entity.entityType == "villager") {
            val sourceId = entity.id.substringAfter(':', entity.id)
            val supportIds = content.supportIds(sourceId)
            if (supportIds is AppResult.Failure) return AppResult.Failure(supportIds.error)
            val supportDetails = content.detailsByIds((supportIds as AppResult.Success).value)
            if (supportDetails is AppResult.Failure) return AppResult.Failure(supportDetails.error)
            val details = (supportDetails as AppResult.Success).value
            val support = VillagerSupportPresentationBuilder.build(
                sourceId,
                details.filter { it.entityType == "npc_schedule" },
                details.filter { it.entityType == "villager_gift" },
            )
            val giftTargets = relations.resolve(
                support.giftItemIds.map { DetailRelation("礼物偏好", it) },
            )
            supportSubmenus(support, giftTargets)
        } else emptyList()
        return AppResult.Success(
            WikiEntry(
                id = id,
                title = entity.nameZh,
                englishTitle = englishTitleForDisplay(entity.nameZh, entity.nameEn),
                categoryLabel = typeLabel,
                image = entity.imagePath?.let(EntryImage::Packaged) ?: EntryImage.Missing,
                summary = entity.descriptionZh?.takeIf(String::isNotBlank) ?: entity.descriptionEn?.takeIf(String::isNotBlank),
                sections = entrySections(presentation.facts, aliases),
                relations = presentation.relationGroups.flatMap { group -> group.relations.map { toEntryRelation(group.title, it, targets) } },
                submenus = submenus,
            ),
        )
    }

    private suspend fun packageTypes(): AppResult<List<ManifestEntityType>> {
        val info = packages.openActive().getOrNull() ?: return AppResult.Failure(AppError.NoDataPackage)
        return AppResult.Success(info.manifest.content.entityTypes)
    }

    private suspend fun loadSummaries(
        types: Set<String>,
        typeLabels: Map<String, String>,
    ): AppResult<List<WikiEntrySummary>> {
        val entries = mutableListOf<WikiEntrySummary>()
        for (type in types) {
            val label = typeLabels[type] ?: return AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：$type"))
            when (val result = content.summaries(type)) {
                is AppResult.Success -> entries += result.value.map { toWikiSummary(it, label) }
                is AppResult.Failure -> return result
            }
        }
        return AppResult.Success(entries)
    }

    private fun mapSearchHits(
        results: List<com.example.stardewoffline.core.model.SearchResult>,
        labels: Map<String, String>,
        selectedTypes: Set<String>,
    ): AppResult<List<WikiSearchHit>> {
        val hits = mutableListOf<WikiSearchHit>()
        for (result in results) {
            if (result.summary.entityType in SUPPORT_ENTITY_TYPES) continue
            if (selectedTypes.isNotEmpty() && result.summary.entityType !in selectedTypes) continue
            val label = labels[result.summary.entityType]
                ?: return AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：${result.summary.entityType}"))
            hits += WikiSearchHit(toWikiSummary(result.summary, label), result.summary.entityType, result.reason)
        }
        return AppResult.Success(hits)
    }

    private fun filterEntries(entries: List<WikiEntrySummary>, keyword: String?, entryCategory: String?): List<WikiEntrySummary> {
        val term = keyword?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
        return entries.filter { entry ->
            (term == null || entry.title.lowercase().contains(term) || entry.englishTitle?.lowercase()?.contains(term) == true) &&
                (entryCategory == null || entry.filterCategory == entryCategory)
        }
    }

    private fun entrySections(
        facts: List<com.example.stardewoffline.core.model.DetailFact>,
        aliases: List<String>,
    ): List<EntrySection> =
        facts.takeIf { it.isNotEmpty() }?.let { listOf(EntrySection("核心信息", it.map(::toEntryFact))) }.orEmpty() +
            aliases.takeIf { it.isNotEmpty() }?.let { listOf(EntrySection("别名", listOf(EntryFact("别名", it.joinToString("、"))))) }.orEmpty()

    private fun toWikiSummary(
        summary: com.example.stardewoffline.core.model.EntitySummary,
        typeLabel: String,
    ) = WikiEntrySummary(
        id = summary.id,
        title = summary.nameZh,
        englishTitle = englishTitleForDisplay(summary.nameZh, summary.nameEn),
        categoryLabel = typeLabel,
        filterCategory = summary.category,
        image = summary.imagePath?.let(EntryImage::Packaged) ?: EntryImage.Missing,
    )

    private fun toEntryFact(fact: com.example.stardewoffline.core.model.DetailFact) = EntryFact(fact.label, fact.value)

    private fun scheduleGroups(schedules: List<VillagerScheduleItem>): List<WikiEntrySubmenuGroup> {
        val order = listOf("春季", "夏季", "秋季", "冬季", "通用日期", "天气与节日", "婚后日程", "其他特殊日程")
        return order.mapNotNull { group ->
            val items = schedules.filter { it.group == group }.sortedBy { it.order }
            items.takeIf { it.isNotEmpty() }?.let {
                WikiEntrySubmenuGroup(
                    group,
                    it.map { schedule -> WikiEntrySubmenuItem(schedule.label, schedule.details.map { fact -> EntryFact(fact.label, fact.value) }) },
                )
            }
        }
    }

    private fun supportSubmenus(
        support: VillagerSupportPresentation,
        targets: Map<String, com.example.stardewoffline.core.model.EntitySummary>,
    ): List<WikiEntrySubmenu> = buildList {
        if (support.schedules.isNotEmpty()) add(
            WikiEntrySubmenu(
                title = "日程",
                summary = "${support.schedules.size} 条季节/日期规则",
                groups = scheduleGroups(support.schedules),
            ),
        )
        if (support.gifts.values.any { it.isNotEmpty() }) {
            add(
                WikiEntrySubmenu(
                    title = "礼物偏好",
                    summary = "${support.gifts.values.sumOf { it.size }} 项偏好",
                    groups = support.gifts.map { (label, items) ->
                        WikiEntrySubmenuGroup(
                            label,
                            items.map { item ->
                                val target = item.itemId?.let { raw ->
                                    targets[raw]?.let { summary -> RelationTarget.Entry(summary.id, summary.nameZh) }
                                }
                                WikiEntrySubmenuItem(
                                    label = target?.displayName() ?: item.readableLabel ?: "物品暂未收录",
                                    details = item.details.map { EntryFact(it.label, it.value) },
                                    target = target,
                                )
                            },
                        )
                    },
                ),
            )
        }
    }

    private fun RelationTarget.displayName(): String = when (this) {
        is RelationTarget.Entry -> title
        is RelationTarget.ReadableText -> value
        is RelationTarget.Unavailable -> message
    }

    private fun toEntryRelation(
        section: String,
        relation: DetailRelation,
        targets: Map<String, com.example.stardewoffline.core.model.EntitySummary>,
    ): EntryRelation {
        val target = targets[relation.targetId]?.let { RelationTarget.Entry(it.id, it.nameZh) }
            ?: relation.details.firstOrNull()?.value?.takeIf(String::isNotBlank)?.let(RelationTarget::ReadableText)
            ?: RelationTarget.Unavailable("关联内容暂未收录")
        return EntryRelation(section, relation.label, relation.details.map(::toEntryFact), target)
    }

    private fun <T> AppResult<T>.failure(): AppResult.Failure = this as AppResult.Failure
}

internal fun englishTitleForDisplay(title: String, englishTitle: String?): String? =
    englishTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title.trim(), ignoreCase = true) }

object WikiCatalogueConfiguration {
    private val configured = listOf(
        ConfiguredCategory(id = "farm", title = "农场与物品", types = setOf("object", "crop", "big_craftable", "tool", "ring", "weapon", "footwear", "trinket"), cover = "cover-farm"),
        ConfiguredCategory(id = "villagers", title = "村民", types = setOf("villager"), cover = "cover-world"),
        ConfiguredCategory(id = "people", title = "世界与生物", types = setOf("monster", "fish", "mineral", "ginger_island"), cover = "cover-world"),
        ConfiguredCategory(id = "activities", title = "活动与配方", types = setOf("achievement", "bundle", "quest", "special_order", "cooking_recipe", "crafting_recipe", "tailoring_recipe"), cover = "cover-activities"),
    )

    fun sections(types: List<ManifestEntityType>): List<WikiSection> {
        val available = types.filter { it.count > 0 }.associateBy(ManifestEntityType::id)
        val featured = configured.mapNotNull { it.toWikiCategory(available) }
        val all = available.values
            .filterNot { it.id in SUPPORT_ENTITY_TYPES }
            .sortedBy(ManifestEntityType::displayName)
            .map { type ->
                WikiCategory("type:${type.id}", type.displayName, setOf(type.id), type.count, CategoryCover("type-${type.id}"))
            }
        return listOfNotNull(
            featured.takeIf { it.isNotEmpty() }?.let { WikiSection("featured", "主题图鉴", it) },
            WikiSection("all", "全部分类", all),
        )
    }

    private data class ConfiguredCategory(
        val id: String,
        val title: String,
        val types: Set<String>,
        val cover: String,
    ) {
        fun toWikiCategory(available: Map<String, ManifestEntityType>): WikiCategory? {
            val visibleTypes = types.filterTo(linkedSetOf()) { it in available }
            if (visibleTypes.isEmpty()) return null
            return WikiCategory(id, title, visibleTypes, visibleTypes.sumOf { available.getValue(it).count }, CategoryCover(cover))
        }
    }
}
