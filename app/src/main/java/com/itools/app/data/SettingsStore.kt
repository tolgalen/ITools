package com.itools.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** SYSTEM, LIGHT and DARK use the neutral green palette (or the dynamic one); the rest are custom dark palettes. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, DARK_BLUE, BLACK, GREEN;

    /** Custom palettes take precedence over dynamic color. */
    val hasOwnPalette get() = this == DARK_BLUE || this == BLACK || this == GREEN
}

/** Settings stored in the backup (the language is handled separately by AppCompat). */
data class SettingsSnapshot(val theme: ThemeMode, val dynamicColor: Boolean, val favorites: Set<String>)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kFavs = stringSetPreferencesKey("favorites")
    private val kDynamic = booleanPreferencesKey("dynamic_color")

    private fun themeOf(p: Preferences) =
        runCatching { ThemeMode.valueOf(p[kTheme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)

    val theme: Flow<ThemeMode> = context.dataStore.data.map(::themeOf).distinctUntilChanged()
    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[kFavs] ?: emptySet() }.distinctUntilChanged()
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[kDynamic] ?: false }.distinctUntilChanged()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[kTheme] = mode.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[kDynamic] = v }
    suspend fun toggleFavorite(id: String) = context.dataStore.edit {
        val cur = it[kFavs] ?: emptySet()
        it[kFavs] = if (id in cur) cur - id else cur + id
    }

    suspend fun snapshot(): SettingsSnapshot = context.dataStore.data.first().let { p ->
        SettingsSnapshot(themeOf(p), p[kDynamic] ?: false, p[kFavs] ?: emptySet())
    }

    suspend fun restore(s: SettingsSnapshot) = context.dataStore.edit {
        it[kTheme] = s.theme.name
        it[kDynamic] = s.dynamicColor
        it[kFavs] = s.favorites
    }
}
