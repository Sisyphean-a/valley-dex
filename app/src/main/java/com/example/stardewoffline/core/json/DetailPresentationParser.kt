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
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object DetailPresentationParser {
    fun present(entity: EntityDetail): DetailPresentation {
        val derived = entity.extraJson.objectAt("officialDerived")
        val facts = factsFor(entity.entityType, entity.extraJson, derived)
        val groups = groupsFor(entity.entityType, derived, entity.extraJson)
        return DetailPresentation(facts, groups.filter { it.relations.isNotEmpty() }, entity.extraJson.toString())
    }

    private fun factsFor(type: String, root: JsonObject, derived: JsonObject) = when (type) {
        "object", "mineral", "ring" -> itemFacts(derived)
        "crop" -> cropFacts(derived)
        "fish" -> fishFacts(derived)
        "villager" -> villagerFacts(derived)
        "weapon", "tool", "footwear", "trinket" -> equipmentFacts(root)
        "shop" -> shopFacts(root)
        "drop" -> listOfNotNull(root.fact("chance", "概率") { DetailFormatters.chance(it.doubleOrNull ?: return@fact null) })
        else -> genericFacts(derived, root)
    }

    private fun groupsFor(type: String, derived: JsonObject, root: JsonObject) = buildList {
        if (type == "crop") add(cropRelations(derived))
        if (type == "fish") add(fishRelations(derived))
        if (type == "villager") add(villagerRelations(derived))
        if (type in RECIPE_TYPES) add(recipeRelations(derived))
        if (type == "drop") add(dropRelations(root))
        if (type == "shop") add(shopItems(root))
        add(shopRelations(derived))
        add(machineRelations(derived))
        add(usedInRelations(derived))
    }

    private fun itemFacts(derived: JsonObject) = listOfNotNull(
        derived.fact("sellPrice", "售价"),
        derived.fact("edibility", "食用值"),
        derived.array("contextTags").takeIf { it.isNotEmpty() }?.let { DetailFact("技术标签", it.texts().joinToString("、")) },
    )

    private fun cropFacts(derived: JsonObject) = listOfNotNull(
        derived.array("seasons").takeIf { it.isNotEmpty() }?.let { DetailFact("季节", DetailFormatters.seasons(it.texts())) },
        derived.fact("growDays", "总生长天数"),
        derived.array("growthPhases").takeIf { it.isNotEmpty() }?.let { DetailFact("生长阶段", it.texts().joinToString(" + ", postfix = " 天")) },
        derived.fact("regrowDays", "再生天数"),
        derived.fact("needsWatering", "需要浇水") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
        derived.fact("isPaddyCrop", "水稻作物") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
        derived.fact("isTrellisCrop", "需要棚架") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
        harvestRange(derived),
    )

    private fun fishFacts(derived: JsonObject) = listOfNotNull(
        derived.fact("difficulty", "难度"),
        derived.fact("behavior", "行为代号"),
        sizeRange(derived),
        derived.array("seasons").takeIf { it.isNotEmpty() }?.let { DetailFact("季节", DetailFormatters.seasons(it.texts())) },
        derived.fact("weather", "天气"),
        timeWindows(derived),
    )

    private fun villagerFacts(derived: JsonObject) = listOfNotNull(
        birthday(derived),
        derived.fact("homeRegion", "居住区域"),
        derived.fact("gender", "性别"),
        derived.fact("canBeRomanced", "可婚配") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
    )

    private fun equipmentFacts(root: JsonObject) = listOfNotNull(
        valueRange(root, "MinDamage", "MaxDamage", "伤害"),
        root.fact("Defense", "防御"), root.fact("Speed", "速度"), root.fact("CritChance", "暴击率"),
        root.fact("SalePrice", "售价"), root.fact("AttachmentSlots", "附件槽"),
        root.fact("CanBeLostOnDeath", "死亡时可丢失") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
    )

    private fun shopFacts(root: JsonObject) = listOfNotNull(
        root.fact("Id", "商店 ID"), root.fact("Currency", "货币"), root.fact("PriceModifierMode", "价格修正规则"),
        root.array("Owners").takeIf { it.isNotEmpty() }?.let { owners ->
            DetailFact("所有者", owners.mapNotNull { it.jsonObject.string("Name") ?: it.jsonObject.string("Id") }.joinToString("、"))
        },
    )

    private fun genericFacts(derived: JsonObject, root: JsonObject) = listOfNotNull(
        derived.fact("sellPrice", "售价"), derived.fact("edibility", "食用值"),
        root.fact("Price", "售价"), root.fact("Requester", "委托人"), root.fact("Duration", "持续时间"),
        root.fact("OrderType", "订单类型"), root.fact("Count", "目标数量"),
        root.fact("Repeatable", "可重复") { DetailFormatters.bool(it.booleanOrNull ?: return@fact null) },
    ).distinctBy(DetailFact::label)

    private fun cropRelations(derived: JsonObject) = DetailRelationGroup("种植与收获", listOfNotNull(
        derived.relation("seedItemId", "种子"), derived.relation("harvestItemId", "收获物"),
        *shopOffers(derived.array("seedShopOffers"), "种子购买来源").toTypedArray(),
    ))

    private fun fishRelations(derived: JsonObject) = DetailRelationGroup("出现与养殖", buildList {
        derived.array("locations").forEach { location -> add(locationRelation(location)) }
        derived.array("fishPondRules").forEach { rule -> add(fishPondRelation(rule)) }
    })

    private fun villagerRelations(derived: JsonObject) = DetailRelationGroup("人物关系", listOfNotNull(derived.relation("loveInterest", "恋爱对象")))

    private fun recipeRelations(derived: JsonObject) = DetailRelationGroup("配方", buildList {
        derived.array("ingredients").forEach { ingredient -> add(ingredientRelation(ingredient)) }
        addAll(listOfNotNull(derived.relation("outputItemId", "产物")))
    })

    private fun dropRelations(root: JsonObject) = DetailRelationGroup("掉落关系", listOfNotNull(
        root.relation("monsterId", "怪物"), root.relation("itemId", "物品"),
    ))

    private fun shopRelations(derived: JsonObject) = DetailRelationGroup("商店来源", shopOffers(derived.array("shopOffers"), "商店"))

    private fun shopItems(root: JsonObject) = DetailRelationGroup("商品列表", root.array("Items").map { element ->
        val item = element.jsonObject
        DetailRelation("商品", item.string("ItemId"), listOfNotNull(
            item.fact("Price", "价格"), item.fact("TradeItemId", "交易物品"), item.fact("TradeItemAmount", "交易数量"),
            item.fact("AvailableStock", "库存"),
            DetailFormatters.condition(item.string("Condition"))?.let { DetailFact("条件", it) },
        ))
    })

    private fun machineRelations(derived: JsonObject) = DetailRelationGroup("机器用途", derived.array("machineUses").map(::machineRelation))

    private fun usedInRelations(derived: JsonObject) = DetailRelationGroup("被用于", derived.array("usedIn").map(::usedInRelation))

    private fun shopOffers(items: JsonArray, label: String) = items.map { item ->
        val offer = item.jsonObject
        DetailRelation(label, offer.string("shopId"), listOfNotNull(
            offer.fact("price", "价格"), offer.fact("currency", "货币"),
            offer.string("tradeItemId")?.let { DetailFact("交易物品", it) },
            DetailFormatters.condition(offer.string("condition"))?.let { DetailFact("条件", it) },
        ))
    }

    private fun locationRelation(element: JsonElement): DetailRelation {
        val item = element.jsonObject
        return DetailRelation("地点", null, listOfNotNull(
            item.string("locationId")?.let { DetailFact("地点", it) }, item.string("areaId")?.let { DetailFact("区域", it) },
            item.string("season")?.let { DetailFact("季节", DetailFormatters.season(it)) },
            item["chance"]?.jsonPrimitive?.doubleOrNull?.let { DetailFact("概率", DetailFormatters.chance(it)) },
            item.fact("minFishingLevel", "最低钓鱼等级"),
            DetailFormatters.condition(item.string("condition"))?.let { DetailFact("条件", it) },
        ))
    }

    private fun fishPondRelation(element: JsonElement): DetailRelation {
        val item = element.jsonObject
        return DetailRelation("鱼塘规则", null, listOfNotNull(
            item.string("ruleId")?.let { DetailFact("规则", it) }, item.fact("maxPopulation", "最大数量"), item.fact("spawnTime", "繁殖时间"),
        ))
    }

    private fun ingredientRelation(element: JsonElement): DetailRelation {
        val item = element.jsonObject
        return DetailRelation("原料", item.string("itemId"), listOfNotNull(item.fact("quantity", "数量")))
    }

    private fun machineRelation(element: JsonElement): DetailRelation {
        val item = element.jsonObject
        return DetailRelation("机器", item.string("machineId"), listOfNotNull(
            item.fact("requiredCount", "所需数量"), item.fact("minutesUntilReady", "完成分钟"), item.fact("daysUntilReady", "完成天数"),
            DetailFormatters.condition(item.string("condition"))?.let { DetailFact("条件", it) },
        ))
    }

    private fun usedInRelation(element: JsonElement): DetailRelation {
        val item = element.jsonObject
        return DetailRelation(item.string("usageType") ?: "用途", item.string("usageId"), listOfNotNull(item.fact("quantity", "数量")))
    }

    private fun birthday(derived: JsonObject): DetailFact? {
        val birthday = derived.objectAt("birthday")
        val day = birthday["day"]?.jsonPrimitive?.intOrNull ?: return null
        val season = birthday.string("season")?.let(DetailFormatters::season)
        return DetailFact("生日", listOfNotNull(season, day.takeIf { it > 0 }?.toString()).joinToString(" ").ifBlank { "未知" })
    }

    private fun harvestRange(derived: JsonObject): DetailFact? = valueRange(derived, "harvestMin", "harvestMax", "收获数量")

    private fun sizeRange(derived: JsonObject): DetailFact? = valueRange(derived, "minSize", "maxSize", "尺寸")

    private fun timeWindows(derived: JsonObject): DetailFact? {
        val times = derived.array("timeWindows").mapNotNull { it.jsonPrimitive.intOrNull }
        return times.chunked(2).takeIf { it.isNotEmpty() }?.let { DetailFact("时间段", it.joinToString("、") { pair -> pair.joinToString(" - ", transform = DetailFormatters::gameTime) }) }
    }

    private fun valueRange(objectValue: JsonObject, min: String, max: String, label: String): DetailFact? {
        val values = listOfNotNull(objectValue.string(min), objectValue.string(max))
        return values.takeIf { it.isNotEmpty() }?.let { DetailFact(label, it.distinct().joinToString(" - ")) }
    }

    private fun JsonObject.relation(key: String, label: String) = string(key)?.let { DetailRelation(label, it) }
    private fun JsonObject.fact(key: String, label: String, transform: (JsonPrimitive) -> String? = { it.contentOrNull }) = this[key]?.jsonPrimitive?.let(transform)?.let { DetailFact(label, it) }
    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.array(key: String) = this[key]?.jsonArray ?: JsonArray(emptyList())
    private fun JsonObject.objectAt(key: String) = this[key]?.jsonObject ?: JsonObject(emptyMap())
    private fun JsonArray.texts() = mapNotNull { it.jsonPrimitive.contentOrNull }

    private val RECIPE_TYPES = setOf("cooking_recipe", "crafting_recipe")
}
