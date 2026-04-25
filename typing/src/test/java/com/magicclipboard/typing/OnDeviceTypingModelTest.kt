package com.magicclipboard.typing

import com.magicclipboard.typing.model.GestureTrace
import com.magicclipboard.typing.model.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceTypingModelTest {
    private val model = OnDeviceTypingModel()

    @Test
    fun `suggest returns matching prefix candidates`() {
        val suggestions = model.suggest(
            contextBeforeCursor = "",
            composingText = "cl",
            languageMode = LanguageMode.FRENCH,
        )

        assertTrue(suggestions.first().text.lowercase().startsWith("cl"))
    }

    @Test
    fun `autocorrect fixes a close misspelling`() {
        val corrected = model.autocorrect(
            token = "clipoard",
            languageMode = LanguageMode.ENGLISH,
        )

        assertEquals("clipboard", corrected)
    }

    @Test
    fun `learned words outrank stock lexicon`() {
        model.learn("codexer", LanguageMode.ENGLISH)

        val suggestions = model.suggest(
            contextBeforeCursor = "",
            composingText = "co",
            languageMode = LanguageMode.ENGLISH,
        )

        assertEquals("codexer", suggestions.first().text.lowercase())
    }

    @Test
    fun `gesture decoder resolves an ordered signature`() {
        val decoded = model.decode(
            trace = GestureTrace(
                letters = listOf('c', 'l', 'i', 'p', 'b', 'o', 'a', 'r', 'd'),
                elapsedMs = 420,
            ),
            languageMode = LanguageMode.ENGLISH,
        )

        assertEquals("clipboard", decoded.first().text)
    }

    @Test
    fun `gesture decoder tolerates extra path letters`() {
        val decoded = model.decode(
            trace = GestureTrace(
                letters = listOf('c', 'v', 'l', 'i', 'p', 'b', 'n', 'o', 'a', 'r', 'd'),
                elapsedMs = 510,
            ),
            languageMode = LanguageMode.ENGLISH,
        )

        assertEquals("clipboard", decoded.first().text)
    }

    @Test
    fun `gesture decoder tolerates collapsed repeated letters`() {
        val decoded = model.decode(
            trace = GestureTrace(
                letters = listOf('h', 'e', 'l', 'o'),
                elapsedMs = 360,
            ),
            languageMode = LanguageMode.ENGLISH,
        )

        assertEquals("hello", decoded.first().text)
    }
}
