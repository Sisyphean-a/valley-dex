package com.example.stardewoffline.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.ui.component.EntityListItem
import org.junit.Rule
import org.junit.Test

class EntityListItemTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsPrimaryAndEnglishNames() {
        composeRule.setContent {
            EntityListItem(EntitySummary("object:24", "object", "防风草", "Parsnip", "作物", null, "fang feng cao"), null) {}
        }

        composeRule.onNodeWithText("防风草").assertIsDisplayed()
        composeRule.onNodeWithText("Parsnip").assertIsDisplayed()
    }
}
