package com.itools.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itools.app.R
import com.itools.app.data.SettingsStore
import com.itools.app.tools.Category
import com.itools.app.tools.ToolCatalog
import com.itools.app.tools.ToolMeta
import kotlinx.coroutines.launch
import java.text.Normalizer

private fun norm(s: String) =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: SettingsStore, onOpenTool: (String) -> Unit, onOpenSettings: () -> Unit) {
    val favs by store.favorites.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    // Nombres localizados resueltos una vez para buscar en ambos idiomas
    val names = ToolCatalog.entries.associate { it.id to stringResource(it.name) }
    val descs = ToolCatalog.entries.associate { it.id to stringResource(it.description) }

    val filtered = remember(query, category, names) {
        val q = norm(query.trim())
        ToolCatalog.entries.filter { m ->
            (category == null || m.category.name == category) &&
                (q.isEmpty() || listOf(names[m.id]!!, m.englishName, m.id, descs[m.id]!!).any { norm(it).contains(q) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.tools_count, ToolCatalog.entries.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().imePadding()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRowChips(category) { category = it }
            }
            val favTools = ToolCatalog.entries.filter { it.id in favs && it in filtered }
            if (favTools.isNotEmpty() && query.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.favorites)) }
                items(favTools, key = { "fav-" + it.id }) { m ->
                    ToolCard(m, names[m.id]!!, descs[m.id]!!, true, { onOpenTool(m.id) }) {
                        scope.launch { store.toggleFavorite(m.id) }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.all_tools)) }
            }
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.no_results, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
            items(filtered, key = { it.id }) { m ->
                ToolCard(m, names[m.id]!!, descs[m.id]!!, m.id in favs, { onOpenTool(m.id) }) {
                    scope.launch { store.toggleFavorite(m.id) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun LazyRowChips(selected: String?, onSelect: (String?) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.all_tools)) })
        }
        items(Category.entries.size) { i ->
            val c = Category.entries[i]
            FilterChip(
                selected = selected == c.name,
                onClick = { onSelect(if (selected == c.name) null else c.name) },
                leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp), tint = c.tint) },
                label = { Text(stringResource(c.label)) }
            )
        }
    }
}

@Composable
private fun ToolCard(m: ToolMeta, name: String, desc: String, fav: Boolean, onClick: () -> Unit, onFav: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .then(Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(color = m.category.tint.copy(alpha = 0.14f), shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
                    Icon(m.icon, null, tint = m.category.tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(m.category.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFav, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
