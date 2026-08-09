package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.model.ManifestEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WikiCataloguePresentationTest {
    @Test
    fun identicalChineseAndEnglishTitlesAreNotDisplayedTwice() {
        assertNull(englishTitleForDisplay("商店", "商店"))
        assertNull(englishTitleForDisplay("商店", " 商店 "))
        assertEquals("Shop", englishTitleForDisplay("商店", "Shop"))
    }

    @Test
    fun villagersHaveTheirOwnCategoryAndSupportRecordsStayHidden() {
        val sections = WikiCatalogueConfiguration.sections(
            listOf(
                ManifestEntityType("villager", "村民", 2),
                ManifestEntityType("npc_schedule", "NPC 日程", 20),
                ManifestEntityType("villager_gift", "村民礼物", 2),
                ManifestEntityType("monster", "怪物", 1),
            ),
        )
        val featured = sections.first { it.id == "featured" }.categories
        assertEquals(setOf("villager"), featured.first { it.id == "villagers" }.entityTypes)
        assertFalse(featured.any { it.entityTypes.contains("npc_schedule") || it.entityTypes.contains("villager_gift") })
        assertFalse(sections.first { it.id == "all" }.categories.any { category ->
            category.entityTypes.any { it == "npc_schedule" || it == "villager_gift" }
        })
    }
}
