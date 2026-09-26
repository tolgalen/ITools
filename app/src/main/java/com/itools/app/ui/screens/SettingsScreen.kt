package com.itools.app.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
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
import com.itools.app.data.SettingsBackup
import com.itools.app.data.SettingsStore
import com.itools.app.data.ThemeMode
import com.itools.app.tools.ToolCatalog
import com.itools.app.ui.components.Panel
import com.itools.app.util.writeUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

private val THEMES = listOf(
    ThemeMode.SYSTEM to R.string.theme_system,
    ThemeMode.LIGHT to R.string.theme_light,
    ThemeMode.DARK to R.string.theme_dark,
    ThemeMode.DARK_BLUE to R.string.theme_dark_blue,
    ThemeMode.BLACK to R.string.theme_black,
    ThemeMode.GREEN to R.string.theme_green,
)

/** App language ("" = system language). */
private fun currentLanguage() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

/** Recreates the activity and persists the language (on Android 13+ the system manages it). */
private fun applyLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(
    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
)

private fun toast(ctx: Context, @StringRes msg: Int) = Toast.makeText(ctx, ctx.getString(msg), Toast.LENGTH_SHORT).show()

/** Maximum accepted size for a backup file (avoids loading huge files by mistake). */
private const val MAX_BACKUP_BYTES = 1_000_000

/** Reads at most [limit] bytes (InputStream.readNBytes requires API 33). */
private fun readLimited(input: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val theme by store.theme.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val dynamic by store.dynamicColor.collectAsStateWithLifecycle(false)
    val currentLang = remember { currentLanguage() }
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val uri = LocalUriHandler.current

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { dest ->
        dest ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = SettingsBackup.toJson(store.snapshot(), currentLanguage(), version)
                withContext(Dispatchers.IO) { writeUri(ctx, dest, json.toByteArray()) }
            }.isSuccess
            toast(ctx, if (ok) R.string.backup_saved else R.string.backup_error)
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { src ->
        src ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(src)?.use { readLimited(it, MAX_BACKUP_BYTES + 1) } ?: error("Unreadable file")
                }
                require(bytes.size <= MAX_BACKUP_BYTES) { "File too large" }
                SettingsBackup.fromJson(bytes.decodeToString(), ToolCatalog.entries.mapTo(HashSet()) { it.id })
            }.onSuccess { r ->
                store.restore(r.snapshot)
                toast(ctx, R.string.backup_restored)
                // Changing the language recreates the activity, so it is done last
                if (r.language != null && r.language != currentLanguage()) applyLanguage(r.language)
            }.onFailure { toast(ctx, R.string.backup_invalid) }
        }
    }

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
                THEMES.forEach { (mode, label) ->
                    RadioLine(stringResource(label), theme == mode) { scope.launch { store.setTheme(mode) } }
                }
                // Custom palettes do not use dynamic color
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !theme.hasOwnPalette) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dynamic_color), Modifier.weight(1f))
                        Switch(dynamic, { v -> scope.launch { store.setDynamicColor(v) } })
                    }
                }
            }
            Panel(stringResource(R.string.language)) {
                listOf("" to R.string.lang_system, "en" to R.string.lang_en, "es" to R.string.lang_es).forEach { (tag, label) ->
                    RadioLine(stringResource(label), currentLang == tag) { applyLanguage(tag) }
                }
            }
            Panel(stringResource(R.string.backup)) {
                Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportBackup.launch("itools-backup-${LocalDate.now()}.json") }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.backup_create))
                    }
                    OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.backup_restore))
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
            Panel(stringResource(R.string.license)) {
                Text(stringResource(R.string.license_text), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { uri.openUri("https://www.gnu.org/licenses/gpl-3.0.html") }) { Text(stringResource(R.string.license_link)) }
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
