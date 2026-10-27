package io.github.kalasyan.recompa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Two screens for now: the catalog and a port's detail page. */
@Composable
fun RecompaApp(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val selected = (state as? CatalogState.Ready)?.items?.firstOrNull { it.port.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        PortDetailScreen(item = selected, onBack = { selectedId = null })
    } else {
        CatalogScreen(state = state, onRetry = viewModel::refresh, onOpen = { selectedId = it.port.id })
    }
}
