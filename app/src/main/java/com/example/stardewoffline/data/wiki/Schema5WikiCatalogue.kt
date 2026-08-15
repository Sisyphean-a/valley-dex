package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.datapackage.DataPackageManager
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.model.EntryFact
import com.example.stardewoffline.core.model.EntryRelation
import com.example.stardewoffline.core.model.EntrySection
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.EntryImage.Packaged
import com.example.stardewoffline.core.model.EntryImage.Missing
import com.example.stardewoffline.core.model.RelationTarget
import com.example.stardewoffline.core.model.Schema5Condition
import com.example.stardewoffline.core.model.Schema5EntityDetail
import com.example.stardewoffline.core.model.Schema5EntitySummary
import com.example.stardewoffline.core.model.Schema5Fact
import com.example.stardewoffline.core.model.Schema5FactStatus
import com.example.stardewoffline.core.model.Schema5VisualStatus
import com.example.stardewoffline.core.model.Schema5Relation
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySubmenu
import com.example.stardewoffline.core.model.WikiEntrySubmenuGroup
import com.example.stardewoffline.core.model.WikiEntrySubmenuItem
import com.example.stardewoffline.core.model.WikiEntrySummary
import com.example.stardewoffline.core.model.WikiSearchHit
import com.example.stardewoffline.core.model.WikiSearchPage
import com.example.stardewoffline.core.model.WikiSearchQuery
import com.example.stardewoffline.core.model.WikiSection
import com.example.stardewoffline.data.Schema5ContentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schema-5-only page boundary. It never constructs an EntityDetail or reads a
 * legacy JSON payload; every displayed value comes from typed rows or a card.
 */
