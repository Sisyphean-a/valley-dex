package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.TranslationStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VillagerSupportPresentationTest {
    @Test
    fun leoMainlandMatchesLeoButTemplateDoesNot() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Leo",
            schedules = listOf(
                entity("npc_schedule:LeoMainland:spring", "npc_schedule", "[\"sunny\",\"a1200 Town\"]"),
                entity("npc_schedule:template:spring", "npc_schedule", "[\"a1300\",\"Town\"]"),
            ),
            gifts = emptyList(),
        )
        assertEquals(1, support.schedules.size)
        assertEquals("春季", support.schedules.single().group)
        assertEquals("默认日程", support.schedules.single().label)
    }

    @Test
    fun oneScheduleRecordKeepsAllTimesAndHidesMailToken() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = listOf(entity("npc_schedule:Abigail:Wed", "npc_schedule", "[\"sunny\",\"MAIL internal_event_42\",\"GOTO spring\",\"GOTO NO_SCHEDULE\",\"GOTO Sun_normal\",\"a1200 Town 1 2\",\"1700 Saloon 3 4\"]")),
            gifts = emptyList(),
        )
        assertEquals(1, support.schedules.size)
        assertEquals("通用日期", support.schedules.single().group)
        assertEquals("周三", support.schedules.single().label)
        val details = support.schedules.single().details
        assertEquals(1, details.count { it.label == "日程条件" })
        assertEquals(2, details.count { it.label == "时间" })
        assertTrue(details.any { it.value == "触发游戏邮件" })
        assertTrue(details.none { it.value.contains("internal_event_42") })
        assertTrue(details.any { it.value == "跳转到：春季默认日程" })
        assertTrue(details.any { it.value == "跳转到：无日程" })
        assertTrue(details.any { it.value == "跳转到：周日常规日程" })
        assertTrue(details.none { it.value.contains("NO_SCHEDULE") || it.value.contains("Sun_normal") })
    }

    @Test
    fun weekdayFriendshipAndMarriageRulesHaveDedicatedGroups() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = listOf(
                entity("npc_schedule:Abigail:Fri_6", "npc_schedule", "[\"900 Town\"]"),
                entity("npc_schedule:Abigail:marriageJob", "npc_schedule", "[\"900 Town\"]"),
            ),
            gifts = emptyList(),
        )
        assertEquals(listOf("通用日期", "婚后日程"), support.schedules.map { it.group })
        assertEquals("周五（友谊等级6）", support.schedules[0].label)
        assertEquals("婚后工作日程", support.schedules[1].label)
    }

    @Test
    fun scheduleKeysAreGroupedBySeasonAndDateInsteadOfFirstLocation() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = listOf(
                entity("npc_schedule:Abigail:spring", "npc_schedule", "[\"900 Town\"]"),
                entity("npc_schedule:Abigail:spring_11", "npc_schedule", "[\"1000 Hospital\"]"),
                entity("npc_schedule:Abigail:spring_4", "npc_schedule", "[\"1030 Hospital\"]"),
                entity("npc_schedule:Abigail:winter_15", "npc_schedule", "[\"1100 Beach\"]"),
                entity("npc_schedule:Abigail:rain", "npc_schedule", "[\"1200 SeedShop\"]"),
            ),
            gifts = emptyList(),
        )
        assertEquals(listOf("春季", "春季", "春季", "冬季", "天气与节日"), support.schedules.map { it.group })
        assertEquals(listOf("默认日程", "第4天", "第11天", "第15天", "下雨天"), support.schedules.map { it.label })
    }

    @Test
    fun legacyValueKeepsEmptyGiftSlots() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = emptyList(),
            gifts = listOf(entityWithExtra("villager_gift:Abigail", "villager_gift", "{\"legacyValue\":\"Abigail/(O)74//category_fruits//category_fish//-1//category_gem\"}")),
        )
        assertTrue(support.gifts.getValue("最爱").single().itemId!!.contains("(O)74"))
        assertTrue(support.gifts.getValue("喜欢").single().readableLabel == "水果")
        assertTrue(support.gifts.getValue("一般").single().readableLabel == "鱼")
        assertTrue(support.gifts.getValue("不喜欢").single().readableLabel == "任意采集物")
        assertTrue(support.gifts.getValue("讨厌").single().readableLabel == "宝石")
    }

    @Test
    fun legacyFieldsKeepsEmptyArraySlots() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = emptyList(),
            gifts = listOf(entityWithExtra("villager_gift:Abigail", "villager_gift", "{\"legacyFields\":[\"Abigail\",\"\",\"\",\"category_fruits\",\"\",\"\",\"\",\"\",\"\",\"\"]}")),
        )
        assertTrue(support.gifts.getValue("最爱").isEmpty())
        assertEquals("水果", support.gifts.getValue("喜欢").single().readableLabel)
    }

    @Test
    fun nonStringLegacyFieldKeepsItsSlotAndDoesNotAbortVillagerPresentation() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = emptyList(),
            gifts = listOf(entityWithExtra("villager_gift:Abigail", "villager_gift", "{\"legacyFields\":[\"Abigail\",{},\"unused\",\"category_fruits\",\"unused\",\"\",\"unused\",\"\",\"unused\",\"\"]}")),
        )
        assertTrue(support.gifts.getValue("最爱").isEmpty())
        assertEquals("水果", support.gifts.getValue("喜欢").single().readableLabel)
    }

    @Test
    fun giftsKeepFiveGroupsAndReadableRanges() {
        val support = VillagerSupportPresentationBuilder.build(
            sourceId = "Abigail",
            schedules = emptyList(),
            gifts = listOf(entity("villager_gift:Abigail", "villager_gift", "[\"Abigail\",\"(O)74\",\"unused\",\"category_fruits\",\"unused\",\"category_fish\",\"unused\",\"-1\",\"unused\",\"category_gem\"]")),
        )
        assertEquals(listOf("最爱", "喜欢", "一般", "不喜欢", "讨厌"), support.gifts.keys.toList())
        assertEquals("任意采集物", support.gifts.getValue("不喜欢").single().readableLabel)
        assertTrue(support.gifts.getValue("最爱").single().itemId!!.contains("(O)"))
        assertEquals("水果", support.gifts.getValue("喜欢").single().readableLabel)
    }

    private fun entity(id: String, type: String, fields: String) = entityWithExtra(
        id, type, "{\"legacyFields\":$fields}",
    )

    private fun entityWithExtra(id: String, type: String, extra: String) = EntityDetail(
        id = id, entityType = type, gameId = null, internalName = null,
        nameZh = "测试", nameEn = null, descriptionZh = null, descriptionEn = null,
        category = null, translationStatus = TranslationStatus.COMPLETE, imagePath = null,
        extraJson = Json.parseToJsonElement(extra).jsonObject,
        sourceFile = null, createdAt = "2026-01-01T00:00:00Z",
    )
}
