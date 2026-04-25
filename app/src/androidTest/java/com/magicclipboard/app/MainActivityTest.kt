package com.magicclipboard.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun showsClipKeeperHome() {
        composeRule.onNodeWithText("Search saved items").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performClick()

        composeRule.onNodeWithText("Saved item behavior").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm before delete").assertIsDisplayed()
        composeRule.onNodeWithText("Ask before deleting a snippet").assertIsDisplayed()
    }
}
