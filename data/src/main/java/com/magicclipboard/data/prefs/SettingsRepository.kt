package com.magicclipboard.data.prefs

import com.magicclipboard.data.model.AppSettings
import com.magicclipboard.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setRetentionHours(hours: Int)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setConfirmBeforeDelete(enabled: Boolean)
}
