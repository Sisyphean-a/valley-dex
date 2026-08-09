package com.example.stardewoffline.core.json

import com.example.stardewoffline.core.formatter.DetailFormatters
import com.example.stardewoffline.core.model.DetailFact
import com.example.stardewoffline.core.model.DetailPresentation
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.DetailRelationGroup
import com.example.stardewoffline.core.model.EntityDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 把数据包中的稳定派生字段，以及当前版本中语义明确的官方字段，整理成阅读模型。
 * 未识别的未来字段仍然保留在数据库中，但不会被猜测成用户可见结论。
 */
object DetailPresentationParser {
    fun present(entity: EntityDetail): DetailPresentation {
        val raw = entity.extraJson
        val derived = raw.objectAt("officialDerived")
        val sourceId = entity.id.substringAfter(':', entity.id)
        val facts = factsFor(entity.entityType, raw, derived, sourceId)
        val groups = groupsFor(entity.entityType, raw, derived)
        return DetailPresentation(facts, groups.filter { it.relations.isNotEmpty() })
    }

    private fun factsFor(type: String, raw: JsonObject, derived: JsonObject, sourceId: String): List<DetailFact> = when (type) {
        "object", "mineral", "ring" -> itemFacts(raw, derived)
        "big_craftable" -> bigCraftableFacts(raw)
        "weapon" -> weaponFacts(raw)
        "tool" -> toolFacts(raw)
        "footwear" -> footwearFacts(raw)
        "trinket" -> trinketFacts(raw)
        "furniture" -> furnitureFacts(raw)
        "crop" -> cropFacts(raw, derived)
        "fish" -> fishFacts(derived)
        "villager" -> villagerFacts(raw, derived)
        "monster" -> monsterFacts(raw)
        "drop" -> dropFacts(raw)
        "achievement" -> achievementFacts(raw)
        "shop" -> shopFacts(raw)
        "quest" -> questFacts(raw)
        "special_order" -> specialOrderFacts(raw)
        "bundle" -> bundleFacts(raw)
        "tailoring_recipe" -> tailoringFacts(raw)
        "npc_schedule" -> scheduleFacts(raw)
        "ginger_island" -> islandEventFacts(raw, sourceId)
        "cooking_recipe", "crafting_recipe" -> recipeFacts(raw, derived)
        else -> genericFacts(raw, derived)
    }

    private fun groupsFor(type: String, raw: JsonObject, derived: JsonObject): List<DetailRelationGroup> = buildList {
        when (type) {
            "crop" -> add(cropRelations(derived))
            "fish" -> add(fishRelations(derived))
            "villager" -> add(villagerRelations(derived))
            "monster" -> add(monsterRelations(raw))
            "drop" -> add(dropRelations(raw))
            "shop" -> add(shopEntityRelations(raw))
            "villager_gift" -> add(villagerGiftRelations(raw))
            "bundle" -> add(bundleRelations(raw))
            "quest" -> add(questRelations(raw))
            "special_order" -> add(specialOrderRelations(raw))
            "tailoring_recipe" -> add(tailoringRelations(raw))
            "npc_schedule" -> add(scheduleRelations(raw))
        }
        if (type in RECIPE_TYPES) add(recipeRelations(raw, derived))
        if (type != "shop") add(shopRelations(derived))
        add(machineRelations(derived))
        add(usedInRelations(derived))
    }

