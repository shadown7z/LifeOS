package com.atlaes.lifeos.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.lifeOsPreferences by preferencesDataStore(name = "lifeos_preferences")

class ThemePreference(private val context: Context) {
    private val key = stringPreferencesKey("theme_mode")

    val mode: Flow<ThemeMode> = context.lifeOsPreferences.data.map { preferences ->
        runCatching { ThemeMode.valueOf(preferences[key] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setMode(mode: ThemeMode) {
        context.lifeOsPreferences.edit { it[key] = mode.name }
    }
}
