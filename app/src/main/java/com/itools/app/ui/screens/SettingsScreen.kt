package com.itools.app.ui.screens

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itools.app.R
import com.itools.app.data.SettingsStore
import com.itools.app.data.ThemeMode
import com.itools.app.ui.components.Panel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val theme by store.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val dynamic by store.dynamicColor.collectAsStateWithLifecycle(false)
    val currentLang = remember { AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-') }
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val uri = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(stringResource(R.string.theme)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark
                ).forEach { (mode, label) ->
                    RadioLine(stringResource(label), theme == mode) { scope.launch { store.setTheme(mode) } }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dynamic_color), Modifier.weight(1f))
                        Switch(dynamic, { v -> scope.launch { store.setDynamicColor(v) } })
                    }
                }
            }
            Panel(stringResource(R.string.language)) {
                listOf("" to R.string.lang_system, "en" to R.string.lang_en, "es" to R.string.lang_es).forEach { (tag, label) ->
                    RadioLine(stringResource(label), currentLang == tag) {
                        // Recrea la actividad y persiste el idioma (Android 13+ lo gestiona el sistema).
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                        )
                    }
                }
            }
            Panel(stringResource(R.string.about)) {
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { uri.openUri("https://it-tools.tech") }) { Text("it-tools.tech") }
                TextButton(onClick = { uri.openUri("https://github.com/CorentinTh/it-tools") }) { Text("github.com/CorentinTh/it-tools") }
            }
        }
    }
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
