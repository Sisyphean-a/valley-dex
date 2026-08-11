package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.model.ManifestEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiCataloguePresentationTest {
    @Test
    fun identicalChineseAndEnglishTitlesAreNotDisplayedTwice() {
        assertNull(englishTitleForDisplay("商店", "商店"))
        assertNull(englishTitleForDisplay("商店", " 商店 "))
        assertEquals("Shop", englishTitleForDisplay("商店", "Shop"))
    }

    @Test
    fun allBrowsableTypesAppearOnceInCatalogueSubgroupsWithoutMajorNavigation() {
        val sections = WikiCatalogueConfiguration.sections(
            listOf(
                ManifestEntityType("villager", "村民", 2),
                ManifestEntityType("npc_schedule", "NPC 日程", 20),
                ManifestEntityType("villager_gift", "村民礼物", 2),
                ManifestEntityType("monster", "怪物", 1),
                ManifestEntityType("shop", "商店", 3),
                ManifestEntityType("future_type", "未来资料", 4),
            ),
        )
        assertEquals(listOf("catalogue-community", "catalogue-exploration", "catalogue-other"), sections.map { it.id })
        val browsable = sections.flatMap { it.categories }
        assertEquals(listOf("future_type", "monster", "shop", "villager"), browsable.map { it.id.removePrefix("type:") }.sorted())
        assertFalse(browsable.any { category ->
            category.entityTypes.any { it == "npc_schedule" || it == "villager_gift" }
        })
    }

    @Test
    fun cropVillagerAndShopFiltersUseKnownGameData() {
        assertEquals(
            setOf("春季", "夏季"),
            browseFiltersFor(summary("crop", "{\"officialDerived\":{\"seasons\":[\"spring\",\"summer\"]}}")),
        )
        assertEquals(
            setOf("可结婚女性村民"),
            browseFiltersFor(summary("villager", "{\"officialDerived\":{\"canBeRomanced\":true,\"gender\":\"Female\"}}")),
        )
        assertEquals(
            setOf("不可结婚村民"),
            browseFiltersFor(summary("villager", "{\"officialDerived\":{\"canBeRomanced\":false}}")),
        )
        assertEquals(setOf("节日商店"), browseFiltersFor(summary("shop", "{}", id = "shop:EggFestival")))
        assertEquals(setOf("普通商店"), browseFiltersFor(summary("shop", "{}", id = "shop:SeedShop")))
    }

    private fun summary(type: String, extraJson: String, id: String = "$type:test") = EntitySummary(
        id = id,
        entityType = type,
        nameZh = "测试",
        nameEn = null,
        category = null,
        imagePath = null,
        sortKey = null,
        extraJson = extraJson,
    )
}
