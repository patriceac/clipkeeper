package com.magicclipboard.typing

import com.magicclipboard.typing.model.GestureTrace
import com.magicclipboard.typing.model.LanguageMode
import com.magicclipboard.typing.model.SuggestionCandidate

interface GestureDecoder {
    fun decode(
        trace: GestureTrace,
        languageMode: LanguageMode,
        limit: Int = 3,
    ): List<SuggestionCandidate>
}

