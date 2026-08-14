package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.model.ManifestEntityType
import com.example.stardewoffline.core.model.ShopKind
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
    fun typedFacetValuesAreUsedAsBrowseFilters() {
        val summary = com.example.stardewoffline.core.model.Schema5EntitySummary(
            id = "crop:test",
            entityType = "crop",
            gameId = "test",
            internalName = null,
            nameZh = "测试",
            nameEn = null,
            descriptionZh = null,
            descriptionEn = null,
            category = null,
            translationStatus = com.example.stardewoffline.core.model.TranslationStatus.COMPLETE,
            card = com.example.stardewoffline.core.model.Schema5EntityCard(
                entityId = "crop:test",
                identitySummary = null,
                actionSummary1 = null,
                actionSummary2 = null,
                categoryLabel = null,
                sortKey = "测试",
            ),
            visual = null,
            facets = emptyList(),
        )
        assertTrue(browseFiltersFor(summary).isEmpty())
    }
}
