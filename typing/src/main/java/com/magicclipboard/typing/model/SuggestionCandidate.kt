package com.magicclipboard.typing.model

data class SuggestionCandidate(
    val text: String,
    val score: Float,
    val isAutocorrect: Boolean = false,
)

