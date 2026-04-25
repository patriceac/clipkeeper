package com.magicclipboard.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionWindowTest {
    @Test
    fun `label uses hours for short retention windows`() {
        assertEquals("1 hour", retentionWindowLabel(1))
        assertEquals("3 hours", retentionWindowLabel(3))
        assertEquals("12 hours", retentionWindowLabel(12))
    }

    @Test
    fun `label uses days starting at twenty four hours`() {
        assertEquals("1 day", retentionWindowLabel(24))
        assertEquals("2 days", retentionWindowLabel(48))
        assertEquals("7 days", retentionWindowLabel(168))
    }

    @Test
    fun `slider exposes staggered retention presets`() {
        assertEquals(1, retentionHoursFromSliderValue(0f))
        assertEquals(3, retentionHoursFromSliderValue(1f))
        assertEquals(6, retentionHoursFromSliderValue(2f))
        assertEquals(12, retentionHoursFromSliderValue(3f))
        assertEquals(24, retentionHoursFromSliderValue(4f))
        assertEquals(48, retentionHoursFromSliderValue(5f))
        assertEquals(72, retentionHoursFromSliderValue(6f))
        assertEquals(120, retentionHoursFromSliderValue(7f))
        assertEquals(168, retentionHoursFromSliderValue(8f))
    }

    @Test
    fun `saved values position to nearest retention preset`() {
        assertEquals(0f, retentionSliderPosition(1))
        assertEquals(1f, retentionSliderPosition(4))
        assertEquals(5f, retentionSliderPosition(49))
        assertEquals(8f, retentionSliderPosition(168))
    }
}
