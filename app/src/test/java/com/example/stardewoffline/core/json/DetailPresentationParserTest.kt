package com.example.stardewoffline.core.json

import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.TranslationStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPresentationParserTest {
    @Test
    fun mapsCropFieldsAndRelationsWithoutGuessingMissingValues() {
        val presentation = DetailPresentationParser.present(entity("crop", """
            {"officialDerived":{"seasons":["spring"],"growDays":7,"needsWatering":true,"seedItemId":"495","harvestItemId":"16"}}
        """))

        assertTrue(presentation.facts.any { it.label == "季节" && it.value == "春季" })
        assertTrue(presentation.facts.any { it.label == "需要浇水" && it.value == "是" })
        assertEquals(listOf("495", "16"), presentation.relationGroups.single().relations.mapNotNull { it.targetId }.take(2))
    }

    @Test
    fun mapsRecipeIngredientsAndOutput() {
        val presentation = DetailPresentationParser.present(entity("cooking_recipe", """
            {"officialDerived":{"ingredients":[{"itemId":"153","quantity":4}],"outputItemId":"456"}}
        """))

        val recipe = presentation.relationGroups.single { it.title == "配方" }
        assertEquals("153", recipe.relations.first().targetId)
        assertEquals("4", recipe.relations.first().details.single().value)
        assertEquals("456", recipe.relations.last().targetId)
    }

    @Test
    fun ignoresUnknownShopRuntimeFields() {
        val presentation = DetailPresentationParser.present(entity("shop", """
            {"Currency":0,"Owners":[{"Name":"Willy"}],"Items":[{"ItemId":"(O)219","Price":250,"Condition":"SEASON summer"}]}
        """))

        assertTrue(presentation.facts.isEmpty())
        assertTrue(presentation.relationGroups.isEmpty())
    }

    @Test
    fun mapsFishLocationTimeWeatherAndChance() {
        val presentation = DetailPresentationParser.present(entity("fish", """
            {"officialDerived":{"difficulty":80,"behavior":"floater","minSize":1,"maxSize":36,"timeWindows":[600,2000],"seasons":["summer"],"weather":"sunny","locations":[{"locationId":"Beach","areaId":"east-pier","chance":0.18,"minFishingLevel":5}],"fishPondRules":[{"ruleId":"Default","requiredTags":["fish_ocean"],"maxPopulation":-1,"spawnTime":-1,"producedItems":[{"itemId":"(O)812","requiredPopulation":5,"chance":0.7,"minStack":-1,"maxStack":-1}],"populationGates":{"4":["(O)536"]}}]}}
        """))

        assertTrue(presentation.facts.any { it.label == "天气" && it.value == "晴天" })
        assertTrue(presentation.facts.any { it.label == "时间段" && it.value == "06:00 - 20:00" })
        val location = presentation.relationGroups.single { it.title == "出现与养殖" }.relations.first { it.label == "地点" }
        assertTrue(location.details.any { it.value == "海滩（码头东侧）" })
        assertTrue(location.details.any { it.label == "出现概率" && it.value == "18%" })
        val pond = presentation.relationGroups.single { it.title == "出现与养殖" }.relations
        assertTrue(pond.any { it.label == "鱼塘产出" && it.details.any { detail -> detail.label == "最低人口" && detail.value == "5" } })
        assertTrue(pond.any { it.label == "人口门槛" && it.details.any { detail -> detail.value == "(O)536" } })
    }

    @Test
    fun mapsWeaponRawNumbersToGameStats() {
        val presentation = DetailPresentationParser.present(entity("weapon", """
            {"MinDamage":9,"MaxDamage":16,"Knockback":1.5,"Speed":-8,"Precision":0,"Defense":0,"Type":2,"AreaOfEffect":0,"CritChance":0.02,"CritMultiplier":3.0,"MineBaseLevel":32,"MineMinLevel":-1,"CanBeLostOnDeath":true}
        """))

        assertTrue(presentation.facts.any { it.label == "伤害" && it.value == "9 - 16" })
        assertTrue(presentation.facts.any { it.label == "武器类型" && it.value == "棍棒" })
        assertTrue(presentation.facts.any { it.label == "暴击率" && it.value == "2%" })
        assertTrue(presentation.facts.any { it.label == "矿井基础层" && it.value == "第 32 层" })
        assertTrue(presentation.facts.any { it.label == "矿井最低层" && it.value == "不适用" })
    }

    @Test
    fun mapsOfficialShopOffersAndMachineDuration() {
        val shop = DetailPresentationParser.present(entity("shop", """
            {"Id":"SeedShop","Currency":0,"Items":[{"Id":"(O)472","ItemId":"(O)472","Price":-1,"AvailableStock":-1,"AvailableStockLimit":"Global","Condition":"SEASON spring","MinStack":-1,"MaxStack":-1,"Quality":-1}]}
        """))
        val offer = shop.relationGroups.single { it.title == "商品" }.relations.single()
        assertEquals("(O)472", offer.targetId)
        assertTrue(offer.details.any { it.label == "价格" && it.value == "按物品基础价格" })
        assertTrue(offer.details.any { it.label == "库存" && it.value == "不限库存，全局库存" })

        val machine = DetailPresentationParser.present(entity("object", """
            {"officialDerived":{"machineUses":[{"machineId":"(BC)12","minutesUntilReady":6000,"daysUntilReady":-1,"outputs":[{"outputMethod":"StardewValley.Object, Stardew Valley: OutputSeedMaker","minStack":-1,"maxStack":-1,"quality":-1}]}]}}
        """))
        val use = machine.relationGroups.single { it.title == "机器用途" }.relations.single()
        assertTrue(use.details.any { it.label == "完成时间" && it.value == "4天 4小时" })
        assertTrue(use.details.any { it.label == "产出规则" && it.value == "1 条" })
    }

    @Test
    fun mapsMonsterLegacyDropNumbers() {
        val presentation = DetailPresentationParser.present(entity("monster", """
            {"legacyFields":["24","5","0","0","false","1000","766 .75 8 .1 -153 .2","1",".01","4","2",".00","true"]}
        """))

        assertTrue(presentation.facts.any { it.label == "生命值" && it.value == "24" })
        val drops = presentation.relationGroups.single { it.title == "怪物掉落" }.relations
        assertTrue(drops.any { it.targetId == "766" && it.details.any { detail -> detail.value == "75%" } })
        assertTrue(drops.any { it.targetId == "153" && it.details.any { detail -> detail.label == "数量" && detail.value == "1 - 3" } })
    }

    @Test
    fun mapsFurnitureLegacyDimensionsAndPrice() {
        val presentation = DetailPresentationParser.present(entity("furniture", """
            {"legacyFields":["Oak Chair","chair","-1","1 1","4","350","-1","Oak Chair"]}
        """))

        assertTrue(presentation.facts.any { it.label == "家具类型" && it.value == "椅子" })
        assertTrue(presentation.facts.any { it.label == "外观尺寸" && it.value == "按家具类型决定" })
        assertTrue(presentation.facts.any { it.label == "碰撞尺寸" && it.value == "1 × 1 格" })
        assertTrue(presentation.facts.any { it.label == "基础价格" && it.value == "350 金" })
    }

    @Test
    fun mapsBundleRewardRequirementsAndScheduleTimes() {
        val bundle = DetailPresentationParser.present(entity("bundle", """
            {"legacyFields":["Spring Crops","O 465 20","24 1 0 188 1 0","0","","","Spring Crops"]}
        """))
        assertTrue(bundle.facts.any { it.label == "奖励" && it.value == "O 465 20" })
        assertTrue(bundle.relationGroups.single { it.title == "收集包要求" }.relations.any { it.targetId == "(O)465" })
        assertTrue(bundle.relationGroups.single { it.title == "收集包要求" }.relations.any { it.targetId == "24" })

        val schedule = DetailPresentationParser.present(entity("npc_schedule", """
            {"legacyFields":["GOTO spring","MAIL internal_event_42","a1200 SeedShop 1 9 3","1700 Saloon 4 5 2"]}
        """))
        assertTrue(schedule.facts.any { it.label == "日程指令" && it.value.contains("春季") })
        assertTrue(schedule.facts.any { it.label == "日程指令" && it.value.contains("触发游戏邮件") })
        assertTrue(schedule.facts.none { it.value.contains("internal_event_42") })
        assertTrue(schedule.facts.any { it.label == "日程" && it.value.contains("12:00") })
        assertEquals(2, schedule.relationGroups.single { it.title == "日程地点" }.relations.size)
    }

    @Test
    fun mapsLegacyQuestAndIslandEventConditions() {
        val quest = DetailPresentationParser.present(entity("quest", """
            {"legacyFields":["ItemDelivery","Pam Is Thirsty","description","Bring Pam a pale ale.","Pam (O)303","-1","350","reward text","true"]}
        """))
        assertTrue(quest.facts.any { it.label == "任务类型" && it.value == "物品递送" })
        assertTrue(quest.facts.any { it.label == "金币奖励" && it.value == "350 金" })
        assertTrue(quest.relationGroups.single { it.title == "任务关联" }.relations.any { it.targetId == "(O)303" })

        val island = DetailPresentationParser.present(EntityDetail(
            id = "ginger_island:IslandNorth:6497421/e-6497423/f-Leo-1000/w-sunny/t-600-1800/Hl-leoMoved",
            entityType = "ginger_island", gameId = null, internalName = null,
            nameZh = "测试", nameEn = null, descriptionZh = null, descriptionEn = null,
            category = null, translationStatus = TranslationStatus.COMPLETE,
            imagePath = null, extraJson = Json.parseToJsonElement("{}").jsonObject,
            sourceFile = null, createdAt = "2026-01-01T00:00:00Z",
        ))
        assertTrue(island.facts.any { it.label == "地点" && it.value == "姜岛北部" })
        assertTrue(island.facts.any { it.label == "天气" && it.value == "晴天" })
        assertTrue(island.facts.any { it.label == "时间段" && it.value == "06:00 - 18:00" })
    }

    private fun entity(type: String, extra: String) = EntityDetail(
        id = "$type:test", entityType = type, gameId = null, internalName = null,
        nameZh = "测试", nameEn = null, descriptionZh = null, descriptionEn = null,
        category = null, translationStatus = TranslationStatus.COMPLETE,
        imagePath = null, extraJson = Json.parseToJsonElement(extra).jsonObject,
        sourceFile = null, createdAt = "2026-01-01T00:00:00Z",
    )
}