@Singleton
class Schema5WikiCatalogue @Inject constructor(
    private val packages: DataPackageManager,
    private val content: Schema5ContentRepository,
) : WikiCatalogue {
    override suspend fun sections(): AppResult<List<WikiSection>> = packages.withActivePackage {
        AppResult.Success(WikiCatalogueConfiguration.sections(it.manifest.content.entityTypes))
    }

    override suspend fun entries(query: CatalogueQuery): AppResult<CataloguePage> = packages.withActivePackage { info ->
        val category = WikiCatalogueConfiguration.sections(info.manifest.content.entityTypes)
            .asSequence()
            .flatMap { it.categories }
            .firstOrNull { it.id == query.categoryId }
            ?: return@withActivePackage AppResult.Failure(AppError.InvalidManifest("未知图鉴分类：${query.categoryId}"))
        val filters = query.entryCategory?.takeIf(String::isNotBlank)?.let {
            mapOf("_any" to setOf(it))
        }.orEmpty()
        val page = content.browse(
            types = category.entityTypes,
            facetFilters = filters,
            keyword = query.keyword,
            cursor = query.cursor,
            pageSize = query.pageSize,
        )
        if (page is AppResult.Failure) return@withActivePackage page
        val browsePage = (page as AppResult.Success).value
        val summaries = browsePage.summaries.values.flatten()
        val mapped = summaries.map(::toSummary)
        AppResult.Success(
            CataloguePage(
                category = category,
                entries = mapped,
                availableEntryCategories = mapped.flatMap { it.filterCategories }.distinct().sorted(),
                nextCursor = browsePage.nextCursor,
            ),
        )
    }

    override suspend fun entry(id: String): AppResult<WikiEntry> = packages.withActivePackage { info ->
        val detail = when (val result = content.detail(id)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return@withActivePackage result
        } ?: return@withActivePackage AppResult.Failure(AppError.DatabaseQueryFailed("当前数据包中未找到此条目"))
        val label = info.manifest.content.entityTypes.firstOrNull { it.id == detail.summary.entityType }?.displayName
            ?: return@withActivePackage AppResult.Failure(AppError.InvalidEntityTypeCatalog("未声明类型：${detail.summary.entityType}"))
        withContext(Dispatchers.Default) { AppResult.Success(toEntry(detail, label)) }
    }

    override suspend fun summaries(ids: List<String>): AppResult<Map<String, WikiEntrySummary>> = packages.withActivePackage { info ->
        val rows = content.summaries(ids)
        if (rows is AppResult.Failure) return@withActivePackage rows
        val labels = info.manifest.content.entityTypes.associate { it.id to it.displayName }
        val mapped = rows.getOrNull().orEmpty().mapValues { (_, summary) ->
            val label = labels[summary.entityType]
                ?: return@mapValues toSummary(summary, summary.card.categoryLabel ?: "资料")
            toSummary(summary, label)
        }
        AppResult.Success(mapped)
    }

    override suspend fun search(query: WikiSearchQuery): AppResult<List<WikiSearchHit>> = when (
        val page = searchPage(query)
    ) {
        is AppResult.Success -> AppResult.Success(page.value.hits)
        is AppResult.Failure -> page
    }

    override suspend fun searchPage(query: WikiSearchQuery): AppResult<WikiSearchPage> =
        packages.withActivePackage { info ->
            val searchableTypes = if (query.entityTypes.isEmpty()) {
                info.manifest.content.entityTypes.map { it.id }.toSet()
            } else {
                query.entityTypes
            } - setOf("npc_schedule", "villager_gift")
            val results = content.searchPage(
                query.text,
                searchableTypes,
                query.cursor,
                query.pageSize,
            )
            if (results is AppResult.Failure) return@withActivePackage results
            val labels = info.manifest.content.entityTypes.associate { it.id to it.displayName }
            val page = (results as AppResult.Success).value
            AppResult.Success(
                WikiSearchPage(
                    hits = page.results.asSequence()
                        .filter { it.summary.entityType !in setOf("npc_schedule", "villager_gift") }
                        .map { result ->
                            WikiSearchHit(
                                entry = toSummary(
                                    result.summary,
                                    labels[result.summary.entityType]
                                        ?: result.summary.card.categoryLabel
                                        ?: "资料",
                                ),
                                entityTypeId = result.summary.entityType,
                                reason = result.reason,
                            )
                        }
                        .toList(),
                    nextCursor = page.nextCursor,
                ),
            )
        }

    private suspend fun toEntry(detail: Schema5EntityDetail, typeLabel: String): WikiEntry {
        val outgoing = detail.relationGroups.flatMap { group -> group.relations }
        val incoming = content.reverseRelations(detail.summary.id).getOrNull().orEmpty()
        val supportTargetIds = detail.facts
            .filter { it.slotKey == "gift_preferences" }
            .flatMap { fact -> fact.items.mapNotNull { it.value.text } }
            .filter { it.contains(":") && !it.startsWith("类别引用：") && !it.startsWith("未解析") }
        val targetIds = (
            outgoing.map(Schema5Relation::objectEntityId) +
                incoming.map(Schema5Relation::subjectEntityId) +
                supportTargetIds
            ).distinct()
        val targets = content.summaries(targetIds).getOrNull().orEmpty()
        val relations = detail.relationGroups.flatMap { group ->
            val state = relationState(group.status)
            val statusRelation = state?.let {
                EntryRelation(
                    group.family,
                    "关系状态",
                    emptyList(),
                    RelationTarget.ReadableText(
                        if (group.family == "love_interest") "角色资料关联（不是当前恋爱状态）" else it,
                    ),
                )
            }
            listOfNotNull(statusRelation) + group.relations.filter {
                it.subjectEntityId == detail.summary.id
            }.map { relation ->
                val target = targets[relation.objectEntityId]?.let { summary ->
                    RelationTarget.Entry(
                        id = summary.id,
                        title = summary.nameZh,
                        image = imageFor(summary.visual),
                    )
                } ?: RelationTarget.Unavailable("关联内容暂未收录")
                EntryRelation(
                    section = group.family,
                    label = relation.label ?: relationLabel(relation.predicate),
                    details = listOfNotNull(
                        conditionFact(relation.condition),
                        relation.predicate.takeIf { it == "love_interest_pointer" }?.let {
                            EntryFact("说明", "角色资料关联，不代表当前恋爱状态")
                        },
                    ),
                    target = target,
                )
            }
        } + incoming.map { relation ->
            val target = targets[relation.subjectEntityId]?.let { summary ->
                RelationTarget.Entry(
                    id = summary.id,
                    title = summary.nameZh,
                    image = imageFor(summary.visual),
                )
            } ?: RelationTarget.Unavailable("关联内容暂未收录")
            EntryRelation(
                section = relation.family?.let { "反向关系·$it" } ?: "反向关系",
                label = relation.label ?: relationLabel(relation.predicate),
                details = listOfNotNull(
                    conditionFact(relation.condition),
                    EntryFact("方向", "${relation.subjectEntityId} → ${detail.summary.id}"),
                ),
                target = target,
            )
        }
        return WikiEntry(
            id = detail.summary.id,
            title = detail.summary.nameZh,
            englishTitle = englishTitleForDisplay(detail.summary.nameZh, detail.summary.nameEn),
            categoryLabel = typeLabel,
            image = imageFor(detail.summary.visual),
            summary = detail.summary.card.identitySummary ?: detail.summary.descriptionZh ?: detail.summary.descriptionEn,
            sections = typedSections(detail, incoming),
            relations = relations,
            submenus = supportSubmenus(detail, targets),
        )
    }

    private fun typedSections(
        detail: Schema5EntityDetail,
        incomingRelations: List<Schema5Relation> = emptyList(),
    ): List<EntrySection> {
        val cardFacts = listOfNotNull(
            detail.summary.card.actionSummary1?.let { EntryFact("行动摘要", it) },
            detail.summary.card.actionSummary2?.let { EntryFact("行动摘要", it) },
        )
        val facts = cardFacts + detail.facts.flatMap(::factRows)
        val aliases = detail.aliases.takeIf { it.isNotEmpty() }?.let {
            EntrySection("别名", listOf(EntryFact("别名", it.joinToString("、"))))
        }
        val sources = (detail.facts.flatMap { it.sources } + detail.relationGroups.flatMap { group ->
            group.relations.flatMap { it.sources }
        } + incomingRelations.flatMap { it.sources }).distinctBy {
            listOf(
                it.kind, it.title, it.gameVersion, it.revision, it.sourceUrl, it.reviewedAt,
                it.evidenceKind, it.transformationRule, it.reviewStatus, it.conflictStatus, it.expiresAt,
            )
        }.takeIf { it.isNotEmpty() }?.let { rows ->
            EntrySection(
                "数据说明",
                rows.map { source ->
                    EntryFact(
                        "来源",
                        sourceDescription(source),
                    )
                },
            )
        }
        return listOfNotNull(
            facts.takeIf { it.isNotEmpty() }?.let { EntrySection("核心信息", it) },
            aliases,
            sources,
        )
    }

    private fun supportSubmenus(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<WikiEntrySubmenu> {
        val schedule = detail.facts.firstOrNull { it.slotKey == "schedule" }
            ?.takeIf { it.items.isNotEmpty() }
            ?.let { fact ->
                WikiEntrySubmenu(
                    title = "日程",
                    summary = "已收录 ${fact.items.size} 条日程记录",
                    groups = listOf(
                        WikiEntrySubmenuGroup(
                            title = "日程记录",
                            items = fact.items.mapIndexed { index, item ->
                                val details = item.value.text.orEmpty().split('；').mapNotNull { part ->
                                    val separator = part.indexOf('：')
                                    if (separator <= 0) null else EntryFact(
                                        part.substring(0, separator),
                                        part.substring(separator + 1),
                                    )
                                }
                                WikiEntrySubmenuItem("记录 ${index + 1}", details)
                            },
                        ),
                    ),
                )
            }
        val gifts = detail.facts.firstOrNull { it.slotKey == "gift_preferences" }
            ?.takeIf { it.items.isNotEmpty() }
            ?.let { fact ->
                val groups = fact.items.groupBy { giftPreferenceLabel(it.scopeId.orEmpty()) }
                    .toSortedMap(preferenceComparator)
                    .map { (preference, items) ->
                        WikiEntrySubmenuGroup(
                            title = preference,
                            items = items.map { item ->
                                val value = item.value.text.orEmpty()
                                val target = targets[value]?.let { summary ->
                                    RelationTarget.Entry(summary.id, summary.nameZh, imageFor(summary.visual))
                                }
                                WikiEntrySubmenuItem(value, target = target)
                            },
                        )
                    }
                WikiEntrySubmenu(
                    title = "礼物偏好",
                    summary = "已收录 ${fact.items.size} 条礼物参考",
                    groups = groups,
                )
            }
        return listOfNotNull(schedule, gifts)
    }

    private fun factRows(fact: Schema5Fact): List<EntryFact> {
        val label = factLabel(fact.slotKey)
        val state = when (fact.status) {
            Schema5FactStatus.FIXED -> null
            Schema5FactStatus.CONDITIONAL -> if (fact.condition == null) "条件未完整记录" else null
            Schema5FactStatus.DYNAMIC_RULE -> if (fact.condition == null) "动态规则（条件未完整记录）" else "动态规则"
            Schema5FactStatus.UNKNOWN -> "未知"
            Schema5FactStatus.NOT_COLLECTED -> "暂未收录"
            Schema5FactStatus.NOT_APPLICABLE -> "不适用"
        }
        val condition = conditionFact(fact.condition)?.value
        val prefix = state ?: fact.value?.display() ?: "暂未提供"
        val sourceNote = fact.sources.takeIf { it.isNotEmpty() }?.joinToString("、", transform = ::sourceDescription)
        val value = listOfNotNull(
            prefix,
            condition?.let { "条件：$it" },
            sourceNote?.let { "来源：$it" },
        ).joinToString("；")
        val rows = mutableListOf(EntryFact(label, value))
        fact.items.forEach { item ->
            rows += EntryFact(
                label,
                listOfNotNull(
                    item.value.display(),
                    item.scopeId?.let {
                        if (fact.slotKey == "gift_preferences") {
                            "偏好：${giftPreferenceLabel(it)}"
                        } else {
                            "范围：$it"
                        }
                    },
                    conditionFact(item.condition)?.value?.let { "条件：$it" },
                    item.sources.takeIf { it.isNotEmpty() }?.joinToString("、", transform = ::sourceDescription)
                        ?.let { "来源：$it" },
                ).joinToString("；"),
            )
        }
        return rows
    }

    private fun factLabel(slotKey: String): String = when (slotKey) {
        "sell_price" -> "出售价格"
        "purchase_price" -> "购买价格"
        "seed_purchase_price" -> "种子购买价格"
        "purchase_currency", "seed_purchase_currency" -> "交易货币"
        "purchase_currency_amount", "seed_purchase_currency_amount" -> "非金币报价"
        "purchase_price_rule", "seed_purchase_price_rule" -> "动态报价规则"
        "purchase_exchange_item_id", "seed_purchase_exchange_item_id" -> "兑换成本物品"
        "purchase_exchange_amount", "seed_purchase_exchange_amount" -> "兑换数量"
        "seasons" -> "季节"
        "first_harvest_days" -> "首次收获天数"
        "regrow_days" -> "再生天数"
        "needs_watering" -> "需要浇水"
        "seed_item_id" -> "种子来源"
        "harvest_item_id" -> "收获物"
        "fishing_locations" -> "捕捞地点"
        "fishing_time" -> "捕捞时间"
        "weather" -> "天气"
        "difficulty" -> "难度"
        "behavior" -> "行为"
        "min_size" -> "最小尺寸"
        "max_size" -> "最大尺寸"
        "residence_region" -> "常住地"
        "birthday" -> "生日"
        "gender" -> "性别"
        "can_be_romanced" -> "婚配资格"
        "schedule" -> "日程"
        "gift_preferences" -> "礼物偏好"
        "locations" -> "出现地点"
        "drops" -> "掉落"
        "acquisition" -> "获得方式"
        "damage_min" -> "最低伤害"
        "damage_max" -> "最高伤害"
        "upgrade_material_id" -> "升级材料"
        "upgrade_price" -> "升级价格"
        "crafting_output_item_id" -> "制作产物"
        "crafting_material_id" -> "制作材料"
        "crafting_material_quantity" -> "材料数量"
        "machine_uses" -> "加工用途"
        "machine_use_required_count" -> "加工数量"
        "machine_use_minutes" -> "加工时间"
        "used_in" -> "用途"
        "used_in_quantity" -> "使用数量"
        "used_in_quality" -> "品质规则"
        else -> slotKey.replace('_', ' ').trim().ifBlank { "信息" }
    }

    private fun giftPreferenceLabel(scopeId: String): String = when {
        scopeId.contains(":loved:") -> "最爱"
        scopeId.contains(":liked:") -> "喜欢"
        scopeId.contains(":neutral:") -> "一般"
        scopeId.contains(":disliked:") -> "不喜欢"
        scopeId.contains(":hated:") -> "讨厌"
        else -> "未分类"
    }

    private val preferenceComparator = compareBy<String> {
        listOf("最爱", "喜欢", "一般", "不喜欢", "讨厌", "未分类")
            .indexOf(it)
            .let { index -> if (index < 0) Int.MAX_VALUE else index }
    }

    private fun sourceDescription(source: com.example.stardewoffline.core.model.Schema5SourceSummary): String =
        listOfNotNull(
            when (source.kind) {
                "official_direct" -> "官方原始数据"
                "official_derived" -> "官方派生数据"
                "supplemental_reviewed" -> "审核补充资料"
                else -> source.kind
            },
            source.title,
            source.gameVersion?.let { "版本 $it" },
            source.revision?.let { "修订 $it" },
            source.evidenceKind?.let { "证据 $it" },
            source.transformationRule?.let { "转换 $it" },
            source.reviewStatus?.takeUnless { it == "not_required" }?.let { "审核 $it" },
            source.conflictStatus?.takeUnless { it == "none" }?.let { "冲突 $it" },
            source.expiresAt?.let { "有效期至 $it" },
        ).joinToString("；")

    private fun relationLabel(predicate: String): String = when (predicate) {
        "kinship" -> "亲属关系"
        "friendship" -> "朋友关系"
        "friendship_unspecified" -> "亲友关联（具体关系未注明）"
        "guardianship" -> "监护关系"
        "cohabitation" -> "同住关系"
        "love_interest_pointer" -> "角色资料关联（不是当前恋爱状态）"
        else -> "关系"
    }

    private fun relationState(status: Schema5FactStatus): String? = when (status) {
        Schema5FactStatus.FIXED -> null
        Schema5FactStatus.CONDITIONAL -> "条件关系"
        Schema5FactStatus.DYNAMIC_RULE -> "动态规则"
        Schema5FactStatus.UNKNOWN -> "未知"
        Schema5FactStatus.NOT_COLLECTED -> "暂未收录"
        Schema5FactStatus.NOT_APPLICABLE -> "不适用"
    }

    private fun conditionFact(condition: Schema5Condition?): EntryFact? = condition?.let {
        EntryFact("适用条件", it.playerSummary ?: it.originalText ?: when (it.completeness) {
            com.example.stardewoffline.core.model.Schema5ConditionCompleteness.COMPLETE -> "条件已完整记录"
            com.example.stardewoffline.core.model.Schema5ConditionCompleteness.PARTIAL -> "另有未识别条件"
            com.example.stardewoffline.core.model.Schema5ConditionCompleteness.OPAQUE -> "受游戏条件限制"
        })
    }

    private fun toSummary(summary: Schema5EntitySummary, label: String = summary.card.categoryLabel ?: "资料") =
        WikiEntrySummary(
            id = summary.id,
            title = summary.nameZh,
            englishTitle = englishTitleForDisplay(summary.nameZh, summary.nameEn),
            categoryLabel = label,
            filterCategories = summary.facets.mapNotNull { it.value.text }.toSet(),
            image = imageFor(summary.visual),
            shop = null,
            actionSummary1 = summary.card.actionSummary1,
            actionSummary2 = summary.card.actionSummary2,
        )

    private fun imageFor(visual: com.example.stardewoffline.core.model.Schema5Visual?): EntryImage = when {
        visual == null || visual.status == Schema5VisualStatus.OFFICIAL_NONE -> Missing
        visual.status == Schema5VisualStatus.PROXY ->
            if (!visual.relativePath.isNullOrBlank()) Packaged(visual.relativePath) else EntryImage.Proxy
        visual.status == Schema5VisualStatus.PENDING_REVIEW || visual.status == Schema5VisualStatus.PACKAGE_ERROR ->
            EntryImage.PackageError
        visual.status in setOf(Schema5VisualStatus.OFFICIAL_OWN, Schema5VisualStatus.OFFICIAL_REUSE) &&
            !visual.relativePath.isNullOrBlank() -> Packaged(visual.relativePath)
        else -> EntryImage.PackageError
    }

}
