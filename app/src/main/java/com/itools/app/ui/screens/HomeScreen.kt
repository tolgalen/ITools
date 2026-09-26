package com.itools.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** Tool list keys: favorites, all, or the name of a [Category]. */
const val LIST_FAVORITES = "favorites"
const val LIST_ALL = "all"

private val MARKS = Regex("\\p{M}+")
private fun norm(s: String) = MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

/** Localized texts of a tool and its pre-normalized search text. */
private class ToolTexts(val name: String, val desc: String, val haystack: String)

/** Localized names and normalized text, computed once per configuration (not on every keystroke). */
@Composable
private fun rememberToolTexts(): Map<String, ToolTexts> {
    val res = LocalContext.current.resources
    val config = LocalConfiguration.current
    return remember(config) {
        ToolCatalog.entries.associate { m ->
            val name = res.getString(m.name)
            val desc = res.getString(m.description)
            m.id to ToolTexts(name, desc, listOf(name, m.englishName, m.id, desc).joinToString("\n") { norm(it) })
        }
    }
}

/* ======================= Home: tiles ======================= */

private class NavTile(val key: String, val title: String, val icon: ImageVector, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(store: SettingsStore, onOpenTool: (String) -> Unit, onOpenList: (String) -> Unit, onOpenSettings: () -> Unit) {
    val favs by store.favorites.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val texts = rememberToolTexts()

    val results = remember(query, texts) {
        val q = norm(query.trim())
        if (q.isEmpty()) emptyList() else ToolCatalog.entries.filter { q in texts.getValue(it.id).haystack }
    }

    // Favorites (if any) first, then "All" and one per category
    val primary = MaterialTheme.colorScheme.primary
    val tiles = buildList {
        if (ToolCatalog.entries.any { it.id in favs }) add(NavTile(LIST_FAVORITES, stringResource(R.string.tile_favorites), Icons.Outlined.Star, primary))
        add(NavTile(LIST_ALL, stringResource(R.string.tile_all), Icons.Outlined.Apps, primary))
        Category.entries.forEach { add(NavTile(it.name, stringResource(it.label), it.icon, it.tint)) }
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
                actions = { OverflowMenu(onOpenSettings) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        // The search field lives outside the grids so it is not recreated (and does not lose focus) when switching from tiles to results
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().imePadding()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
            )
            val bottom = PaddingValues(bottom = pad.calculateBottomPadding())
            if (query.isBlank()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = pad.calculateBottomPadding() + 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tiles, key = { it.key }) { tile -> HomeTile(tile.title, tile.icon, tile.tint) { onOpenList(tile.key) } }
                }
            } else {
                ToolCardGrid(results, texts, favs, bottom, onOpenTool, { id -> scope.launch { store.toggleFavorite(id) } }) {
                    Text(
                        stringResource(R.string.no_results, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }
    }
}

/** Square navigation tile (icon + title), styled like the menus of the other apps. */
@Composable
private fun HomeTile(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.aspectRatio(1f)) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Three-dot overflow menu of the top bar. */
@Composable
private fun OverflowMenu(onOpenSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                onClick = { open = false; onOpenSettings() }
            )
        }
    }
}

/* ======================= Subtiles: tool list ======================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolListScreen(key: String, store: SettingsStore, onOpenTool: (String) -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val favs by store.favorites.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    val texts = rememberToolTexts()
    val category = Category.entries.firstOrNull { it.name == key }
    val title = when {
        key == LIST_FAVORITES -> stringResource(R.string.favorites)
        category != null -> stringResource(category.label)
        else -> stringResource(R.string.all_tools)
    }
    val tools = remember(key, favs) {
        when {
            key == LIST_FAVORITES -> ToolCatalog.entries.filter { it.id in favs }
            category != null -> ToolCatalog.entries.filter { it.category == category }
            else -> ToolCatalog.entries
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = { OverflowMenu(onOpenSettings) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        ToolCardGrid(tools, texts, favs, pad, onOpenTool, { id -> scope.launch { store.toggleFavorite(id) } }) {
            Text(
                stringResource(R.string.list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        }
    }
}

/** Grid of tool cards with description and favorite button. */
@Composable
private fun ToolCardGrid(
    tools: List<ToolMeta>,
    texts: Map<String, ToolTexts>,
    favs: Set<String>,
    pad: PaddingValues,
    onOpenTool: (String) -> Unit,
    onToggleFav: (String) -> Unit,
    empty: @Composable () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (tools.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
        items(tools, key = { it.id }) { m ->
            val tx = texts.getValue(m.id)
            ToolCard(m, tx.name, tx.desc, m.id in favs, { onOpenTool(m.id) }) { onToggleFav(m.id) }
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
                    Modifier.size(36.dp).background(m.category.tint.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
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
