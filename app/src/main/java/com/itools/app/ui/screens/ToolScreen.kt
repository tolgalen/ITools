package com.itools.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itools.app.R
import com.itools.app.data.SettingsStore
import com.itools.app.tools.ToolCatalog
import com.itools.app.tools.ToolContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(id: String, store: SettingsStore, onBack: () -> Unit) {
    val meta = ToolCatalog.find(id)
    if (meta == null) { Text("Unknown tool: $id"); return }
    val favs by store.favorites.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    val fav = id in favs
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(meta.name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { scope.launch { store.toggleFavorite(id) } }) {
                        Icon(
                            if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            stringResource(if (fav) R.string.remove_favorite else R.string.add_favorite),
                            tint = if (fav) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                stringResource(meta.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f)) { ToolContent(id) }
        }
    }
}
