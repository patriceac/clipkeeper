package com.magicclipboard.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val retentionHours: Int = 24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val confirmBeforeDelete: Boolean = true,
)
