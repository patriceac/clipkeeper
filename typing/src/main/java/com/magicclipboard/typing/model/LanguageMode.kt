package com.magicclipboard.typing.model

enum class LanguageMode(
    val displayName: String,
    val localeTag: String,
) {
    ENGLISH(displayName = "English", localeTag = "en-US"),
    FRENCH(displayName = "Français", localeTag = "fr-FR"),
}

