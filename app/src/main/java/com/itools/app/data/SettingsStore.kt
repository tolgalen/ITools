package com.itools.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kFavs = stringSetPreferencesKey("favorites")
    private val kDynamic = booleanPreferencesKey("dynamic_color")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[kTheme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }.distinctUntilChanged()
    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[kFavs] ?: emptySet() }.distinctUntilChanged()
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[kDynamic] ?: false }.distinctUntilChanged()

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[kTheme] = mode.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[kDynamic] = v }
    suspend fun toggleFavorite(id: String) = context.dataStore.edit {
        val cur = it[kFavs] ?: emptySet()
        it[kFavs] = if (id in cur) cur - id else cur + id
    }
}
