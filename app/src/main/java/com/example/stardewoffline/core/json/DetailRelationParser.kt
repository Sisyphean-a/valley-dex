package com.example.stardewoffline.core.json

import com.example.stardewoffline.core.formatter.DetailFormatters
import com.example.stardewoffline.core.model.DetailFact
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.DetailRelationGroup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object DetailRelationParser {
    fun groupsFor(type: String, raw: JsonObject, derived: JsonObject): List<DetailRelationGroup> = buildList {
        when (type) {
            "crop" -> add(cropRelations(derived))
            "fish" -> add(fishRelations(derived))
            "villager" -> add(villagerRelations(derived))
            "monster" -> add(monsterRelations(raw))
            "drop" -> add(dropRelations(raw))
            "shop" -> {
                add(shopOwnerRelations(raw))
                add(shopEntityRelations(raw))
            }
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

    private fun shopOwnerRelations(raw: JsonObject) = DetailRelationGroup("店主", raw.array("Owners").mapNotNull { owner ->
        owner.asObject()?.string("Id")?.takeIf(String::isNotBlank)?.let { DetailRelation("店主", it) }
    })

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
            val details = offerDetails(offer, raw.string("Currency"))
            when {
                isResolvableItemReference(fixed) -> add(DetailRelation("商品", fixed, details))
                random.any(::isResolvableItemReference) -> random.filter(::isResolvableItemReference).forEach { id ->
                    add(DetailRelation("随机商品", id, details))
                }
            }
            offer.string("TradeItemId")?.let { trade ->
                if (isResolvableItemReference(trade)) add(DetailRelation("兑换材料", trade, listOfNotNull(offer.fact("TradeItemAmount", "数量") { DetailFormatters.integer(it.contentOrNull ?: "") })))
            }
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

    private fun offerDetails(offer: JsonObject, defaultCurrency: String? = null): List<DetailFact> {
        val value = offer.stringAny("price", "Price") ?: return emptyList()
        if (value.toIntOrNull() == -1) return emptyList()
        return listOf(DetailFact("购买价格", purchasePrice(offer, value, defaultCurrency)))
    }

    private fun purchasePrice(offer: JsonObject, value: String, defaultCurrency: String?): String {
        val currency = (offer.stringAny("currency", "Currency") ?: defaultCurrency)
            ?.let(DetailFormatters::currency)
        if (currency.isNullOrBlank() || currency == "金币") return DetailFormatters.gold(value) ?: value
        val formatted = DetailFormatters.formatNumber(value) ?: value
        return "$formatted $currency"
    }
    private fun stackFact(value: JsonObject, minKey: String, maxKey: String, label: String): DetailFact? {
        val min = value.stringAny(minKey, minKey.capitalized())?.toIntOrNull()
        val max = value.stringAny(maxKey, maxKey.capitalized())?.toIntOrNull()
        if (min == null && max == null) return null
        if (min == -1 && max == -1) return DetailFact(label, "默认数量")
        val values = listOfNotNull(min?.takeIf { it >= 0 }, max?.takeIf { it >= 0 }).distinct()
        return DetailFact(label, values.joinToString(" - ").ifBlank { "默认数量" })
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
    private fun isStructuredShop(raw: JsonObject): Boolean =
        raw.objectAt("_provenance").isNotEmpty() || raw.array("Items").any { it.asObject()?.string("Id")?.isNotBlank() == true }

    private fun isQuestItemReference(value: String): Boolean =
        value.toIntOrNull() != null || value.matches(Regex("\\([A-Z]+\\).+"))

    private fun isResolvableItemReference(value: String?): Boolean {
        val item = value?.trim().orEmpty()
        if (
            item.isBlank() ||
            item.contains("FLAVORED_ITEM", true) ||
            item.contains("DROP_IN", true) ||
            item.contains("NEARBY_FLOWER", true) ||
            item.startsWith("ALL_ITEMS", true) ||
            item.startsWith("RANDOM_ITEMS", true) ||
            item.startsWith("ITEMS_", true) ||
            item.startsWith("MONSTER_SLAYER_REWARDS", true) ||
            item.startsWith("TOOL_UPGRADES", true) ||
            item.startsWith("MOVIE_CONCESSIONS", true) ||
            item.startsWith("PET_ADOPTION", true) ||
            item.startsWith("LOST_UNIQUE_ITEMS", true) ||
            item.startsWith("DISH_OF_THE_DAY", true)
        ) return false
        return item.matches(SUPPORTED_ITEM_REFERENCE) || item.matches(UNPREFIXED_ITEM_REFERENCE)
    }

    private val SUPPORTED_ITEM_REFERENCE = Regex("\\((O|BC|F|T|TR|W|B)\\).+")
    private val UNPREFIXED_ITEM_REFERENCE = Regex("[A-Za-z0-9_.:-]+")

    private val RECIPE_TYPES = setOf("cooking_recipe", "crafting_recipe")
}
