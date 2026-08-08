package com.example.stardewoffline.core.formatter

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailFormattersTest {
    @Test
    fun formatsKnownSeasonsAndLeavesUnknownValuesIntact() {
        assertEquals("春季", DetailFormatters.season("spring"))
        assertEquals("modded", DetailFormatters.season("modded"))
    }

    @Test
    fun formatsOnlyValidGameTimesAndProbabilityRanges() {
        assertEquals("18:30", DetailFormatters.gameTime(1830))
        assertEquals("1265", DetailFormatters.gameTime(1265))
        assertEquals("25%", DetailFormatters.chance(0.25))
        assertEquals("1.5", DetailFormatters.chance(1.5))
    }

    @Test
    fun preservesUnknownEnumsAndFishAreas() {
        assertEquals("剑", DetailFormatters.weaponType("0"))
        assertEquals("防御剑", DetailFormatters.weaponType("3"))
        assertEquals("未知武器类型（4）", DetailFormatters.weaponType("4"))
        assertEquals("海滩（dock-unknown）", DetailFormatters.location("Beach", "dock-unknown"))
        assertEquals("齐币", DetailFormatters.currency("4"))
    }
}
