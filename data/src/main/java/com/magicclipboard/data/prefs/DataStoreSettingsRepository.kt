package com.magicclipboard.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.magicclipboard.data.model.AppSettings
import com.magicclipboard.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            retentionHours = prefs[KEY_RETENTION_HOURS] ?: 24,
            themeMode = parseThemeMode(prefs[KEY_THEME_MODE]),
            confirmBeforeDelete = prefs[KEY_CONFIRM_BEFORE_DELETE] ?: true,
        )
    }

    override suspend fun setRetentionHours(hours: Int) {
        dataStore.edit { it[KEY_RETENTION_HOURS] = hours.coerceIn(1, 24 * 30) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setConfirmBeforeDelete(enabled: Boolean) {
        dataStore.edit { it[KEY_CONFIRM_BEFORE_DELETE] = enabled }
    }

    private companion object {
        val KEY_RETENTION_HOURS = intPreferencesKey("retention_hours")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
    }

    private fun parseThemeMode(rawValue: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == rawValue } ?: ThemeMode.SYSTEM
}
