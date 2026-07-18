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

    private fun entity(type: String, extra: String) = EntityDetail(
        id = "$type:test", entityType = type, gameId = null, internalName = null,
        nameZh = "测试", nameEn = null, descriptionZh = null, descriptionEn = null,
        category = null, translationStatus = TranslationStatus.COMPLETE,
        imagePath = null, extraJson = Json.parseToJsonElement(extra).jsonObject,
        sourceFile = null, createdAt = "2026-01-01T00:00:00Z",
    )
}
