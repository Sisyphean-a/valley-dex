package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.DefaultDispatcher
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.datapackage.DataPackageManager
import com.example.stardewoffline.core.json.DetailPresentationParser
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.model.CategoryCover
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.EntityDetail
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val SUPPORT_ENTITY_TYPES = setOf("npc_schedule", "villager_gift")
private val SELL_PRICE_FACT_LABELS = setOf("售价", "出售价格", "基础售价", "基础价格")

interface WikiCatalogue {
    suspend fun sections(): AppResult<List<WikiSection>>
    suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage>
    suspend fun entry(id: String): AppResult<WikiEntry>
    suspend fun summaries(ids: List<String>): AppResult<Map<String, WikiEntrySummary>>
    suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>>
}

@Singleton
class DefaultWikiCatalogue @Inject constructor(
    private val packages: DataPackageManager,
    private val content: ContentRepository,
    private val relations: EntityRelationResolver,
    private val search: SearchRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WikiCatalogue {
    private val categoryCacheMutex = Mutex()
    private var cachedPackage: PackageCacheKey? = null
    private val categoryEntries = mutableMapOf<String, List<WikiEntrySummary>>()

    override suspend fun sections(): AppResult<List<WikiSection>> = when (val active = activePackage()) {
        is AppResult.Success -> AppResult.Success(WikiCatalogueConfiguration.sections(active.value.types))
        is AppResult.Failure -> active
    }

    override suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage> = packages.withActivePackage { info ->
        val active = ActivePackage(info.id, info.manifest.content.entityTypes)
        val category = WikiCatalogueConfiguration.sections(active.types).asSequence()
            .flatMap { it.categories }
            .firstOrNull { it.id == query.categoryId }
            ?: return@withActivePackage AppResult.Failure(AppError.InvalidManifest("未知图鉴分类：${query.categoryId}"))
        val labels = active.types.associate { it.id to it.displayName }
        val entries = cachedEntries(PackageCacheKey(info), category, labels)
        val summaries = entries.getOrNull() ?: return@withActivePackage entries.failure()
        withContext(defaultDispatcher) {
            AppResult.Success(
                CataloguePage(
                    category = category,
                    entries = filterEntries(summaries, query.keyword, query.entryCategory),
                    availableEntryCategories = summaries.mapNotNull(WikiEntrySummary::filterCategory).distinct().sorted(),
                ),
            )
        }
    }

    override suspend fun entry(id: String): AppResult<WikiEntry> = packages.withActivePackage { info ->
        val entity = content.detail(id).getOrNull()
            ?: return@withActivePackage AppResult.Failure(AppError.DatabaseQueryFailed("当前数据包中未找到此条目"))
        if (entity.nameZh.isBlank() || entity.translationStatus == com.example.stardewoffline.core.model.TranslationStatus.MISSING) {
            return@withActivePackage AppResult.Failure(AppError.DatabaseCorrupted("条目缺少可读中文名"))
        }
        val typeLabel = info.manifest.content.entityTypes.firstOrNull { it.id == entity.entityType }?.displayName
            ?: return@withActivePackage AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：${entity.entityType}"))
        withContext(defaultDispatcher) { buildEntry(id, entity, typeLabel) }
    }

    override suspend fun summaries(ids: List<String>): AppResult<Map<String, WikiEntrySummary>> = packages.withActivePackage { info ->
        val labels = info.manifest.content.entityTypes.associate { it.id to it.displayName }
        val summaries = content.summaries(ids)
        val values = summaries.getOrNull() ?: return@withActivePackage summaries.failure()
        withContext<AppResult<Map<String, WikiEntrySummary>>>(defaultDispatcher) {
            val mapped = linkedMapOf<String, WikiEntrySummary>()
            for ((id, summary) in values) {
                val label = labels[summary.entityType]
                    ?: return@withContext AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：${summary.entityType}"))
                mapped[id] = toWikiSummary(summary, label)
            }
            AppResult.Success(mapped)
        }
    }

    override suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>> = packages.withActivePackage { info ->
        val labels = info.manifest.content.entityTypes.associate { it.id to it.displayName }
        when (val result = search.search(query.text, query.entityTypes)) {
            is AppResult.Success -> withContext(defaultDispatcher) { mapSearchHits(result.value, labels) }
            is AppResult.Failure -> result
        }
    }

    /**
     * Flow: keeps only one package's lightweight category summaries in memory.
     * Effect: typing and display-mode changes reuse the same immutable source list.
     */
    private suspend fun cachedEntries(
        cacheKey: PackageCacheKey,
        category: WikiCategory,
        typeLabels: Map<String, String>,
    ): AppResult<List<WikiEntrySummary>> = categoryCacheMutex.withLock {
        if (cachedPackage != cacheKey) {
            cachedPackage = cacheKey
            categoryEntries.clear()
        }
        categoryEntries[category.id]?.let { return@withLock AppResult.Success(it) }
        when (val loaded = loadSummaries(category.entityTypes, typeLabels)) {
            is AppResult.Success -> {
                categoryEntries[category.id] = loaded.value
                loaded
            }
            is AppResult.Failure -> loaded
        }
    }

    private suspend fun activePackage(): AppResult<ActivePackage> {
        val info = packages.openActive().getOrNull() ?: return AppResult.Failure(AppError.NoDataPackage)
        return AppResult.Success(ActivePackage(info.id, info.manifest.content.entityTypes))
    }

    private suspend fun buildEntry(
        id: String,
        entity: com.example.stardewoffline.core.model.EntityDetail,
        typeLabel: String,
    ): AppResult<WikiEntry> {
        val presentation = DetailPresentationParser.present(entity)
        val relationDefinitions = presentation.relationGroups.flatMap { it.relations }
        val targets = if (entity.entityType == "shop") emptyMap() else relations.resolve(relationDefinitions)
        val targetDetails = if (entity.entityType == "shop") relations.resolveDetails(relationDefinitions) else emptyMap()
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
                relations = presentation.relationGroups.flatMap { group ->
                    group.relations.mapNotNull { toEntryRelation(group.title, it, targets, targetDetails) }
                },
                submenus = submenus,
            ),
        )
    }

    private suspend fun loadSummaries(
        types: Set<String>,
        typeLabels: Map<String, String>,
    ): AppResult<List<WikiEntrySummary>> {
        val summaries = content.summaries(types)
        val grouped = summaries.getOrNull() ?: return summaries.failure()
        val entries = mutableListOf<WikiEntrySummary>()
        for (type in types) {
            val label = typeLabels[type] ?: return AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：$type"))
            entries += grouped[type].orEmpty().map { toWikiSummary(it, label) }
        }
        return AppResult.Success(entries)
    }

    private fun mapSearchHits(
        results: List<com.example.stardewoffline.core.model.SearchResult>,
        labels: Map<String, String>,
    ): AppResult<List<WikiSearchHit>> {
        val hits = mutableListOf<WikiSearchHit>()
        for (result in results) {
            if (result.summary.entityType in SUPPORT_ENTITY_TYPES) continue
            val label = labels[result.summary.entityType]
                ?: return AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：${result.summary.entityType}"))
            hits += WikiSearchHit(toWikiSummary(result.summary, label), result.summary.entityType, result.reason)
        }
        return AppResult.Success(hits)
    }

    private fun filterEntries(entries: List<WikiEntrySummary>, keyword: String?, entryCategory: String?): List<WikiEntrySummary> {
        val term = keyword?.trim()?.takeIf(String::isNotEmpty)
        return entries.filter { entry ->
            (term == null || entry.title.contains(term, ignoreCase = true) || entry.englishTitle?.contains(term, ignoreCase = true) == true) &&
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
        targetDetails: Map<String, EntityDetail>,
    ): EntryRelation? {
        val resolvedTarget = targetDetails[relation.targetId]?.let(::entryTarget)
            ?: targets[relation.targetId]?.let { summary ->
                RelationTarget.Entry(
                    id = summary.id,
                    title = summary.nameZh,
                    image = summary.imagePath?.let(EntryImage::Packaged) ?: EntryImage.Missing,
                )
            }
        // Rule: a shop card is useful only when its product resolves to a published entry.
        // Failure: do not turn a purchase price into a fake product name or expose an empty offer.
        if (section == "商品" && resolvedTarget == null) return null
        val target = resolvedTarget
            ?: relation.details.firstOrNull()?.value?.takeIf(String::isNotBlank)?.let(RelationTarget::ReadableText)
            ?: RelationTarget.Unavailable("关联内容暂未收录")
        return EntryRelation(section, relation.label, relation.details.map(::toEntryFact), target)
    }

    private fun entryTarget(entity: EntityDetail): RelationTarget.Entry = RelationTarget.Entry(
        id = entity.id,
        title = entity.nameZh,
        image = entity.imagePath?.let(EntryImage::Packaged) ?: EntryImage.Missing,
        sellPrice = DetailPresentationParser.present(entity).facts
            .firstOrNull { it.label in SELL_PRICE_FACT_LABELS }
            ?.value,
    )

    private fun <T> AppResult<T>.failure(): AppResult.Failure = this as AppResult.Failure
    private data class ActivePackage(val id: String, val types: List<ManifestEntityType>)
    private data class PackageCacheKey(
        val id: String,
        val generatedAt: String,
        val entityTypes: List<ManifestEntityType>,
    ) {
        constructor(info: com.example.stardewoffline.core.model.DataPackageInfo) : this(
            info.id,
            info.manifest.generatedAt,
            info.manifest.content.entityTypes,
        )
    }
}

