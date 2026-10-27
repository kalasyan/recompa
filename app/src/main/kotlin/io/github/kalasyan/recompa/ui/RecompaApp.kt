package io.github.kalasyan.recompa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val DIAGNOSTICS = "#diagnostics"

/** Three screens for now: the catalog, a port's detail page and diagnostics. */
@Composable
fun RecompaApp(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf<String?>(null) }

    if (route == DIAGNOSTICS) {
        BackHandler { route = null }
        DiagnosticsScreen(onBack = { route = null })
        return
    }
    val selected = (state as? CatalogState.Ready)?.items?.firstOrNull { it.port.id == route }
    if (selected != null) {
        BackHandler { route = null }
        PortDetailScreen(item = selected, onBack = { route = null })
    } else {
        CatalogScreen(
            state = state,
            onRetry = viewModel::refresh,
            onOpen = { route = it.port.id },
            onDiagnostics = { route = DIAGNOSTICS },
        )
    }
}