    private fun itemFacts(raw: JsonObject, derived: JsonObject): List<DetailFact> = listOfNotNull(
        preferFact(derived, raw, "sellPrice", "售价", DetailFormatters::gold, rawKey = "Price"),
        preferFact(derived, raw, "edibility", "食用", DetailFormatters::edibility, rawKey = "Edibility"),
        raw.fact("Category", "物品分类") { DetailFormatters.category(it.contentOrNull ?: "") },
        raw.fact("Type", "物品类型") { objectType(it.contentOrNull ?: "") },
        raw.fact("IsDrink", "饮品") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CanBeGivenAsGift", "可作为礼物") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CanBeTrashed", "可丢弃") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("ColorOverlayFromNextIndex", "使用后续索引颜色") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("ExcludeFromFishingCollection", "不计入钓鱼图鉴") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("ExcludeFromShippingCollection", "不计入出货图鉴") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("ExcludeFromRandomSale", "不参与随机出售") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("GeodeDropsDefaultItems", "使用默认晶球掉落") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.array("GeodeDrops").takeIf { it.isNotEmpty() }?.let { DetailFact("晶球掉落", "${it.size} 项") },
        raw.array("ArtifactSpotChances").takeIf { it.isNotEmpty() }?.let { DetailFact("文物点概率", "${it.size} 项") },
        contextTagFact(derived, raw),
    )

    private fun bigCraftableFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        raw.fact("Price", "基础售价") { DetailFormatters.gold(it.contentOrNull ?: "") },
        raw.fact("Fragility", "耐久规则") { fragility(it.contentOrNull ?: "") },
        raw.fact("CanBePlacedOutdoors", "可放在室外") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CanBePlacedIndoors", "可放在室内") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("IsLamp", "属于灯具") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("ContextTags", "用途标签") { DetailFormatters.contextTags(listOf(it.contentOrNull.orEmpty())) },
    )

    private fun weaponFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        rangeFact(raw, "MinDamage", "MaxDamage", "伤害"),
        raw.fact("Type", "武器类型") { DetailFormatters.weaponType(it.contentOrNull ?: "") },
        raw.fact("Speed", "速度修正") { DetailFormatters.weaponSpeed(it.contentOrNull ?: "", raw.string("Type")) },
        raw.fact("Knockback", "击退力") { DetailFormatters.formatNumber(it.contentOrNull ?: "") },
        signedFact(raw, "Precision", "精准度加成"),
        signedFact(raw, "Defense", "防御加成"),
        signedFact(raw, "AreaOfEffect", "攻击范围加成"),
        raw.fact("CritChance", "暴击率") { DetailFormatters.percentage(it.contentOrNull ?: "") },
        raw.fact("CritMultiplier", "暴击倍率") { it.contentOrNull?.let { value -> DetailFormatters.formatNumber(value)?.let { formatted -> "×$formatted" } } },
        raw.fact("MineBaseLevel", "矿井基础层") { mineLevel(it.contentOrNull ?: "") },
        raw.fact("MineMinLevel", "矿井最低层") { mineLevel(it.contentOrNull ?: "") },
        raw.fact("CanBeLostOnDeath", "死亡时可能丢失") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.array("Projectiles").takeIf { it.isNotEmpty() }?.let { DetailFact("投射物", "${it.size} 种") },
    )

    private fun toolFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        raw.fact("ClassName", "工具类型") { DetailFormatters.toolClass(it.contentOrNull ?: "") },
        raw.fact("UpgradeLevel", "升级等级") { upgradeLevel(it.contentOrNull ?: "") },
        raw.fact("SalePrice", "出售价格") { DetailFormatters.gold(it.contentOrNull ?: "") },
        raw.fact("AttachmentSlots", "附件槽位") { attachmentSlots(it.contentOrNull ?: "") },
        raw.fact("CanBeLostOnDeath", "死亡时可能丢失") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
    )

    private fun trinketFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        raw.fact("TrinketEffectClass", "效果类型") { effectType(it.contentOrNull ?: "") },
        raw.fact("DropsNaturally", "会自然掉落") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CanBeReforged", "可重铸") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
    )

    private fun footwearFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "footwear")
        return listOfNotNull(
            fields.getOrNull(2)?.let { DetailFact("基础价格", DetailFormatters.gold(it) ?: it) },
            fields.getOrNull(3)?.toIntOrNull()?.let { DetailFact("防御加成", "+$it") },
            fields.getOrNull(4)?.toIntOrNull()?.let { DetailFact("免疫加成", "+$it") },
        )
    }

    private fun furnitureFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "furniture")
        return listOfNotNull(
            fields.getOrNull(1)?.let { DetailFact("家具类型", furnitureType(it)) },
            fields.getOrNull(2)?.let { DetailFact("外观尺寸", furnitureSize(it, "按家具类型决定")) },
            fields.getOrNull(3)?.let { DetailFact("碰撞尺寸", furnitureSize(it, "按家具类型决定")) },
            fields.getOrNull(4)?.toIntOrNull()?.let { DetailFact("可旋转次数", it.toString()) },
            fields.getOrNull(5)?.let { DetailFact("基础价格", DetailFormatters.gold(it) ?: it) },
        )
    }

    private fun cropFacts(raw: JsonObject, derived: JsonObject): List<DetailFact> = listOfNotNull(
        (derived.array("seasons").takeIf { it.isNotEmpty() } ?: raw.array("Seasons").takeIf { it.isNotEmpty() })?.let { DetailFact("季节", DetailFormatters.seasons(it.texts())) },
        cropGrowDays(raw, derived),
        (derived.array("growthPhases").takeIf { it.isNotEmpty() } ?: raw.array("DaysInPhase").takeIf { it.isNotEmpty() })?.let { DetailFact("生长阶段", "${it.texts().joinToString("、")}天") },
        regrowFact(raw, derived),
        preferFact(derived, raw, "needsWatering", "需要浇水", transform = { value -> DetailFormatters.booleanText(value) }, rawKey = "NeedsWatering"),
        preferFact(derived, raw, "isPaddyCrop", "水稻作物", transform = { value -> DetailFormatters.booleanText(value) }, rawKey = "IsPaddyCrop"),
        preferFact(derived, raw, "isTrellisCrop", "需要棚架", transform = { value -> DetailFormatters.booleanText(value) }, rawKey = "IsTrellisCrop"),
        raw.fact("IsRaised", "需要支架") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        harvestRange(derived, raw),
        raw.fact("HarvestMethod", "收获方式") { DetailFormatters.harvestMethod(it.contentOrNull ?: "") },
        raw.fact("ExtraHarvestChance", "额外收获概率") { DetailFormatters.percentage(it.contentOrNull ?: "") },
        raw.fact("HarvestMaxIncreasePerFarmingLevel", "每级农业额外收获") { DetailFormatters.percentage(it.contentOrNull ?: "") },
        raw.fact("HarvestMinQuality", "最低品质") { DetailFormatters.quality(it.contentOrNull ?: "") },
        raw.fact("HarvestMaxQuality", "最高品质") { DetailFormatters.quality(it.contentOrNull ?: "") },
        raw.fact("CountForMonoculture", "计入单一作物成就") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CountForPolyculture", "计入多种作物成就") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.array("PlantableLocationRules").takeIf { it.isNotEmpty() }?.let { DetailFact("可种植地点", "受地点规则限制（${it.size} 条规则）") },
    )

    private fun fishFacts(derived: JsonObject): List<DetailFact> = listOfNotNull(
        derived.fact("difficulty", "钓鱼难度") { DetailFormatters.integer(it.contentOrNull ?: "") },
        derived.fact("behavior", "鱼类行为") { DetailFormatters.fishBehavior(it.contentOrNull ?: "") },
        sizeRange(derived),
        derived.array("seasons").takeIf { it.isNotEmpty() }?.let { DetailFact("季节", DetailFormatters.seasons(it.texts())) },
        derived.fact("weather", "天气") { DetailFormatters.weather(it.contentOrNull ?: "") },
        timeWindows(derived),
    )

    private fun villagerFacts(raw: JsonObject, derived: JsonObject): List<DetailFact> = listOfNotNull(
        birthday(derived),
        derived.fact("homeRegion", "居住区域") { DetailFormatters.location(it.contentOrNull ?: "") },
        derived.fact("gender", "性别") { DetailFormatters.gender(it.contentOrNull ?: "") },
        derived.fact("canBeRomanced", "可婚配") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
        raw.fact("Age", "年龄阶段") { DetailFormatters.age(it.contentOrNull ?: "") },
        raw.fact("Manner", "待人方式") { DetailFormatters.socialTrait(it.contentOrNull ?: "") },
        raw.fact("SocialAnxiety", "社交倾向") { DetailFormatters.socialTrait(it.contentOrNull ?: "") },
        raw.fact("Optimism", "乐观程度") { DetailFormatters.socialTrait(it.contentOrNull ?: "") },
        raw.fact("CanReceiveGifts", "可收礼物") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CanSocialize", "可社交") { DetailFormatters.conditionOrValue(it.contentOrNull ?: "") },
        raw.fact("CanVisitIsland", "可前往姜岛") { DetailFormatters.conditionOrValue(it.contentOrNull ?: "") },
        raw.fact("CanGreetNearbyCharacters", "会主动打招呼") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("CanCommentOnPurchasedShopItems", "会评论购买的商品") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("PerfectionScore", "完美度评分") { DetailFormatters.formatNumber(it.contentOrNull ?: "") },
    )

    private fun monsterFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "monster")
        return listOfNotNull(
            intFact(fields, 0, "生命值"),
            intFact(fields, 1, "攻击力"),
            boolFact(fields, 4, "会飞行"),
            intFact(fields, 7, "韧性"),
            numberFact(fields, 8, "行动抖动值"),
            intFact(fields, 9, "追击阈值"),
            intFact(fields, 10, "移动速度"),
            percentageFact(fields, 11, "攻击落空概率"),
            boolFact(fields, 12, "矿井怪物"),
            intFact(fields, 13, "经验值"),
        )
    }

    private fun dropFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        raw.fact("chance", "掉落概率") { DetailFormatters.percentage(it.contentOrNull ?: "") },
    )

    private fun achievementFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "achievement")
        return listOfNotNull(
            fields.firstOrNull()?.takeIf(String::isNotBlank)?.let { DetailFact("成就目标", it) },
            fields.getOrNull(1)?.takeIf(String::isNotBlank)?.let { DetailFact("完成条件", it) },
            boolFact(fields, 2, "隐藏成就"),
        )
    }

    private fun shopFacts(raw: JsonObject): List<DetailFact> {
        if (!isStructuredShop(raw)) return emptyList()
        return listOfNotNull(
            raw.fact("Currency", "货币") { DetailFormatters.currency(it.contentOrNull ?: "") },
            raw.fact("PriceModifierMode", "价格修正方式") { modifierMode(it.contentOrNull ?: "") },
            raw.fact("ApplyProfitMargins", "应用利润率") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
            raw.fact("StackSizeVisibility", "堆叠数量显示") { it.contentOrNull },
            raw.array("Items").takeIf { it.isNotEmpty() }?.let { DetailFact("商品数量", "${it.size} 项") },
        )
    }

    private fun questFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "quest")
        return listOfNotNull(
            raw.fact("Count", "目标数量") { DetailFormatters.integer(it.contentOrNull ?: "") },
            raw.fact("RewardItemPrice", "物品奖励价格") { DetailFormatters.gold(it.contentOrNull ?: "") },
            raw.array("Targets").takeIf { it.isNotEmpty() }?.let { DetailFact("目标类型", "${it.size} 种") },
            fields.getOrNull(0)?.let { DetailFact("任务类型", questType(it)) },
            fields.getOrNull(3)?.takeIf(String::isNotBlank)?.let { DetailFact("目标说明", it) },
            fields.getOrNull(4)?.takeIf(String::isNotBlank)?.let { DetailFact("目标条件", it) },
            fields.getOrNull(6)?.let { DetailFact("金币奖励", DetailFormatters.gold(it) ?: it) },
            fields.getOrNull(7)?.takeIf(String::isNotBlank)?.let { DetailFact("奖励说明", it) },
            fields.getOrNull(8)?.let { DetailFact("可取消", DetailFormatters.booleanText(it) ?: it) },
        )
    }

    private fun specialOrderFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        raw.fact("Requester", "委托人") { it.contentOrNull },
        raw.fact("OrderType", "订单类型") { it.contentOrNull?.takeIf(String::isNotBlank) },
        raw.fact("Duration", "持续时间") { durationName(it.contentOrNull ?: "") },
        raw.fact("Repeatable", "可重复接取") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
        raw.fact("RequiredTags", "要求标签") { tagValue(it.contentOrNull ?: "") },
        raw.fact("Condition", "出现条件") { DetailFormatters.condition(it.contentOrNull) },
        raw.fact("SpecialRule", "特殊规则") { it.contentOrNull?.takeIf(String::isNotBlank) },
        raw.array("Objectives").takeIf { it.isNotEmpty() }?.let { DetailFact("目标阶段", "${it.size} 项") },
        raw.array("Rewards").takeIf { it.isNotEmpty() }?.let { DetailFact("奖励项目", "${it.size} 项") },
        raw.array("RandomizedElements").takeIf { it.isNotEmpty() }?.let { DetailFact("随机元素", "${it.size} 组") },
    )

    private fun bundleFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "bundle")
        val items = bundleItemTokens(fields.getOrNull(2))
        val structured = bundleEntries(raw)
        return listOfNotNull(
            raw.string("AreaName")?.let { DetailFact("区域", it) },
            fields.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { DetailFact("收集包", it) },
            fields.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { DetailFact("奖励", it) },
            fields.getOrNull(3)?.toIntOrNull()?.let { DetailFact("颜色编号", bundleColor(it)) },
            fields.getOrNull(4)?.toIntOrNull()?.takeIf { it >= 0 }?.let { DetailFact("所需槽位", it.toString()) },
            items.takeIf { it.isNotEmpty() }?.let { DetailFact("所需物品", "${it.size} 项") },
            structured.takeIf { it.isNotEmpty() }?.let { DetailFact("收集包数量", "${it.size} 项") },
        )
    }

    private fun tailoringFacts(raw: JsonObject): List<DetailFact> = listOfNotNull(
        raw.array("FirstItemTags").takeIf { it.isNotEmpty() }?.let { DetailFact("第一件材料", DetailFormatters.contextTags(it.texts())) },
        raw.array("SecondItemTags").takeIf { it.isNotEmpty() }?.let { DetailFact("第二件材料", DetailFormatters.contextTags(it.texts())) },
        raw.fact("SpendRightItem", "消耗右侧材料") { DetailFormatters.booleanText(it.contentOrNull ?: "") },
    )

    private fun scheduleFacts(raw: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "npc_schedule")
        val entries = fields.filter(::isScheduleEntry).mapNotNull(::formatScheduleEntry)
        val directives = fields.filter(::isScheduleDirective).mapNotNull(::scheduleDirective)
        val condition = fields.firstOrNull()?.takeIf { !isScheduleEntry(it) && !isScheduleDirective(it) }
        return listOfNotNull(
            condition?.let { DetailFact("日程条件", DetailFormatters.condition(it) ?: "受游戏条件限制") },
            directives.takeIf { it.isNotEmpty() }?.let { DetailFact("日程指令", it.joinToString("；")) },
            entries.takeIf { it.isNotEmpty() }?.let { DetailFact("日程", it.joinToString("\n")) },
        )
    }

    private fun islandEventFacts(raw: JsonObject, sourceId: String): List<DetailFact> {
        val id = raw.string("eventId") ?: raw.string("Id") ?: sourceId
        val time = Regex("(?:^|/)t-(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(id)
        val weather = Regex("(?:^|/)w-([A-Za-z]+)", RegexOption.IGNORE_CASE).find(id)
        val friendship = Regex("(?:^|/)f-([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(id)
        val location = id.substringBefore(':').takeIf { it.isNotBlank() }
        return listOfNotNull(
            location?.let { DetailFact("地点", DetailFormatters.location(it)) },
            weather?.groupValues?.getOrNull(1)?.let { DetailFact("天气", DetailFormatters.weather(it)) },
            time?.let { DetailFact("时间段", "${DetailFormatters.gameTime(it.groupValues[1].toInt())} - ${DetailFormatters.gameTime(it.groupValues[2].toInt())}") },
            friendship?.groupValues?.getOrNull(1)?.let { DetailFact("友谊条件", it) },
        )
    }

    private fun recipeFacts(raw: JsonObject, derived: JsonObject): List<DetailFact> {
        val fields = legacyFields(raw, "recipe")
        val outputTokens = fields.getOrNull(2)?.trim()?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        val unlockIndex = if (raw.string("outputEntityType") == "big_craftable" || fields.getOrNull(3)?.equals("true", true) == true) 4 else 3
        return listOfNotNull(
            derived.fact("outputEntityType", "产物类型") { entityTypeName(it.contentOrNull ?: "") },
            outputTokens.drop(1).firstOrNull()?.toIntOrNull()?.let { DetailFact("每次产出", it.toString()) },
            fields.getOrNull(unlockIndex)?.takeIf { it.isNotBlank() && !it.equals("null", true) }?.let { DetailFact("解锁条件", recipeCondition(it)) },
            fields.getOrNull(1)?.takeIf { raw.string("outputEntityType") == "big_craftable" }?.let { DetailFact("制作菜单", menuName(it)) },
            fields.getOrNull(3)?.takeIf { raw.string("outputEntityType") == "big_craftable" }?.let { DetailFact("大型工艺品", DetailFormatters.booleanText(it) ?: it) },
        )
    }

    private fun genericFacts(raw: JsonObject, derived: JsonObject): List<DetailFact> = listOfNotNull(
        derived.fact("sellPrice", "售价") { it.contentOrNull?.let(DetailFormatters::gold) },
        derived.fact("edibility", "食用") { it.contentOrNull?.let(DetailFormatters::edibility) },
        raw.fact("Price", "基础售价") { DetailFormatters.gold(it.contentOrNull ?: "") },
        raw.fact("SalePrice", "出售价格") { DetailFormatters.gold(it.contentOrNull ?: "") },
        raw.fact("Category", "物品分类") { DetailFormatters.category(it.contentOrNull ?: "") },
        contextTagFact(derived, raw),
    )

    private fun cropRelations(derived: JsonObject) = DetailRelationGroup("种植与收获", buildList {
        derived.relation("seedItemId", "种子")?.let(::add)
        derived.relation("harvestItemId", "收获物")?.let(::add)
        addAll(shopOffers(derived.array("seedShopOffers"), "种子购买来源"))
    })

    private fun fishRelations(derived: JsonObject) = DetailRelationGroup("出现与养殖", buildList {
        derived.array("locations").forEach { location -> add(locationRelation(location)) }
        derived.array("fishPondRules").forEach { rule ->
            add(fishPondRelation(rule))
            rule.asObject()?.let { pondRule ->
                pondRule.array("producedItems").forEach { produced ->
                    val item = produced.asObject() ?: return@forEach
                    val itemId = item.string("itemId")
                    if (isResolvableItemReference(itemId)) add(DetailRelation("鱼塘产出", itemId, outputDetails(item)))
                }
                pondRule.objectAt("populationGates").forEach { (population, rewards) ->
                    val values = rewards.asPrimitive()?.contentOrNull ?: rewards.asArray()?.texts()?.joinToString("、") ?: return@forEach
                    add(DetailRelation("人口门槛", null, listOf(
                        DetailFact("所需人口", population),
                        DetailFact("奖励物品", values),
                    )))
                }
            }
        }
    })

    private fun villagerRelations(derived: JsonObject) = DetailRelationGroup(
        "人物关系",
        listOfNotNull(derived.relation("loveInterest", "恋爱对象")),
    )

    private fun recipeRelations(raw: JsonObject, derived: JsonObject) = DetailRelationGroup("配方", buildList {
        derived.array("ingredients").forEach { ingredient -> add(ingredientRelation(ingredient)) }
        derived.string("outputItemId")?.let { output ->
            add(DetailRelation("产物", output, listOfNotNull(
                derived.string("outputEntityType")?.let { DetailFact("类型", entityTypeName(it)) },
            )))
        }
    })

    private fun shopRelations(derived: JsonObject) = DetailRelationGroup("商店来源", shopOffers(derived.array("shopOffers"), "商店"))

    private fun shopEntityRelations(raw: JsonObject): DetailRelationGroup {
        if (!isStructuredShop(raw)) return DetailRelationGroup("商品", emptyList())
        return DetailRelationGroup("商品", buildList {
        raw.array("Items").forEach { element ->
            val offer = element.asObject() ?: return@forEach
            if (offer.string("Id").isNullOrBlank()) return@forEach
            val fixed = offer.string("ItemId")
            val random = offer.array("RandomItemId").texts().ifEmpty {
                offer.string("RandomItemId")?.let(::listOf).orEmpty()
            }
            val details = offerDetails(offer)
            when {
                isResolvableItemReference(fixed) -> add(DetailRelation("商品", fixed, details))
                random.any(::isResolvableItemReference) -> random.filter(::isResolvableItemReference).forEach { id ->
                    add(DetailRelation("随机商品", id, details))
                }
                else -> add(DetailRelation("商品", null, details + DetailFact("商品类型", "随机商品池")))
            }
            offer.string("TradeItemId")?.let { trade ->
                if (isResolvableItemReference(trade)) add(DetailRelation("兑换材料", trade, listOfNotNull(offer.fact("TradeItemAmount", "数量") { DetailFormatters.integer(it.contentOrNull ?: "") })))
            }
        }
        raw.array("Owners").mapNotNull { it.asObject()?.string("Name") ?: it.asPrimitive()?.contentOrNull }.forEach { owner ->
            add(DetailRelation("店主", "villager:$owner"))
        }
    })
    }

    private fun machineRelations(derived: JsonObject) = DetailRelationGroup("机器用途", buildList {
        derived.array("machineUses").forEach { element ->
            val machine = element.asObject() ?: return@forEach
            val machineId = machine.string("machineId")
            val baseDetails = machineDetails(machine)
            if (machineId != null) add(DetailRelation("机器", machineId, baseDetails))
            machine.array("outputs").forEach { outputElement ->
                val output = outputElement.asObject() ?: return@forEach
                val itemId = output.string("itemId")
                if (isResolvableItemReference(itemId)) add(DetailRelation("产物", itemId, outputDetails(output)))
            }
        }
    })

    private fun usedInRelations(derived: JsonObject) = DetailRelationGroup(
        "被用于",
        derived.array("usedIn").map(::usedInRelation),
    )

    private fun usedInRelation(element: JsonElement): DetailRelation {
        val item = element.asObject() ?: return DetailRelation("用途", null)
        return DetailRelation(
            DetailFormatters.usageType(item.string("usageType") ?: "用途"),
            item.string("usageId"),
            listOfNotNull(
                item.fact("quantity", "数量") { DetailFormatters.integer(it.contentOrNull ?: "") },
                item.fact("quality", "最低品质") { DetailFormatters.quality(it.contentOrNull ?: "") },
            ),
        )
    }

    private fun monsterRelations(raw: JsonObject) = DetailRelationGroup("怪物掉落", buildList {
        val fields = legacyFields(raw, "monster")
        val tokens = fields.getOrNull(6)?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        for (index in tokens.indices step 2) {
            val rawId = tokens.getOrNull(index) ?: continue
            if (rawId == "8") continue
            val chance = tokens.getOrNull(index + 1)
            val itemId = normalizeMonsterDropId(rawId)
            if (!isResolvableItemReference(itemId)) continue
            add(DetailRelation("掉落物", itemId, listOfNotNull(
                chance?.let { DetailFact("概率", DetailFormatters.percentage(it) ?: it) },
                if (rawId.startsWith('-')) DetailFact("数量", "1 - 3") else null,
            )))
        }
    })

    private fun dropRelations(raw: JsonObject) = DetailRelationGroup("掉落关联", listOfNotNull(
        raw.string("monsterId")?.let { DetailRelation("怪物", "monster:$it") },
        raw.string("itemId")?.let(::normalizeMonsterDropId)?.takeIf(::isResolvableItemReference)?.let { DetailRelation("物品", it) },
    ))

    private fun villagerGiftRelations(raw: JsonObject) = DetailRelationGroup("礼物喜好", buildList {
        val fields = legacyFields(raw, "villager_gift")
        val labels = listOf("最爱", "喜欢", "一般", "不喜欢", "讨厌")
        val indexes = listOf(1, 3, 5, 7, 9)
        indexes.forEachIndexed { offset, index ->
            fields.getOrNull(index)?.split(Regex("\\s+"))?.filter(String::isNotBlank)?.forEach { itemId ->
                val special = DetailFormatters.specialIngredient(itemId) ?: DetailFormatters.categoryTag(itemId)
                if (special != null) add(DetailRelation(labels[offset], null, listOf(DetailFact("范围", special))))
                else if (isResolvableItemReference(itemId)) add(DetailRelation(labels[offset], itemId))
            }
        }
    })

    private fun bundleRelations(raw: JsonObject) = DetailRelationGroup("收集包要求", buildList {
        val fields = legacyFields(raw, "bundle")
        bundleItemTokens(fields.getOrNull(2)).forEach { (itemId, quantity, quality) ->
            val special = DetailFormatters.specialIngredient(itemId) ?: DetailFormatters.categoryTag(itemId)
            when {
                special != null -> add(DetailRelation("所需物品", null, listOfNotNull(
                    DetailFact("范围", special),
                    DetailFact("数量", quantity.toString()),
                    DetailFormatters.quality(quality.toString())?.let { DetailFact("品质", it) },
                )))
                isResolvableItemReference(itemId) -> add(DetailRelation("所需物品", itemId, listOfNotNull(
                    DetailFact("数量", quantity.toString()),
                    DetailFormatters.quality(quality.toString())?.let { DetailFact("品质", it) },
                )))
            }
        }
        bundleRewardRelation(fields.getOrNull(1))?.let(::add)
        val area = raw.string("AreaName")?.replace(Regex("\\s+"), "-")
        bundleEntries(raw).forEach { bundle ->
            val index = bundle.intOrNull("Index")?.takeIf { it >= 0 } ?: return@forEach
            val target = area?.let { "bundle:$it/$index" }
            add(DetailRelation("子收集包", target, listOfNotNull(
                bundle.string("Name")?.let { DetailFact("名称", it) },
                bundle.string("Items")?.let { DetailFact("需求", it) },
                bundle.string("Pick")?.toIntOrNull()?.takeIf { it >= 0 }?.let { DetailFact("任选数量", it.toString()) },
                bundle.string("Reward")?.let { DetailFact("奖励", it) },
            )))
        }
    })

    private fun questRelations(raw: JsonObject) = DetailRelationGroup("任务关联", buildList {
        raw.string("RewardItemId")?.let { reward ->
            if (isResolvableItemReference(reward)) add(DetailRelation("物品奖励", reward, listOfNotNull(
                raw.string("RewardItemPrice")?.let { DetailFact("奖励价格", DetailFormatters.gold(it) ?: it) },
            )))
        }
        raw.array("Targets").texts().forEach { target ->
            add(DetailRelation("目标", "monster:${target.replace(' ', '-')}", listOfNotNull(
                raw.string("Count")?.let { DetailFact("数量", DetailFormatters.integer(it) ?: it) },
            )))
        }
        addAll(legacyQuestRelations(raw))
    })

    private fun legacyQuestRelations(raw: JsonObject): List<DetailRelation> {
        val fields = legacyFields(raw, "quest")
        val type = fields.getOrNull(0)?.lowercase() ?: return emptyList()
        val tokens = fields.getOrNull(4)?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        val quantity = tokens.getOrNull(2)?.toIntOrNull() ?: tokens.getOrNull(1)?.toIntOrNull()
        return buildList {
            when (type) {
                "itemdelivery", "lostitem", "secretlostitem" -> {
                    tokens.getOrNull(0)?.let { add(DetailRelation("人物", "villager:$it")) }
                    tokens.drop(1).firstOrNull(::isQuestItemReference)?.let { item ->
                        add(DetailRelation("目标物品", item, listOfNotNull(quantity?.let { DetailFact("数量", it.toString()) })))
                    }
                }
                "monster" -> tokens.getOrNull(0)?.let { monster ->
                    add(DetailRelation("目标怪物", "monster:${monster.replace('_', '-')}", listOfNotNull(quantity?.let { DetailFact("数量", it.toString()) })))
                }
                "crafting", "itemharvest", "resource" -> tokens.getOrNull(0)?.let { item ->
                    if (isQuestItemReference(item)) add(DetailRelation("目标物品", item, listOfNotNull(quantity?.let { DetailFact("数量", it.toString()) })))
                }
                "location", "building" -> tokens.firstOrNull()?.let { add(DetailRelation("目标地点", null, listOf(DetailFact("名称", DetailFormatters.location(it))))) }
            }
        }
    }

    private fun specialOrderRelations(raw: JsonObject) = DetailRelationGroup("订单目标", buildList {
        raw.string("Requester")?.takeIf(String::isNotBlank)?.let { add(DetailRelation("委托人", "villager:$it")) }
        raw.array("Objectives").forEach { element ->
            val objective = element.asObject() ?: return@forEach
            val count = objective.string("RequiredCount")
            val tags = objective.objectAt("Data").string("AcceptedContextTags")
            add(DetailRelation("目标", null, listOfNotNull(
                count?.let { DetailFact("数量", DetailFormatters.integer(it) ?: it) },
                tags?.let { DetailFact("标签", "按目标标签匹配") },
            )))
        }
    })

    private fun tailoringRelations(raw: JsonObject) = DetailRelationGroup("裁缝产物", buildList {
        val ids = buildList {
            raw.string("CraftedItemId")?.let(::add)
            addAll(raw.array("CraftedItemIds").texts())
            raw.string("CraftedItemIdFeminine")?.let(::add)
        }.distinct()
        ids.forEach { id ->
            if (isResolvableItemReference(id)) add(DetailRelation("产物", id))
            else add(DetailRelation("产物", null, listOf(DetailFact("产物类型", "衣物或帽子"))))
        }
    })

    private fun scheduleRelations(raw: JsonObject) = DetailRelationGroup("日程地点", buildList {
        legacyFields(raw, "npc_schedule").filter(::isScheduleEntry).mapNotNull(::scheduleLocationRelation).forEach(::add)
    })

    private fun shopOffers(items: JsonArray, label: String) = items.mapNotNull { item ->
        val offer = item.asObject() ?: return@mapNotNull null
        offer.string("shopId")?.let { shop -> DetailRelation(label, shop, offerDetails(offer)) }
    }

    private fun locationRelation(element: JsonElement): DetailRelation {
        val item = element.asObject() ?: return DetailRelation("地点", null)
        val locationId = item.string("locationId")
        val areaId = item.string("areaId")
        return DetailRelation("地点", null, listOfNotNull(
            locationId?.let { DetailFact("地点", DetailFormatters.location(it, areaId)) },
            item.string("season")?.let { DetailFact("季节", DetailFormatters.season(it)) },
            item.array("seasons").takeIf { it.isNotEmpty() }?.let { DetailFact("季节", DetailFormatters.seasons(it.texts())) },
            item.string("chance")?.let { DetailFact("出现概率", DetailFormatters.percentage(it) ?: it) },
            item.fact("minFishingLevel", "最低钓鱼等级") { DetailFormatters.integer(it.contentOrNull ?: "") },
            item.fact("minDistanceFromShore", "离岸最小距离") { distance(it.contentOrNull ?: "") },
            item.fact("maxDistanceFromShore", "离岸最大距离") { distance(it.contentOrNull ?: "") },
            DetailFormatters.condition(item.string("condition"))?.let { DetailFact("出现条件", it) },
        ))
    }

    private fun fishPondRelation(element: JsonElement): DetailRelation {
        val item = element.asObject() ?: return DetailRelation("鱼塘规则", null)
        return DetailRelation("鱼塘规则", null, listOfNotNull(
            item.string("ruleId")?.let { DetailFact("规则", it) },
            item.array("requiredTags").takeIf { it.isNotEmpty() }?.let { DetailFact("匹配标签", DetailFormatters.contextTags(it.texts())) },
            item.fact("maxPopulation", "最大数量") { population(it.contentOrNull ?: "") },
            item.fact("spawnTime", "繁殖周期") { DetailFormatters.days(it.intOrNull ?: return@fact null) },
            item.array("populationGates").takeIf { it.isNotEmpty() }?.let { DetailFact("人口门槛", "${it.size} 项") },
            item.objectAt("populationGates").takeIf { it.isNotEmpty() }?.let { DetailFact("人口门槛", "${it.size} 项") },
        ))
    }

    private fun ingredientRelation(element: JsonElement): DetailRelation {
        val item = element.asObject() ?: return DetailRelation("原料", null)
        val itemId = item.string("itemId")
        val special = itemId?.let(DetailFormatters::specialIngredient)
        return if (special != null) {
            DetailRelation("原料", null, listOfNotNull(
                DetailFact("原料范围", special),
                item.fact("quantity", "数量") { DetailFormatters.integer(it.contentOrNull ?: "") },
            ))
        } else {
            DetailRelation("原料", itemId, listOfNotNull(item.fact("quantity", "数量") { DetailFormatters.integer(it.contentOrNull ?: "") }))
        }
    }

    private fun machineDetails(machine: JsonObject): List<DetailFact> = listOfNotNull(
        machine.fact("requiredCount", "所需数量") { DetailFormatters.integer(it.contentOrNull ?: "") },
        machine.array("requiredTags").takeIf { it.isNotEmpty() }?.let { DetailFact("输入标签", DetailFormatters.contextTags(it.texts())) },
        readyFact(machine),
        machine.array("outputs").takeIf { it.isNotEmpty() }?.let { DetailFact("产出规则", "${it.size} 条") },
        DetailFormatters.condition(machine.string("condition"))?.let { DetailFact("条件", it) },
    )

    private fun outputDetails(output: JsonObject): List<DetailFact> = listOfNotNull(
        output.string("outputMethod")?.let { DetailFact("产出方式", DetailFormatters.outputMethod(it)) },
        output.fact("requiredPopulation", "最低人口") { DetailFormatters.integer(it.contentOrNull ?: "") },
        output.fact("chance", "概率") { DetailFormatters.percentage(it.contentOrNull ?: "") },
        stackFact(output, "minStack", "maxStack", "数量"),
        output.fact("quality", "品质") { DetailFormatters.quality(it.contentOrNull ?: "") },
        DetailFormatters.condition(output.string("condition"))?.let { DetailFact("条件", it) },
    )

    private fun readyFact(machine: JsonObject): DetailFact? {
        val minutes = machine.string("minutesUntilReady")?.toIntOrNull()
        val days = machine.string("daysUntilReady")?.toIntOrNull()
        val value = buildList {
            minutes?.let(DetailFormatters::durationMinutes)?.let(::add)
            if (days != null && days >= 0) add(DetailFormatters.days(days).orEmpty())
        }.distinct().joinToString("，")
        return when {
            value.isNotBlank() -> DetailFact("完成时间", value)
            machine.array("outputs").any { it.asObject()?.string("outputMethod")?.isNotBlank() == true } -> DetailFact("完成时间", "由机器规则决定")
            else -> null
        }
    }

    private fun offerDetails(offer: JsonObject): List<DetailFact> = listOfNotNull(
        offer.stringAny("currency", "Currency")?.let { DetailFact("货币", DetailFormatters.currency(it) ?: it) },
        offer.stringAny("price", "Price")?.let { DetailFact("价格", price(it)) },
        offer.stringAny("tradeItemAmount", "TradeItemAmount")?.toIntOrNull()?.takeIf { it > 0 }?.let { DetailFact("兑换数量", it.toString()) },
        stockFact(offer),
        stackFact(offer, "minStack", "maxStack", "购买数量"),
        offer.stringAny("quality", "Quality")?.let { DetailFact("品质", DetailFormatters.quality(it) ?: it) },
        offer.stringAny("isRecipe", "IsRecipe")?.let { DetailFact("商品是配方", DetailFormatters.booleanText(it) ?: it) },
        offer.stringAny("useObjectDataPrice", "UseObjectDataPrice")?.let { DetailFact("使用物品基础价格", DetailFormatters.booleanText(it) ?: it) },
        offer.stringAny("ignoreShopPriceModifiers", "IgnoreShopPriceModifiers")?.let { DetailFact("忽略商店价格修正", DetailFormatters.booleanText(it) ?: it) },
        offer.stringAny("avoidRepeat", "AvoidRepeat")?.let { DetailFact("避免重复商品", DetailFormatters.booleanText(it) ?: it) },
        offer.stringAny("toolUpgradeLevel", "ToolUpgradeLevel")?.toIntOrNull()?.takeIf { it >= 0 }?.let { DetailFact("工具升级等级", "第 ${it} 级") },
        offer.stringAny("maxItems", "MaxItems")?.toIntOrNull()?.takeIf { it >= 0 }?.let { DetailFact("最大商品数", it.toString()) },
        offer.stringAny("priceModifierMode", "PriceModifierMode")?.let { DetailFact("价格修正规则", modifierMode(it)) },
        offer.stringAny("availableStockModifierMode", "AvailableStockModifierMode")?.let { DetailFact("库存修正规则", modifierMode(it)) },
        offer.stringAny("stackModifierMode", "StackModifierMode")?.let { DetailFact("堆叠修正规则", modifierMode(it)) },
        offer.stringAny("qualityModifierMode", "QualityModifierMode")?.let { DetailFact("品质修正规则", modifierMode(it)) },
        offer.stringAny("condition", "Condition")?.let { DetailFormatters.condition(it)?.let { value -> DetailFact("条件", value) } },
        offer.stringAny("perItemCondition", "PerItemCondition")?.let { DetailFormatters.condition(it)?.let { value -> DetailFact("每件条件", value) } },
        offer.arrayAny("shopPriceModifiers", "ShopPriceModifiers").takeIf { it.isNotEmpty() }?.let { DetailFact("商店价格规则", "${it.size} 条") },
        offer.arrayAny("priceModifiers", "PriceModifiers").takeIf { it.isNotEmpty() }?.let { DetailFact("商品价格规则", "${it.size} 条") },
        offer.arrayAny("availableStockModifiers", "AvailableStockModifiers").takeIf { it.isNotEmpty() }?.let { DetailFact("库存规则", "${it.size} 条") },
        offer.arrayAny("stackModifiers", "StackModifiers").takeIf { it.isNotEmpty() }?.let { DetailFact("堆叠规则", "${it.size} 条") },
        offer.arrayAny("qualityModifiers", "QualityModifiers").takeIf { it.isNotEmpty() }?.let { DetailFact("品质规则", "${it.size} 条") },
    )

    private fun stockFact(offer: JsonObject): DetailFact? {
        val value = offer.stringAny("availableStock", "AvailableStock") ?: return null
        val stock = if (value == "-1") "不限库存" else DetailFormatters.integer(value) ?: value
        val limit = offer.stringAny("availableStockLimit", "AvailableStockLimit")?.let { stockLimit(it) }
        return DetailFact("库存", listOfNotNull(stock, limit).joinToString("，"))
    }

    private fun rangeFact(raw: JsonObject, minKey: String, maxKey: String, label: String): DetailFact? {
        val min = raw.string(minKey)
        val max = raw.string(maxKey)
        return rangeFact(min, max, label)
    }

    private fun rangeFact(min: String?, max: String?, label: String): DetailFact? {
        val values = listOfNotNull(min, max).mapNotNull(DetailFormatters::formatNumber).distinct()
        return values.takeIf { it.isNotEmpty() }?.let { DetailFact(label, it.joinToString(" - ")) }
    }

    private fun cropGrowDays(raw: JsonObject, derived: JsonObject): DetailFact? {
        derived.string("growDays")?.toIntOrNull()?.let { return DetailFact("总生长天数", "${it}天") }
        val phases = raw.array("DaysInPhase").texts().mapNotNull(String::toIntOrNull)
        return phases.takeIf { it.isNotEmpty() }?.let { DetailFact("总生长天数", "${it.sum()}天") }
    }

    private fun harvestRange(derived: JsonObject, raw: JsonObject): DetailFact? = rangeFact(
        derived.string("harvestMin") ?: raw.string("HarvestMinStack"),
        derived.string("harvestMax") ?: raw.string("HarvestMaxStack"),
        "收获数量",
    )

    private fun sizeRange(derived: JsonObject): DetailFact? = rangeFact(derived.string("minSize"), derived.string("maxSize"), "尺寸")

    private fun timeWindows(derived: JsonObject): DetailFact? = derived.array("timeWindows").mapNotNull { it.asPrimitive()?.intOrNull }.let { times ->
        DetailFormatters.gameTimes(times)?.let { DetailFact("时间段", it) }
    }

    private fun birthday(derived: JsonObject): DetailFact? {
        val birthday = derived.objectAt("birthday")
        val day = birthday.string("day")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val season = birthday.string("season")?.let(DetailFormatters::season)
        return DetailFact("生日", listOfNotNull(season, "${day}日").joinToString(" "))
    }

    private fun contextTagFact(derived: JsonObject, raw: JsonObject): DetailFact? {
        val tags = derived.array("contextTags").texts().ifEmpty { raw.array("ContextTags").texts() }
        return tags.takeIf { it.isNotEmpty() }?.let { DetailFact("用途标签", DetailFormatters.contextTags(it)) }
    }

    private fun preferFact(
        primary: JsonObject,
        fallback: JsonObject,
        key: String,
        label: String,
        transform: (String) -> String?,
        rawKey: String = key,
    ): DetailFact? = primary.fact(key, label) { transform(it.contentOrNull ?: "") }
        ?: fallback.fact(rawKey, label) { transform(it.contentOrNull ?: "") }

    private fun regrowFact(raw: JsonObject, derived: JsonObject): DetailFact? {
        val value = derived.string("regrowDays") ?: raw.string("RegrowDays") ?: return null
        return when (val days = value.toIntOrNull()) {
            -1 -> DetailFact("再生", "不再生长")
            null -> DetailFact("再生天数", value)
            else -> DetailFact("再生天数", "${days}天")
        }
    }

    private fun signedFact(raw: JsonObject, key: String, label: String): DetailFact? = raw.fact(key, label) {
        it.contentOrNull?.toIntOrNull()?.let(DetailFormatters::signed)
    }

    private fun intFact(values: List<String>, index: Int, label: String): DetailFact? = values.getOrNull(index)?.toIntOrNull()?.let { DetailFact(label, it.toString()) }

    private fun numberFact(values: List<String>, index: Int, label: String): DetailFact? = values.getOrNull(index)?.let { DetailFormatters.formatNumber(it)?.let { value -> DetailFact(label, value) } }

    private fun percentageFact(values: List<String>, index: Int, label: String): DetailFact? = values.getOrNull(index)?.let { DetailFormatters.percentage(it)?.let { value -> DetailFact(label, value) } }

    private fun boolFact(values: List<String>, index: Int, label: String): DetailFact? = values.getOrNull(index)?.let { value -> DetailFormatters.booleanText(value)?.let { DetailFact(label, it) } }

    private fun stackFact(value: JsonObject, minKey: String, maxKey: String, label: String): DetailFact? {
        val min = value.stringAny(minKey, minKey.capitalized())?.toIntOrNull()
        val max = value.stringAny(maxKey, maxKey.capitalized())?.toIntOrNull()
        if (min == null && max == null) return null
        if (min == -1 && max == -1) return DetailFact(label, "默认数量")
        val values = listOfNotNull(min?.takeIf { it >= 0 }, max?.takeIf { it >= 0 }).distinct()
        return DetailFact(label, values.joinToString(" - ").ifBlank { "默认数量" })
    }

    private fun legacyFields(raw: JsonObject, type: String): List<String> {
        val values = raw.array("legacyFields").texts()
        if (values.isNotEmpty()) {
            if (values.size == 1 && type == "achievement" && values[0].contains('^')) return values[0].split('^')
            return values
        }
        val legacy = raw.string("legacyValue") ?: return emptyList()
        return legacy.split(if (type == "achievement") '^' else '/').toList()
    }

    private fun bundleRewardRelation(value: String?): DetailRelation? {
        val tokens = value?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        val prefix = tokens.getOrNull(0) ?: return null
        val itemId = tokens.getOrNull(1) ?: return null
        val quantity = tokens.getOrNull(2)?.toIntOrNull()
        val qualified = when (prefix.uppercase()) {
            "O" -> "(O)$itemId"
            "BO" -> "(BC)$itemId"
            else -> null
        }
        return if (qualified != null && isResolvableItemReference(qualified)) {
            DetailRelation("奖励", qualified, listOfNotNull(quantity?.let { DetailFact("数量", it.toString()) }))
        } else {
            DetailRelation("奖励", null, listOf(DetailFact("说明", value.orEmpty())))
        }
    }

    private fun bundleEntries(raw: JsonObject): List<JsonObject> = buildList {
        addAll(raw.array("Bundles").mapNotNull { it.asObject() })
        raw.array("BundleSets").forEach { set -> addAll(set.asObject()?.array("Bundles")?.mapNotNull { it.asObject() }.orEmpty()) }
    }

    private fun bundleColor(value: Int): String = when (value) {
        0 -> "绿色"
        1 -> "紫色"
        2 -> "橙色"
        3 -> "黄色"
        4 -> "红色"
        5 -> "蓝色"
        6 -> "青色"
        else -> "未知颜色（$value）"
    }

    private fun bundleItemTokens(value: String?): List<Triple<String, Int, Int>> {
        val tokens = value?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        return buildList {
            var index = 0
            while (index + 2 < tokens.size) {
                val id = tokens[index]
                val quantity = tokens[index + 1].toIntOrNull()
                val quality = tokens[index + 2].toIntOrNull()
                if (quantity != null && quality != null) add(Triple(id, quantity, quality))
                index += 3
            }
        }
    }

    private fun formatScheduleEntry(value: String): String? {
        val tokens = value.split(Regex("\\s+")).filter(String::isNotBlank)
        val time = scheduleTime(tokens.getOrNull(0)) ?: return null
        val location = tokens.getOrNull(1) ?: return null
        val x = tokens.getOrNull(2)?.toIntOrNull()
        val y = tokens.getOrNull(3)?.toIntOrNull()
        return buildString {
            append(DetailFormatters.gameTime(time))
            append("：")
            append(DetailFormatters.location(location))
            if (x != null && y != null) append("（${x}, ${y}）")
        }
    }

    private fun scheduleLocationRelation(value: String): DetailRelation? {
        val tokens = value.split(Regex("\\s+")).filter(String::isNotBlank)
        val time = scheduleTime(tokens.getOrNull(0)) ?: return null
        val location = tokens.getOrNull(1) ?: return null
        val x = tokens.getOrNull(2)?.toIntOrNull()
        val y = tokens.getOrNull(3)?.toIntOrNull()
        return DetailRelation("地点", null, listOfNotNull(
            DetailFact("时间", DetailFormatters.gameTime(time)),
            DetailFact("地点", DetailFormatters.location(location)),
            if (x != null && y != null) DetailFact("坐标", "(${x}, ${y})") else null,
        ))
    }

    private fun isScheduleEntry(value: String): Boolean = scheduleTime(value.split(Regex("\\s+")).firstOrNull()) != null

    private fun scheduleTime(token: String?): Int? = token?.removePrefix("a")?.toIntOrNull()

    private fun isScheduleDirective(value: String): Boolean = value.startsWith("MAIL ") || value.startsWith("GOTO ")

    private fun scheduleDirective(value: String): String? {
        val parts = value.split(Regex("\\s+"), limit = 2)
        return when (parts.firstOrNull()?.uppercase()) {
            "MAIL" -> parts.getOrNull(1)?.let { "触发游戏邮件" }
            "GOTO" -> parts.getOrNull(1)?.let { "跳转到：${DetailFormatters.scheduleRule(it)}" }
            else -> null
        }
    }

    private fun normalizeMonsterDropId(value: String): String {
        val id = value.removePrefix("-")
        val mapped = when (id) {
            "0" -> "378"
            "2" -> "380"
            "4" -> "382"
            "6" -> "384"
            "10" -> "386"
            "12" -> "388"
            "14" -> "390"
            else -> id
        }
        return mapped
    }

    private fun distance(value: String): String? = when (value.toIntOrNull()) {
        -1 -> "不限制"
        null -> value.takeIf(String::isNotBlank)
        else -> value
    }

    private fun population(value: String): String? = when (value.toIntOrNull()) {
        -1 -> "默认上限"
        null -> value.takeIf(String::isNotBlank)
        else -> value
    }

    private fun price(value: String): String = if (value == "-1") "按物品基础价格" else DetailFormatters.gold(value) ?: value

    private fun stockLimit(value: String): String = when (value.lowercase()) {
        "global" -> "全局库存"
        "player" -> "每位玩家独立库存"
        else -> value
    }

    private fun modifierMode(value: String): String = when (value.lowercase()) {
        "stack" -> "叠加"
        "minimum" -> "取较小值"
        "maximum" -> "取较大值"
        else -> value
    }

    private fun durationName(value: String): String = when (value.lowercase()) {
        "day", "days", "oneday" -> "一天"
        "week", "weeks" -> "一周"
        "month", "months" -> "一个月"
        else -> value
    }

    private fun fragility(value: String): String = when (value.toIntOrNull()) {
        0 -> "不会因时间损坏"
        1 -> "会随时间损坏"
        else -> DetailFormatters.integer(value) ?: value
    }

    private fun mineLevel(value: String): String = when (value.toIntOrNull()) {
        null -> value
        -1 -> "不适用"
        else -> "第 ${value.toInt()} 层"
    }

    private fun upgradeLevel(value: String): String = when (value.toIntOrNull()) {
        null -> value
        -1 -> "不可升级"
        else -> "第 ${value.toInt()} 级"
    }

    private fun attachmentSlots(value: String): String = when (value.toIntOrNull()) {
        null -> value
        -1 -> "不支持附件"
        else -> "${value.toInt()} 个"
    }

    private fun questType(value: String): String = when (value.lowercase()) {
        "basic" -> "普通任务"
        "crafting" -> "制作任务"
        "itemdelivery" -> "物品递送"
        "monster" -> "讨伐怪物"
        "social" -> "社交任务"
        "location" -> "地点任务"
        "fishing" -> "钓鱼任务"
        "building" -> "建筑任务"
        "itemharvest" -> "收获任务"
        "resource" -> "资源收集"
        "weeding" -> "除草任务"
        else -> value
    }

    private fun effectType(value: String): String = value.substringAfterLast('.').let { DetailFormatters.toolClass(it) }

    private fun furnitureType(value: String): String = when (value.lowercase()) {
        "chair" -> "椅子"
        "bench" -> "长凳"
        "couch" -> "沙发"
        "armchair" -> "扶手椅"
        "dresser" -> "梳妆台"
        "longtable" -> "长桌"
        "painting" -> "画"
        "lamp" -> "灯"
        "decor" -> "装饰品"
        "bookcase" -> "书柜"
        "table" -> "桌子"
        "rug" -> "地毯"
        "window" -> "窗户"
        "fireplace" -> "壁炉"
        "bed" -> "床"
        "torch" -> "火把"
        "sconce" -> "壁灯"
        else -> value
    }

    private fun furnitureSize(value: String, fallback: String): String {
        if (value == "-1") return fallback
        val parts = value.split(Regex("\\s+"))
        return if (parts.size >= 2) "${parts[0]} × ${parts[1]} 格" else value
    }

    private fun objectType(value: String): String = when (value.lowercase()) {
        "basic" -> "基础物品"
        "minerals" -> "矿物"
        "ring" -> "戒指"
        "fish" -> "鱼"
        "archaeology" -> "考古物"
        "crafting" -> "制作材料"
        else -> value
    }

    private fun entityTypeName(value: String): String = when (value) {
        "object" -> "物品"
        "big_craftable" -> "大型工艺品"
        "weapon" -> "武器"
        else -> value
    }

    private fun recipeCondition(value: String): String = when {
        value.startsWith("s ", true) || value.lowercase() in setOf("farming", "fishing", "foraging", "mining", "combat", "luck") -> {
            val parts = value.split(Regex("\\s+"))
            val offset = if (parts.firstOrNull()?.equals("s", true) == true) 1 else 0
            if (parts.size > offset + 1) "需要${DetailFormatters.skill(parts[offset])}技能等级 ${parts[offset + 1]}" else "需要对应技能等级"
        }
        value.startsWith("f ", true) -> {
            val parts = value.split(Regex("\\s+"))
            if (parts.size >= 3) "需要与${parts[1]}达到 ${parts[2]} 心友谊" else "原始条件：$value"
        }
        value.equals("default", true) -> "默认解锁条件"
        else -> "原始条件：$value"
    }

    private fun menuName(value: String): String = when (value.lowercase()) {
        "home" -> "家中"
        else -> value
    }

    private fun tagValue(value: String): String = DetailFormatters.contextTag(value)

    private fun isStructuredShop(raw: JsonObject): Boolean =
        raw.objectAt("_provenance").isNotEmpty() || raw.array("Items").any { it.asObject()?.string("Id")?.isNotBlank() == true }

    private fun isQuestItemReference(value: String): Boolean =
        value.toIntOrNull() != null || value.matches(Regex("\\([A-Z]+\\).+"))

    private fun isResolvableItemReference(value: String?): Boolean {
        val item = value?.trim().orEmpty()
        if (item.isBlank() || item.contains("FLAVORED_ITEM", true) || item.contains("DROP_IN", true) || item.contains("NEARBY_FLOWER", true)) return false
        return item.matches(Regex("(?:\\([A-Z]+\\))?[-A-Za-z0-9_:.]+"))
    }

    private fun JsonObject.relation(key: String, label: String) = string(key)?.let { DetailRelation(label, it) }

    private fun JsonObject.fact(key: String, label: String, transform: (JsonPrimitive) -> String? = { it.contentOrNull }) = primitive(key)?.let(transform)?.takeIf(String::isNotBlank)?.let { DetailFact(label, it) }

    private fun JsonObject.string(key: String) = primitive(key)?.contentOrNull

    private fun JsonObject.stringAny(vararg keys: String) = keys.asSequence()
        .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
        .firstOrNull()

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.arrayAny(vararg keys: String) = keys.asSequence().mapNotNull { key -> this[key] as? JsonArray }.firstOrNull() ?: JsonArray(emptyList())

    private fun JsonObject.objectAt(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonElement.asObject() = this as? JsonObject

    private fun JsonElement.asPrimitive() = this as? JsonPrimitive

    private fun JsonElement.asArray() = this as? JsonArray

    private fun JsonArray.texts() = mapNotNull { it.asPrimitive()?.contentOrNull }

    private fun JsonObject.intOrNull(key: String) = string(key)?.toIntOrNull()

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }

    private val RECIPE_TYPES = setOf("cooking_recipe", "crafting_recipe")
}
