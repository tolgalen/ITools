package com.itools.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itools.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/* ---------- Language ---------- */

/** Active language, resolved once at the root (see [ProvideLanguage]) instead of on every call to [t]. */
private val LocalSpanish = staticCompositionLocalOf { false }

/** Resolves the language of the active resources (honors the language chosen in the app) and exposes it to [isSpanish]. */
@Composable
fun ProvideLanguage(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalSpanish provides (stringResource(R.string.lang_code) == "es"), content = content)

/** true if the active resources are the Spanish ones. */
@Composable
@ReadOnlyComposable
fun isSpanish(): Boolean = LocalSpanish.current

/** Bilingual text for the internal labels of the tools. */
@Composable
@ReadOnlyComposable
fun t(en: String, es: String): String = if (LocalSpanish.current) es else en

/** Color in CSS format (#rrggbb), for web views. */
fun Color.css(): String = "#%06x".format(toArgb() and 0xffffff)

val Mono = FontFamily.Monospace
val MonoStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono)

/* ---------- Clipboard ---------- */

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ITools", text))
    // Android 13+ already shows its own notice
    if (android.os.Build.VERSION.SDK_INT < 33)
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

@Composable
fun CopyButton(text: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    IconButton(onClick = { copyText(ctx, text) }, enabled = text.isNotEmpty(), modifier = modifier) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy))
    }
}

/* ---------- Background work ---------- */

/**
 * Runs [compute] on a background thread [debounceMs] after the last change of [keys], keeping the previous
 * result meanwhile, so typing never blocks the UI. The first value is computed immediately (small samples).
 */
@Composable
fun <T> rememberComputed(vararg keys: Any?, debounceMs: Long = 250, compute: () -> T): T {
    var value by remember { mutableStateOf(compute()) }
    val skipFirst = remember { booleanArrayOf(true) }
    LaunchedEffect(*keys) {
        if (skipFirst[0]) { skipFirst[0] = false; return@LaunchedEffect }
        delay(debounceMs)
        value = withContext(Dispatchers.Default) { compute() }
    }
    return value
}

/* ---------- Layout ---------- */

/** Standard scrollable container for each tool. */
@Composable
fun ToolColumn(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val m = Modifier.fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
    Column(
        m.padding(horizontal = 16.dp, vertical = 12.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun Panel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/* ---------- Fields ---------- */

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else 18,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        textStyle = if (mono) MonoStyle else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth()
    )
}

/** Read-only field with a copy button. */
@Composable
fun OutputField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    mono: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else 30,
        textStyle = if (mono) MonoStyle else LocalTextStyle.current,
        trailingIcon = { CopyButton(value) },
        modifier = modifier.fillMaxWidth()
    )
}

/** Label / value row with copy (table-like results). */
@Composable
fun KeyValue(label: String, value: String, copy: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(128.dp)
        )
        // The weight goes on a Box: applied to SelectionContainer, long values pushed the copy button off screen
        Box(Modifier.weight(1f)) {
            SelectionContainer { Text(value.ifEmpty { "—" }, style = MonoStyle) }
        }
        if (copy) CopyButton(value, Modifier.size(36.dp))
    }
}

@Composable
fun KeyValueTable(rows: List<Pair<String, String>>, copy: Boolean = true) {
    Panel {
        rows.forEachIndexed { i, (k, v) ->
            KeyValue(k, v, copy)
            if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun ErrorText(text: String) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

@Composable
fun Hint(text: String) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

/* ---------- Selectors ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoice(label: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (v, l) ->
                FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(l) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
fun NumberField(value: Int, onChange: (Int) -> Unit, label: String, modifier: Modifier = Modifier, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    InputField(text, {
        text = it
        it.toIntOrNull()?.let { n -> onChange(n.coerceIn(min, max)) }
    }, label, modifier, keyboard = KeyboardType.Number)
}

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) =
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )

@Composable
fun MonoBlock(text: String, modifier: Modifier = Modifier, size: Int = 13) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
            SelectionContainer {
                Text(text, style = TextStyle(fontFamily = Mono, fontSize = size.sp, lineHeight = (size * 1.3).sp))
            }
        }
    }
}