internal fun englishTitleForDisplay(title: String, englishTitle: String?): String? =
    englishTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title.trim(), ignoreCase = true) }

object WikiCatalogueConfiguration {
    private val groups = listOf(
        ConfiguredGroup(
            id = "farm",
            title = "农场经营",
            types = listOf("object", "crop", "big_craftable", "tool", "furniture"),
            cover = "cover-farm",
        ),
        ConfiguredGroup(
            id = "community",
            title = "人物与社区",
            types = listOf("villager", "shop"),
            cover = "cover-community",
        ),
        ConfiguredGroup(
            id = "exploration",
            title = "探索与战斗",
            types = listOf("monster", "fish", "mineral", "drop", "weapon", "footwear", "ring", "trinket", "ginger_island"),
            cover = "cover-world",
        ),
        ConfiguredGroup(
            id = "missions",
            title = "任务与收集",
            types = listOf("achievement", "bundle", "quest", "special_order"),
            cover = "cover-missions",
        ),
        ConfiguredGroup(
            id = "crafting",
            title = "料理与制作",
            types = listOf("cooking_recipe", "crafting_recipe", "tailoring_recipe"),
            cover = "cover-activities",
        ),
    )

    /**
     * Guarantee: every browsable manifest type appears in exactly one catalogue section;
     * unknown future types remain reachable under “其他资料”.
     */
    fun sections(types: List<ManifestEntityType>): List<WikiSection> {
        val available = types.filter { it.count > 0 && it.id !in SUPPORT_ENTITY_TYPES }.associateBy(ManifestEntityType::id)
        val groupedTypeIds = groups.flatMap(ConfiguredGroup::types).toSet()
        val majorCategories = groups.mapNotNull { it.toMajorCategory(available) }
        val catalogueSections = groups.mapNotNull { it.toCatalogueSection(available) }.toMutableList()
        val remaining = available.values
            .filterNot { it.id in groupedTypeIds }
            .sortedBy(ManifestEntityType::displayName)
            .map(::toTypeCategory)
        if (remaining.isNotEmpty()) catalogueSections += WikiSection("catalogue-other", "其他资料", remaining)
        return buildList {
            if (majorCategories.isNotEmpty()) add(WikiSection("major", "大类导航", majorCategories))
            addAll(catalogueSections)
        }
    }

    private fun toTypeCategory(type: ManifestEntityType) = WikiCategory(
        id = "type:${type.id}",
        title = type.displayName,
        entityTypes = setOf(type.id),
        entryCount = type.count,
        cover = CategoryCover("type-${type.id}"),
    )

    private data class ConfiguredGroup(
        val id: String,
        val title: String,
        val types: List<String>,
        val cover: String,
    ) {
        fun toMajorCategory(available: Map<String, ManifestEntityType>): WikiCategory? {
            val visibleTypes = types.filterTo(linkedSetOf()) { it in available }
            if (visibleTypes.isEmpty()) return null
            return WikiCategory(id, title, visibleTypes, visibleTypes.sumOf { available.getValue(it).count }, CategoryCover(cover))
        }

        fun toCatalogueSection(available: Map<String, ManifestEntityType>): WikiSection? {
            val categories = types.mapNotNull(available::get).map(::toTypeCategory)
            return categories.takeIf(List<WikiCategory>::isNotEmpty)?.let { WikiSection("catalogue-$id", title, it) }
        }
    }
}
