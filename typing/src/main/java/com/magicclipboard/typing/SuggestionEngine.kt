package com.magicclipboard.typing

import com.magicclipboard.typing.model.LanguageMode
import com.magicclipboard.typing.model.SuggestionCandidate

interface SuggestionEngine {
    fun suggest(
        contextBeforeCursor: String,
        composingText: String,
        languageMode: LanguageMode,
        limit: Int = 3,
    ): List<SuggestionCandidate>

    fun autocorrect(
        token: String,
        languageMode: LanguageMode,
    ): String?

    fun learn(
        token: String,
        languageMode: LanguageMode,
    )

    fun seedLearnedWords(
        languageMode: LanguageMode,
        words: Set<String>,
    )
}

