package com.itools.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itools.app.data.SettingsStore
import com.itools.app.data.ThemeMode
import com.itools.app.ui.AppNav
import com.itools.app.ui.components.ProvideLanguage
import com.itools.app.ui.theme.IToolsTheme

// AppCompatActivity is required for per-app language switching (AppCompatDelegate.setApplicationLocales).
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(applicationContext)
        setContent {
            val mode by store.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val dynamic by store.dynamicColor.collectAsStateWithLifecycle(false)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.DARK_BLUE, ThemeMode.BLACK, ThemeMode.GREEN -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            IToolsTheme(mode = mode, dark = dark, dynamic = dynamic) {
                ProvideLanguage { AppNav(store) }
            }
        }
    }
}
