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
import com.example.stardewoffline.core.model.ShopKind
import com.example.stardewoffline.core.model.ShopOwner
import com.example.stardewoffline.core.model.ShopPresentation
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
        val referenceValues = detail.facts.flatMap { fact ->
            listOfNotNull(fact.value?.text) + fact.items.mapNotNull { it.value.text }
        }.filter { it.contains(":") && !it.startsWith("类别引用：") && !it.startsWith("未解析") }
        val targetIds = (
            outgoing.map(Schema5Relation::objectEntityId) +
                incoming.map(Schema5Relation::subjectEntityId) +
                referenceValues
            ).distinct()
        val targets = content.summaries(targetIds).getOrNull().orEmpty()
        // 决策 05：本人不可婚配时（如文森特/贾斯、已婚村民），官方 LoveInterest
        // 配对指针没有玩家价值，不进入普通页面；builder 仍保留该边供诊断。
        val subjectRomanceable = detail.facts
            .firstOrNull { it.slotKey == "can_be_romanced" }
            ?.value?.boolean == true
        val relations = detail.relationGroups.flatMap { group ->
            val familyLabel = relationFamilyLabel(group.family)
            val state = relationState(group.status)
            val statusRelation = state?.let {
                EntryRelation(
                    familyLabel,
                    "关系状态",
                    emptyList(),
                    RelationTarget.ReadableText(
                        if (group.family == "love_interest") "角色资料关联（不是当前恋爱状态）" else it,
                    ),
                )
            }
            listOfNotNull(statusRelation) + group.relations.filter {
                it.subjectEntityId == detail.summary.id &&
                    !(it.predicate == "love_interest_pointer" && !subjectRomanceable)
            }.map { relation ->
                val target = targets[relation.objectEntityId]?.let { summary ->
                    RelationTarget.Entry(
                        id = summary.id,
                        title = summary.nameZh,
                        image = imageFor(summary.visual),
                    )
                } ?: RelationTarget.Unavailable("关联内容暂未收录")
                EntryRelation(
                    section = familyLabel,
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
        } + incoming.filter {
            !(it.predicate == "love_interest_pointer" && !subjectRomanceable)
        }.map { relation ->
            val target = targets[relation.subjectEntityId]?.let { summary ->
                RelationTarget.Entry(
                    id = summary.id,
                    title = summary.nameZh,
                    image = imageFor(summary.visual),
                )
            } ?: RelationTarget.Unavailable("关联内容暂未收录")
            EntryRelation(
                section = relation.family?.let { "反向关系·${relationFamilyLabel(it)}" } ?: "反向关系",
                label = relation.label ?: relationLabel(relation.predicate),
                details = listOfNotNull(
                    conditionFact(relation.condition),
                    EntryFact(
                        "方向",
                        "${targets[relation.subjectEntityId]?.nameZh ?: relation.subjectEntityId} → ${detail.summary.nameZh}",
                    ),
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
            sections = typedSections(detail, targets),
            relations = relations,
            submenus = supportSubmenus(detail, targets),
        )
    }

    private fun typedSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        if (detail.summary.entityType == "villager") return villagerSections(detail, targets)
        if (detail.summary.entityType == "crop") return cropSections(detail, targets)
        if (detail.summary.entityType == "fish") return fishSections(detail, targets)
        if (detail.summary.entityType == "monster") return monsterSections(detail, targets)
        if (detail.summary.entityType == "weapon") return weaponSections(detail, targets)
        if (detail.summary.entityType == "shop") return shopSections(detail, targets)
        if (detail.summary.entityType == "tool") return toolSections(detail, targets)
        if (detail.summary.entityType == "big_craftable") return bigCraftableSections(detail, targets)
        if (detail.summary.entityType == "object" || detail.summary.entityType == "mineral") {
            return itemSections(detail, targets)
        }
        if (detail.summary.entityType == "ring") return ringSections(detail, targets)
        if (detail.summary.entityType == "furniture") return furnitureSections(detail, targets)
        if (detail.summary.entityType == "footwear") return footwearSections(detail, targets)
        if (detail.summary.entityType == "cooking_recipe") return cookingSections(detail, targets)
        if (detail.summary.entityType == "quest") return questSections(detail, targets)
        if (detail.summary.entityType == "achievement") return achievementSections(detail)
        if (detail.summary.entityType == "bundle") return bundleSections(detail)
        if (detail.summary.entityType == "special_order") return specialOrderSections(detail)
        if (detail.summary.entityType == "tailoring_recipe") return tailoringSections(detail)
        if (detail.summary.entityType == "drop") return dropSections(detail)
        if (detail.summary.entityType == "ginger_island") return gingerIslandSections(detail)
        return genericSections(detail, targets)
    }

    /**
     * 姜岛事件详情：触发条件（天气/时间窗）。
     */
    private fun gingerIslandSections(detail: Schema5EntityDetail): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["ginger_trigger_condition"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("触发条件", it)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 掉落详情：概率与来源怪物。
     */
    private fun dropSections(detail: Schema5EntityDetail): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["drop_chance"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("掉落概率", it)
        }
        factsBySlot["drop_source"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("掉落来源", it)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 任务详情：类型与目标优先，奖励/可重复紧随其后；讨伐任务目标
     * 与金币奖励已由 builder 投影。
     */
    private fun questSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["quest_type"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("任务类型", it)
        }
        factsBySlot["quest_objective"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("任务目标", it)
        }
        factsBySlot["quest_reward"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("任务奖励", it)
        }
        factsBySlot["quest_repeatable"]?.let { fact ->
            when (fact.value?.boolean) {
                true -> immediate += EntryFact("可重复", "是")
                false -> immediate += EntryFact("可重复", "否")
                null -> Unit
            }
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        val description = detail.summary.descriptionZh
        if (!description.isNullOrBlank()) {
            sections += EntrySection("任务说明", listOf(EntryFact("说明", description)))
        }
        return sections
    }

    /**
     * 成就详情：解锁条件即成就描述；隐藏成就单独标注。
     */
    private fun achievementSections(detail: Schema5EntityDetail): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["achievement_description"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("解锁条件", it)
        }
        factsBySlot["achievement_secret"]?.let { fact ->
            when (fact.value?.boolean) {
                true -> immediate += EntryFact("隐藏成就", "是（未达成前不显示）")
                false -> immediate += EntryFact("隐藏成就", "否")
                null -> Unit
            }
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 收集包详情：所在区域与所需物品。
     */
    private fun bundleSections(
        detail: Schema5EntityDetail,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["bundle_area"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("所在区域", it)
        }
        factsBySlot["bundle_ingredients"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("所需物品", it)
        }
        factsBySlot["bundle_reward"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("奖励", it)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 裁缝配方详情：所需材料与产物。
     */
    private fun tailoringSections(detail: Schema5EntityDetail): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["tailoring_materials"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("所需材料", it)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 特殊订单详情：委托人、时限与目标。
     */
    private fun specialOrderSections(detail: Schema5EntityDetail): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["special_order_requester"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("委托人", it)
        }
        factsBySlot["special_order_duration"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("时限", it)
        }
        factsBySlot["special_order_objective"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("目标", it)
        }
        factsBySlot["special_order_reward"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("奖励", it)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 工具四层详情：立即行动按工具契约排序（用途与档位 → 升级条件/地点/耗时 →
     * 升级效果由官方描述承载），升级链进入可展开资料。
     */
    private fun toolSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["tool_kind"]?.value?.text?.let { immediate += EntryFact("类型", it) }
        factsBySlot["tool_level"]?.value?.text?.let { immediate += EntryFact("档位", it) }
        factsBySlot["upgrade_from_id"]?.let { fact ->
            fact.value?.text?.let { reference ->
                val previous = targets[reference]?.nameZh ?: reference
                immediate += EntryFact("前一级", previous)
            }
        }
        factsBySlot["upgrade_material_id"]?.let { fact ->
            fact.value?.text?.let { reference ->
                val material = targets[reference]?.nameZh ?: reference
                immediate += EntryFact("升级材料", "$material ×5")
            }
        }
        factsBySlot["upgrade_price"]?.value?.integer?.let { immediate += EntryFact("升级价格", "$it 金币") }
        factsBySlot["upgrade_location"]?.value?.text?.let { immediate += EntryFact("升级地点", it) }
        factsBySlot["upgrade_time"]?.value?.text?.let { immediate += EntryFact("升级耗时", it) }
        factsBySlot["sell_price"]?.value?.integer?.let { immediate += EntryFact("出售价格", "$it 金币") }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        return sections
    }

    /**
     * 大型可制作物四层详情：立即行动（主要产物/用途 → 解锁 → 制作材料 →
     * 购买/升级价），材料全集进入可展开资料。
     */
    private fun bigCraftableSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["primary_output"]?.items?.firstOrNull()?.value?.text?.let {
            immediate += EntryFact("主要产物", it)
        }
        factsBySlot["unlock"]?.items?.firstOrNull()?.value?.text?.let {
            immediate += EntryFact("解锁", it)
        }
        factsBySlot["purchase_price"]?.value?.integer?.let {
            immediate += EntryFact("购买价格", "$it 金币")
        }
        factsBySlot["upgrade_price"]?.value?.integer?.let {
            immediate += EntryFact("升级价格", "$it 金币")
        }
        val materials = factsBySlot["crafting_material_id"]?.items.orEmpty()
        if (materials.isNotEmpty()) {
            immediate += EntryFact("制作材料", "共 ${materials.size} 种（详见下方材料清单）")
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)

        return sections
    }

    /**
     * 商店四层详情：立即行动按商店契约排序（地点 → 营业规则 → 店主 →
     * 前几件商品报价），完整商品表进入可展开资料。
     */
    private fun shopSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["shop_kind"]?.items?.firstOrNull()?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("商店类型", it)
        }
        factsBySlot["location"]?.items?.firstOrNull()?.value?.text?.let {
            immediate += EntryFact("地点", it)
        }
        factsBySlot["opening_hours"]?.let { fact ->
            fact.items.firstOrNull()?.value?.text?.let { hours ->
                val note = fact.condition?.playerSummary
                immediate += EntryFact("营业时间", if (note.isNullOrBlank()) hours else "$hours（$note）")
            }
        }
        factsBySlot["owner"]?.items?.firstOrNull()?.value?.text?.let {
            immediate += EntryFact("店主", it)
        }
        val offers = shopOfferRows(detail, targets)
        offers.take(3).forEach { (label, _) -> immediate += EntryFact("商品", label) }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)

        return sections
    }

    /** 商品报价行：名称 + 价格/兑换/规则 + 条件；scope 配对由 builder 保证。 */
    private fun shopOfferRows(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<Pair<String, RelationTarget?>> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val items = factsBySlot["shop_offer_item"]?.items.orEmpty()
        if (items.isEmpty()) return emptyList()
        fun paired(slotKey: String) = factsBySlot[slotKey]?.items.orEmpty().associateBy { it.scopeId }
        val prices = paired("shop_offer_price")
        val currencies = paired("shop_offer_currency")
        val currencyAmounts = paired("shop_offer_currency_amount")
        val exchangeItems = paired("shop_offer_exchange_item_id")
        val exchangeAmounts = paired("shop_offer_exchange_amount")
        val rules = paired("shop_offer_price_rule")
        return items.map { item ->
            val reference = item.value.text.orEmpty()
            val target = targets[reference]?.let { summary ->
                RelationTarget.Entry(summary.id, summary.nameZh, imageFor(summary.visual))
            }
            val name = target?.title ?: reference
            val scope = item.scopeId
            val suffix = when {
                prices[scope]?.value?.integer != null -> "${prices[scope]?.value?.integer} 金币"
                currencies[scope]?.value?.text != null ->
                    listOfNotNull(
                        currencyAmounts[scope]?.value?.integer,
                        currencies[scope]?.value?.text,
                    ).joinToString(" ")
                exchangeItems[scope]?.value?.text != null -> {
                    val exchangeRef = exchangeItems[scope]?.value?.text.orEmpty()
                    val exchangeName = targets[exchangeRef]?.nameZh ?: exchangeRef
                    "以 ${exchangeAmounts[scope]?.value?.integer ?: 1} × $exchangeName 兑换"
                }
                rules[scope]?.value?.text != null -> rules[scope]?.value?.text.orEmpty()
                else -> ""
            }
            val conditionNote = conditionFact(item.condition)?.value
            val label = listOfNotNull(
                name,
                suffix.takeIf(String::isNotEmpty),
                conditionNote?.let { "（$it）" },
            ).joinToString(" ")
            label to target
        }
    }

    /**
     * 作物四层详情：立即行动按作物契约排序（季节与成熟 → 种子来源和成本 →
     * 收获/再生/关键要求 → 出售价格），用途进入可展开资料。
     */
    private fun cropSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["seasons"]?.let { fact ->
            fact.value?.text?.takeIf(String::isNotBlank)?.let { immediate += EntryFact("季节", it) }
        }
        factsBySlot["first_harvest_days"]?.let { fact ->
            val days = fact.value?.integer
            if (days != null) {
                val regrow = factsBySlot["regrow_days"]?.value?.integer
                val growth = if (regrow != null && regrow > 0) {
                    "首次收获 $days 天，之后每 $regrow 天可再收"
                } else {
                    "首次收获 $days 天"
                }
                immediate += EntryFact("成熟", growth)
            }
        }
        factsBySlot["seed_item_id"]?.let { fact ->
            val seedName = fact.value?.text?.let { targets[it]?.nameZh ?: it } ?: "未知"
            val priceText = when {
                factsBySlot["seed_purchase_price"] == null -> ""
                factsBySlot["seed_purchase_price"]?.status == Schema5FactStatus.DYNAMIC_RULE -> "，价格见商店报价（受游戏规则影响）"
                factsBySlot["seed_purchase_price"]?.status == Schema5FactStatus.NOT_COLLECTED -> "，购买价暂未收录"
                factsBySlot["seed_purchase_price"]?.status == Schema5FactStatus.NOT_APPLICABLE -> ""
                else -> factsBySlot["seed_purchase_price"]?.value?.integer?.let { "，${it} 金币" } ?: ""
            }
            immediate += EntryFact("种子", seedName + priceText)
        }
        factsBySlot["harvest_item_id"]?.let { fact ->
            val harvest = fact.value?.text?.let { targets[it]?.nameZh ?: it }
            if (!harvest.isNullOrBlank()) immediate += EntryFact("收获物", harvest)
        }
        factsBySlot["needs_watering"]?.let { fact ->
            if (fact.value?.boolean == false) immediate += EntryFact("关键要求", "不需要每天浇水")
        }
        factsBySlot["sell_price"]?.let { fact ->
            fact.value?.integer?.let { immediate += EntryFact("出售价格", "$it 金币") }
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)

        return sections
    }

    /**
     * 人物四层详情：立即行动按人物契约排序（常住地/日程规则 → 生日/最爱礼物 →
     * 婚配资格），完整日程与五档礼物进入可展开资料，来源只保留玩家文案。
     */
    private fun villagerSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["residence_region"]?.let { fact ->
            immediate += EntryFact("常住地", fact.value?.display()?.takeIf(String::isNotBlank) ?: "未知")
        }
        factsBySlot["schedule"]?.let { fact ->
            if (fact.items.isNotEmpty()) {
                immediate += EntryFact("日程", "按星期、季节与天气变化（已收录 ${fact.items.size} 条）")
            }
        }
        factsBySlot["birthday"]?.let { fact ->
            immediate += EntryFact("生日", fact.value?.display()?.takeIf(String::isNotBlank) ?: "未知")
        }
        factsBySlot["gift_preferences"]?.let { fact ->
            val loved = fact.items
                .filter { "loved" in (it.scopeId.orEmpty()) }
                .mapNotNull { item -> item.value.text }
                .map { reference -> targets[reference]?.nameZh ?: reference }
            if (loved.isNotEmpty()) immediate += EntryFact("最爱礼物", loved.joinToString("、"))
        }
        factsBySlot["can_be_romanced"]?.let { fact ->
            val text = when (fact.value?.boolean) {
                true -> "可以结婚"
                false -> "不可结婚"
                null -> "未知"
            }
            immediate += EntryFact("婚配资格", text)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        val extra = mutableListOf<EntryFact>()
        factsBySlot["gender"]?.let { fact ->
            fact.value?.text?.takeIf(String::isNotBlank)?.let { extra += EntryFact("性别", it) }
        }
        if (extra.isNotEmpty()) sections += EntrySection("更多资料", extra)
        detail.aliases.takeIf { it.isNotEmpty() }?.let {
            sections += EntrySection("别名", listOf(EntryFact("别名", it.joinToString("、"))))
        }

        return sections
    }

    /**
     * 鱼类四层详情：立即行动回答在哪里/何时能钓到（地点 → 季节/时间/天气 → 难度），
     * 行为与尺寸进入更多资料，逐地点条件进入可展开资料。
     */
    private fun fishSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["fishing_locations"]?.let { fact ->
            val locations = fact.items.mapNotNull { it.value.text }.distinct()
            if (locations.isNotEmpty()) immediate += EntryFact("捕捞地点", locations.joinToString("、"))
        }
        factsBySlot["seasons"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("季节", it)
        }
        factsBySlot["fishing_time"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("捕捞时间", it)
        }
        factsBySlot["weather"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("天气", it)
        }
        factsBySlot["difficulty"]?.value?.integer?.let {
            immediate += EntryFact("难度", "$it / 110")
        }
        factsBySlot["fish_pond_outputs"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("鱼塘产出", it)
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        val extra = mutableListOf<EntryFact>()
        factsBySlot["behavior"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            extra += EntryFact("行为", it)
        }
        val minSize = factsBySlot["min_size"]?.value?.integer
        val maxSize = factsBySlot["max_size"]?.value?.integer
        if (minSize != null || maxSize != null) {
            extra += EntryFact("尺寸", "${minSize ?: "?"}–${maxSize ?: "?"} 厘米")
        }
        factsBySlot["sell_price"]?.value?.integer?.let {
            extra += EntryFact("出售价格", "$it 金币")
        }
        if (extra.isNotEmpty()) sections += EntrySection("更多资料", extra)
        factsBySlot["fishing_locations"]?.let { fact ->
            if (fact.items.isNotEmpty()) {
                sections += EntrySection("捕捞地点详情", factRows(fact, targets))
            }
        }

        return sections
    }

    /**
     * 怪物四层详情：立即行动回答在哪遇到/数值与掉落（地点 → 生命/伤害 → 掉落），
     * 掉落概率条件保留在可展开资料中。
     */
    private fun monsterSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["locations"]?.let { fact ->
            val locations = fact.items.mapNotNull { it.value.text }.distinct()
            if (locations.isNotEmpty()) immediate += EntryFact("出现地点", locations.joinToString("、"))
        }
        factsBySlot["floors"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("出现楼层", it)
        }
        factsBySlot["health"]?.value?.integer?.let { immediate += EntryFact("生命值", it.toString()) }
        factsBySlot["damage"]?.value?.integer?.let { immediate += EntryFact("伤害", it.toString()) }
        factsBySlot["drops"]?.let { fact ->
            val names = fact.items
                .mapNotNull { item -> item.value.text?.let { targets[it]?.nameZh ?: it } }
                .distinct()
            if (names.isNotEmpty()) immediate += EntryFact("掉落", names.joinToString("、"))
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        factsBySlot["drops"]?.let { fact ->
            if (fact.items.isNotEmpty()) {
                sections += EntrySection("掉落详情", factRows(fact, targets))
            }
        }

        return sections
    }

    /**
     * 武器四层详情：立即行动回答武器定位（类型 → 伤害区间 → 获得方式），
     * 出售价格进入更多资料。
     */
    private fun weaponSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["weapon_type"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("武器类型", it)
        }
        val minDamage = factsBySlot["damage_min"]?.value?.integer
        val maxDamage = factsBySlot["damage_max"]?.value?.integer
        if (minDamage != null || maxDamage != null) {
            immediate += EntryFact("伤害", "${minDamage ?: "?"}–${maxDamage ?: "?"}")
        }
        factsBySlot["acquisition"]?.let { fact ->
            val methods = fact.items.mapNotNull { it.value.text }.distinct()
            if (methods.isNotEmpty()) immediate += EntryFact("获得方式", methods.joinToString("、"))
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        val extra = mutableListOf<EntryFact>()
        factsBySlot["sell_price"]?.value?.integer?.let { extra += EntryFact("出售价格", "$it 金币") }
        factsBySlot["purchase_price"]?.value?.integer?.let { extra += EntryFact("购买价格", "$it 金币") }
        if (extra.isNotEmpty()) sections += EntrySection("更多资料", extra)

        return sections
    }

    /**
     * 物品与矿物四层详情：立即行动按物品契约排序（出售价格 → 用途 → 加工），
     * 完整用途与加工规则（数量/时间/条件）进入可展开资料。
     */
    private fun itemSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["sell_price"]?.value?.integer?.let { immediate += EntryFact("出售价格", "$it 金币") }
        factsBySlot["edibility"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("食用效果", it)
        }
        factsBySlot["food_buffs"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("增益", it)
        }
        factsBySlot["gift_likers"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("送礼", it)
        }
        factsBySlot["drop_sources"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("怪物掉落", it)
        }
        resolvedNames(factsBySlot["used_in"], targets).takeIf { it.isNotEmpty() }?.let {
            immediate += EntryFact("用途", it.joinToString("、"))
        }
        resolvedNames(factsBySlot["machine_uses"], targets).takeIf { it.isNotEmpty() }?.let {
            immediate += EntryFact("加工", it.joinToString("、"))
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        val extra = purchaseInfoFacts(detail, targets)
        if (extra.isNotEmpty()) sections += EntrySection("更多资料", extra)
        factsBySlot["machine_uses"]?.let { fact ->
            if (fact.items.isNotEmpty()) sections += EntrySection("加工用途详情", machineUseRows(detail, targets))
        }
        factsBySlot["used_in"]?.let { fact ->
            if (fact.items.isNotEmpty()) sections += EntrySection("用途详情", usageRows(detail, targets))
        }

        return sections
    }

    /**
     * 戒指四层详情：立即行动 = 出售价格 + 购买/兑换途径；加工规则对戒指无玩家价值，不展示。
     */
    private fun ringSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["sell_price"]?.value?.integer?.let { immediate += EntryFact("出售价格", "$it 金币") }
        immediate += purchaseInfoFacts(detail, targets)
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)

        return sections
    }

    /** 用途/加工的目标实体中文名（去重）。 */
    private fun resolvedNames(
        fact: Schema5Fact?,
        targets: Map<String, Schema5EntitySummary>,
    ): List<String> = fact?.items.orEmpty()
        .mapNotNull { item -> item.value.text?.let { targets[it]?.nameZh ?: it } }
        .distinct()

    /** 加工规则行：机器名 + 每次数量/耗时 + 输入条件。 */
    private fun machineUseRows(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntryFact> {
        val bySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val uses = bySlot["machine_uses"]?.items.orEmpty()
        val minutes = bySlot["machine_use_minutes"]?.items.orEmpty().associateBy { it.scopeId }
        val counts = bySlot["machine_use_required_count"]?.items.orEmpty().associateBy { it.scopeId }
        return uses.mapNotNull { item ->
            val name = item.value.text?.let { targets[it]?.nameZh ?: it } ?: return@mapNotNull null
            val parts = mutableListOf<String>()
            counts[item.scopeId]?.value?.integer?.let { parts += "每次 $it 个" }
            minutes[item.scopeId]?.value?.integer?.let { parts += "耗时 ${minutesText(it)}" }
            val condition = conditionFact(item.condition)?.value
            val value = listOfNotNull(
                name,
                parts.joinToString("，").takeIf { it.isNotEmpty() },
                condition?.let { "条件：$it" },
            ).joinToString("；")
            EntryFact("加工", value)
        }
    }

    /** 用途行：配方/收集包名 + 使用数量。 */
    private fun usageRows(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntryFact> {
        val bySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val uses = bySlot["used_in"]?.items.orEmpty()
        val quantities = bySlot["used_in_quantity"]?.items.orEmpty().associateBy { it.scopeId }
        return uses.mapNotNull { item ->
            val name = item.value.text?.let { targets[it]?.nameZh ?: it } ?: return@mapNotNull null
            val quantity = quantities[item.scopeId]?.value?.integer
            EntryFact("用途", listOfNotNull(name, quantity?.let { "×$it" }).joinToString(" "))
        }
    }

    /** 购买/兑换途径：固定价、动态规则与兑换成本。 */
    private fun purchaseInfoFacts(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntryFact> {
        val bySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val rows = mutableListOf<EntryFact>()
        bySlot["purchase_price"]?.let { fact ->
            when {
                fact.status == Schema5FactStatus.DYNAMIC_RULE ->
                    rows += EntryFact("购买价格", "动态规则（随商店报价变化）")
                fact.value?.integer != null -> rows += EntryFact("购买价格", "${fact.value.integer} 金币")
                else -> fact.items.firstOrNull()?.value?.integer?.let {
                    rows += EntryFact("购买价格", "$it 金币")
                }
            }
        }
        bySlot["purchase_exchange_item_id"]?.let { fact ->
            val amount = bySlot["purchase_exchange_amount"]?.items.orEmpty()
                .associateBy { it.scopeId }[fact.items.firstOrNull()?.scopeId]?.value?.integer
            val item = fact.items.firstOrNull()?.value?.text
                ?.let { targets[it]?.nameZh ?: it }
            if (!item.isNullOrBlank()) {
                rows += EntryFact("兑换", listOfNotNull(item, amount?.let { "×$it" }).joinToString(" "))
            }
        }
        return rows
    }

    private fun minutesText(minutes: Long): String = when {
        minutes >= 60L && minutes % 60L == 0L -> "${minutes / 60L} 小时"
        minutes > 60L -> "${minutes / 60L} 小时 ${minutes % 60L} 分钟"
        else -> "$minutes 分钟"
    }

    /** 家具四层详情：立即行动 = 购买/目录规则与兑换；用途进入更多资料。 */
    private fun furnitureSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["purchase_price"]?.let { fact ->
            when {
                fact.value?.integer != null -> immediate += EntryFact("购买价格", "${fact.value.integer} 金币")
                fact.items.firstOrNull()?.value?.integer != null ->
                    immediate += EntryFact("购买价格", "${fact.items.first().value.integer} 金币")
                fact.status == Schema5FactStatus.DYNAMIC_RULE -> {
                    val rule = factsBySlot["purchase_price_rule"]?.items?.firstOrNull()?.value?.text
                        ?: "目录报价规则"
                    immediate += EntryFact("购买价格", rule)
                }
            }
        }
        factsBySlot["purchase_exchange_item_id"]?.let { fact ->
            val amount = factsBySlot["purchase_exchange_amount"]?.items.orEmpty()
                .associateBy { it.scopeId }[fact.items.firstOrNull()?.scopeId]?.value?.integer
            val item = fact.items.firstOrNull()?.value?.text?.let { targets[it]?.nameZh ?: it }
            if (!item.isNullOrBlank()) {
                immediate += EntryFact("兑换", listOfNotNull(item, amount?.let { "×$it" }).joinToString(" "))
            }
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        resolvedNames(factsBySlot["used_in"], targets).takeIf { it.isNotEmpty() }?.let {
            sections += EntrySection("更多资料", listOf(EntryFact("用途", it.joinToString("、"))))
        }

        return sections
    }

    /** 鞋类四层详情：立即行动 = 防御/免疫 → 购买/兑换途径。 */
    private fun footwearSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["defense"]?.value?.integer?.let { immediate += EntryFact("防御", it.toString()) }
        factsBySlot["immunity"]?.value?.integer?.let { immediate += EntryFact("免疫", it.toString()) }
        immediate += purchaseInfoFacts(detail, targets)
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)

        return sections
    }

    /** 料理四层详情：立即行动 = 获取方式 + 材料摘要；完整材料清单进入更多资料。 */
    private fun cookingSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val materialRows = recipeMaterialRows(detail, targets)
        val immediate = mutableListOf<EntryFact>()
        factsBySlot["recipe_source"]?.value?.text?.takeIf(String::isNotBlank)?.let {
            immediate += EntryFact("获取方式", it)
        }
        materialRows.take(4).forEach { (name, quantity) ->
            immediate += EntryFact("材料", listOfNotNull(name, quantity?.let { "×$it" }).joinToString(" "))
        }
        val sections = mutableListOf<EntrySection>()
        if (immediate.isNotEmpty()) sections += EntrySection("立即行动", immediate)
        if (materialRows.size > 4) {
            sections += EntrySection("材料清单", materialRows.map { (name, quantity) ->
                EntryFact("材料", listOfNotNull(name, quantity?.let { "×$it" }).joinToString(" "))
            })
        }
        factsBySlot["crafting_output_item_id"]?.value?.text?.let { reference ->
            targets[reference]?.nameZh?.takeIf { it.isNotBlank() }?.let { name ->
                sections += EntrySection("更多资料", listOf(EntryFact("产物", name)))
            }
        }

        return sections
    }

    /** 配方材料行：名称 + 数量（类别材料已是中文文案）。 */
    private fun recipeMaterialRows(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<Pair<String, Long?>> {
        val factsBySlot = detail.facts.associateBy(Schema5Fact::slotKey)
        val materials = factsBySlot["crafting_material_id"]?.items.orEmpty()
        val quantities = factsBySlot["crafting_material_quantity"]?.items.orEmpty()
            .associateBy { it.scopeId }
        return materials.mapNotNull { item ->
            val raw = item.value.text ?: return@mapNotNull null
            val name = targets[raw]?.nameZh ?: raw
            name to quantities[item.scopeId]?.value?.integer
        }
    }

    private fun genericSections(
        detail: Schema5EntityDetail,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntrySection> {
        val cardFacts = listOfNotNull(
            detail.summary.card.actionSummary1?.let { EntryFact("行动摘要", it) },
            detail.summary.card.actionSummary2?.let { EntryFact("行动摘要", it) },
        )
        val facts = cardFacts + detail.facts.flatMap { fact -> factRows(fact, targets) }
        val aliases = detail.aliases.takeIf { it.isNotEmpty() }?.let {
            EntrySection("别名", listOf(EntryFact("别名", it.joinToString("、"))))
        }
        return listOfNotNull(
            facts.takeIf { it.isNotEmpty() }?.let { EntrySection("核心信息", it) },
            aliases,
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
                                val details = scheduleDetails(item.value.text.orEmpty())
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
                                WikiEntrySubmenuItem(target?.title ?: value, target = target)
                            },
                        )
                    }
                WikiEntrySubmenu(
                    title = "礼物偏好",
                    summary = "已收录 ${fact.items.size} 条礼物参考",
                    groups = groups,
                )
            }
        val uses = detail.facts.firstOrNull { it.slotKey == "used_in" }
            ?.takeIf { it.items.isNotEmpty() }
            ?.let { fact ->
                val quantities = detail.facts.firstOrNull { it.slotKey == "used_in_quantity" }
                    ?.items.orEmpty().associateBy { it.scopeId }
                WikiEntrySubmenu(
                    title = "用途",
                    summary = "已收录 ${fact.items.size} 处用途",
                    groups = listOf(
                        WikiEntrySubmenuGroup(
                            title = "用途",
                            items = fact.items.map { item ->
                                val value = item.value.text.orEmpty()
                                val target = targets[value]?.let { summary ->
                                    RelationTarget.Entry(summary.id, summary.nameZh, imageFor(summary.visual))
                                }
                                val quantity = quantities[item.scopeId]?.value?.integer
                                val label = listOfNotNull(target?.title ?: value, quantity?.let { "×$it" })
                                    .joinToString(" ")
                                WikiEntrySubmenuItem(label, target = target)
                            },
                        ),
                    ),
                )
            }
        val shopOffers = if (detail.summary.entityType == "shop") {
            shopOfferRows(detail, targets).takeIf { it.isNotEmpty() }?.let { rows ->
                WikiEntrySubmenu(
                    title = "商品",
                    summary = "已收录 ${rows.size} 件商品报价",
                    groups = listOf(
                        WikiEntrySubmenuGroup(
                            title = "商品报价",
                            items = rows.map { (label, target) ->
                                WikiEntrySubmenuItem(label, target = target)
                            },
                        ),
                    ),
                )
            }
        } else {
            null
        }
        val craftMaterials = if (detail.summary.entityType == "big_craftable") {
            detail.facts.firstOrNull { it.slotKey == "crafting_material_id" }
                ?.takeIf { it.items.isNotEmpty() }
                ?.let { fact ->
                    val quantities = detail.facts
                        .firstOrNull { it.slotKey == "crafting_material_quantity" }
                        ?.items.orEmpty().associateBy { it.scopeId }
                    WikiEntrySubmenu(
                        title = "材料清单",
                        summary = "制作需要 ${fact.items.size} 种材料",
                        groups = listOf(
                            WikiEntrySubmenuGroup(
                                title = "材料",
                                items = fact.items.map { item ->
                                    val value = item.value.text.orEmpty()
                                    val target = targets[value]?.let { summary ->
                                        RelationTarget.Entry(summary.id, summary.nameZh, imageFor(summary.visual))
                                    }
                                    val quantity = quantities[item.scopeId]?.value?.integer
                                    val label = listOfNotNull(target?.title ?: value, quantity?.let { "×$it" })
                                        .joinToString(" ")
                                    WikiEntrySubmenuItem(label, target = target)
                                },
                            ),
                        ),
                    )
                }
        } else {
            null
        }
        return listOfNotNull(schedule, gifts, uses, shopOffers, craftMaterials)
    }

    /** 解析 builder 本地化后的日程文本：「8:00 山姆家」或规则「与周三日程相同」。 */
    private fun scheduleDetails(text: String): List<EntryFact> =
        text.split('；').mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val separator = trimmed.indexOf(' ')
            if (separator > 0 && TIME_PATTERN.matches(trimmed.substring(0, separator))) {
                EntryFact("时间", trimmed)
            } else {
                EntryFact("规则", trimmed)
            }
        }

    private fun factRows(
        fact: Schema5Fact,
        targets: Map<String, Schema5EntitySummary>,
    ): List<EntryFact> {
        val label = factLabel(fact.slotKey) ?: return emptyList()
        val state = when (fact.status) {
            Schema5FactStatus.FIXED -> null
            Schema5FactStatus.CONDITIONAL -> if (fact.condition == null) "条件未完整记录" else null
            Schema5FactStatus.DYNAMIC_RULE -> if (fact.condition == null) "动态规则（条件未完整记录）" else "动态规则"
            Schema5FactStatus.UNKNOWN -> "未知"
            Schema5FactStatus.NOT_COLLECTED -> "暂未收录"
            Schema5FactStatus.NOT_APPLICABLE -> "不适用"
        }
        val condition = conditionFact(fact.condition)?.value
        val prefix = state ?: fact.value?.display()?.let { display -> targets[display]?.nameZh ?: display } ?: "暂未提供"
        val value = listOfNotNull(
            prefix,
            condition?.let { "条件：$it" },
        ).joinToString("；")
        val rows = mutableListOf(EntryFact(label, value))
        fact.items.forEach { item ->
            rows += EntryFact(
                label,
                listOfNotNull(
                    item.value.display().let { display -> targets[display]?.nameZh ?: display },
                    conditionFact(item.condition)?.value?.let { "条件：$it" },
                ).joinToString("；"),
            )
        }
        return rows
    }

    private fun factLabel(slotKey: String): String? = when (slotKey) {
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
        "floors" -> "出现楼层"
        "shop_kind" -> "商店类型"
        "quest_type" -> "任务类型"
        "quest_objective" -> "任务目标"
        "quest_reward" -> "任务奖励"
        "quest_repeatable" -> "可重复"
        "achievement_description" -> "解锁条件"
        "achievement_secret" -> "隐藏成就"
        "bundle_area" -> "所在区域"
        "bundle_ingredients" -> "所需物品"
        "bundle_reward" -> "奖励"
        "edibility" -> "食用效果"
        "food_buffs" -> "增益"
        "gift_likers" -> "送礼"
        "drop_sources" -> "怪物掉落"
        "special_order_requester" -> "委托人"
        "special_order_duration" -> "时限"
        "special_order_objective" -> "目标"
        "special_order_reward" -> "奖励"
        "fish_pond_outputs" -> "鱼塘产出"
        "tailoring_materials" -> "所需材料"
        "drop_chance" -> "掉落概率"
        "drop_source" -> "掉落来源"
        "recipe_source" -> "获取方式"
        "ginger_trigger_condition" -> "触发条件"
        "drops" -> "掉落"
        "health" -> "生命值"
        "damage" -> "伤害"
        "defense" -> "防御"
        "immunity" -> "免疫"
        "weapon_type" -> "武器类型"
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
        else -> null
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

    private companion object {
        val TIME_PATTERN = Regex("^\\d{1,2}:\\d{2}$")
    }

    private fun relationFamilyLabel(family: String): String = when (family) {
        "kinship" -> "亲属关系"
        "friendship" -> "朋友关系"
        "love_interest" -> "角色资料"
        else -> "关联内容"
    }

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
            englishTitle = if (summary.entityType == "villager") {
                null
            } else {
                englishTitleForDisplay(summary.nameZh, summary.nameEn)
            },
            categoryLabel = label,
            filterCategories = summary.facets.mapNotNull { it.value.text }.toSet(),
            image = imageFor(summary.visual),
            shop = if (summary.entityType == "shop") shopPresentation(summary) else null,
            actionSummary1 = summary.card.actionSummary1,
            actionSummary2 = summary.card.actionSummary2,
        )

    /** 商店列表卡：性质分类 + 店主 + 商品数，无图商店不再用占位图。 */
    private fun shopPresentation(summary: Schema5EntitySummary): ShopPresentation? {
        val kindLabel = summary.facets
            .firstOrNull { it.scopeFamily == "shop_kind" }
            ?.value?.text
        val kind = shopKindOf(kindLabel)
        val ownerName = summary.facets
            .firstOrNull { it.scopeFamily == "shop_owner" }
            ?.value?.text
        return ShopPresentation(
            owner = ownerName?.takeIf(String::isNotBlank)?.let {
                ShopOwner(id = it, title = it, image = EntryImage.Missing)
            },
            offerCount = summary.facets
                .firstOrNull { it.scopeFamily == "shop_offer_count" }
                ?.value?.integer
                ?.toInt()
                ?: 0,
            kind = kind,
        )
    }

    private fun shopKindOf(label: String?): ShopKind = when (label) {
        "节日商店" -> ShopKind.FESTIVAL
        "旅行商人" -> ShopKind.TRAVELING
        "兑换" -> ShopKind.EXCHANGE
        "赌场" -> ShopKind.CASINO
        "书摊" -> ShopKind.BOOKSELLER
        "火山商店" -> ShopKind.VOLCANO
        else -> ShopKind.GENERAL
    }

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
