package com.example.stardewoffline.data.wiki

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
    fun majorGroupsExposeVillagersMonstersAndShopsWhileSupportRecordsStayHidden() {
        val sections = WikiCatalogueConfiguration.sections(
            listOf(
                ManifestEntityType("villager", "村民", 2),
                ManifestEntityType("npc_schedule", "NPC 日程", 20),
                ManifestEntityType("villager_gift", "村民礼物", 2),
                ManifestEntityType("monster", "怪物", 1),
                ManifestEntityType("shop", "商店", 3),
            ),
        )
        val major = sections.first { it.id == "major" }.categories
        assertEquals(setOf("villager", "shop"), major.first { it.id == "community" }.entityTypes)
        assertEquals(setOf("monster"), major.first { it.id == "exploration" }.entityTypes)
        val browsable = sections.filter { it.id.startsWith("catalogue-") }.flatMap { it.categories }
        assertTrue(browsable.any { it.id == "type:villager" })
        assertTrue(browsable.any { it.id == "type:monster" })
        assertTrue(browsable.any { it.id == "type:shop" })
        assertFalse(sections.flatMap { it.categories }.any { category ->
            category.entityTypes.any { it == "npc_schedule" || it == "villager_gift" }
        })
    }

    @Test
    fun everyBrowsableTypeAppearsExactlyOnceInTheDetailedCatalogue() {
        val types = listOf(
            ManifestEntityType("object", "物品", 8),
            ManifestEntityType("villager", "村民", 2),
            ManifestEntityType("monster", "怪物", 1),
            ManifestEntityType("shop", "商店", 3),
            ManifestEntityType("future_type", "未来资料", 4),
            ManifestEntityType("npc_schedule", "NPC 日程", 20),
        )
        val detailedTypes = WikiCatalogueConfiguration.sections(types)
            .filter { it.id.startsWith("catalogue-") }
            .flatMap { it.categories }
            .flatMap { it.entityTypes }
        assertEquals(listOf("future_type", "monster", "object", "shop", "villager").sorted(), detailedTypes.sorted())
        assertEquals(detailedTypes.size, detailedTypes.distinct().size)
    }
}
