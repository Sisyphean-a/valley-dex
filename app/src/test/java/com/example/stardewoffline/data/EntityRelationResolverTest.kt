package com.example.stardewoffline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityRelationResolverTest {
    @Test
    fun mapsQualifiedGameItemReferenceToItsCompatibleEntityType() {
        assertEquals(listOf("object:495"), relationCandidates("(O)495"))
        assertEquals(listOf("weapon:0"), relationCandidates("(W)0"))
    }

    @Test
    fun keepsQualifiedEntitiesAndProbesUnqualifiedNumericIds() {
        assertEquals(listOf("cooking_recipe:Maki-Roll"), relationCandidates("cooking_recipe:Maki-Roll"))
        assertTrue(relationCandidates("24").take(4).containsAll(listOf("object:24", "crop:24")))
    }
}
