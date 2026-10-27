package io.github.kalasyan.recompa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kalasyan.recompa.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: CatalogState,
    onRetry: () -> Unit,
    onOpen: (PortItem) -> Unit,
    onDiagnostics: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
                actions = { TextButton(onClick = onDiagnostics) { Text(stringResource(R.string.diagnostics_title)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                CatalogState.Loading -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.loading))
                }
                is CatalogState.Failed -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val message = when (state.reason) {
                        CatalogState.Reason.NETWORK -> R.string.error_network
                        CatalogState.Reason.NEWER_CATALOG -> R.string.error_newer_catalog
                    }
                    Text(stringResource(message), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                is CatalogState.Ready -> PortGrid(state, onOpen)
            }
        }
    }
}

@Composable
private fun PortGrid(state: CatalogState.Ready, onOpen: (PortItem) -> Unit) {
    if (state.items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_catalog))
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.fromCache) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.offline_copy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.port.id }) { item -> PortCard(item, onClick = { onOpen(item) }) }
    }
}

@Composable
private fun PortCard(item: PortItem, onClick: () -> Unit) {
    // Cards are focusable and clickable, so a gamepad's D-pad and A button work out of the box.
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.port.name, style = MaterialTheme.typography.titleMedium)
            Text(
                item.port.game,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val label = when {
                item.port.recommendedFork == null -> R.string.no_release
                item.compatible -> R.string.compatible
                else -> R.string.not_compatible
            }
            AssistChip(onClick = onClick, label = { Text(stringResource(label)) })
        }
    }
}
