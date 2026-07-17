package com.example.stardewoffline.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityIdTest {
    @Test
    fun parsesQualifiedEntityId() {
        val id = EntityId("crop:24")

        assertEquals("crop", id.typePrefix)
        assertEquals("24", id.sourceId)
    }

    @Test
    fun rejectsUnqualifiedParts() {
        val id = EntityId("24")

        assertNull(id.typePrefix)
        assertNull(id.sourceId)
    }
}
