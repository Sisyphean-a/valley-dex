package com.example.stardewoffline.core.json

import com.example.stardewoffline.core.formatter.DetailFormatters
import com.example.stardewoffline.core.model.DetailFact
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object DetailFactParser {
    fun factsFor(type: String, raw: JsonObject, derived: JsonObject, sourceId: String): List<DetailFact> = when (type) {
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

    private fun shopFacts(raw: JsonObject): List<DetailFact> = buildList {
        raw.array("Items").size.takeIf { it > 0 }?.let { add(DetailFact("可售商品", "$it 项")) }
        raw.string("Currency")?.let { currency ->
            DetailFormatters.currency(currency)?.let { add(DetailFact("交易货币", it)) }
        }
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
    private fun isScheduleDirective(value: String): Boolean = value.startsWith("MAIL ") || value.startsWith("GOTO ")

    private fun scheduleDirective(value: String): String? {
        val parts = value.split(Regex("\\s+"), limit = 2)
        return when (parts.firstOrNull()?.uppercase()) {
            "MAIL" -> parts.getOrNull(1)?.let { "触发游戏邮件" }
            "GOTO" -> parts.getOrNull(1)?.let { "跳转到：${DetailFormatters.scheduleRule(it)}" }
            else -> null
        }
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
}
