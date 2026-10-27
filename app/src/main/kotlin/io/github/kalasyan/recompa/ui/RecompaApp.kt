package io.github.kalasyan.recompa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val DIAGNOSTICS = "#diagnostics"

/** Three screens for now: the catalog, a port's detail page and diagnostics. */
@Composable
fun RecompaApp(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    // Keeps the catalog's list, selection and scroll while another screen is open.
    val screens = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (route == DIAGNOSTICS) {
            BackHandler { route = null }
            DiagnosticsScreen(onBack = { route = null })
            return@Box
        }
        val selected = (state as? CatalogState.Ready)?.items?.firstOrNull { it.port.id == route }
        if (selected != null) {
            BackHandler { route = null }
            PortDetailScreen(item = selected, onBack = { route = null })
        } else {
            screens.SaveableStateProvider("catalog") {
                CatalogScreen(
                    state = state,
                    onRetry = viewModel::refresh,
                    onOpen = { route = it.port.id },
                    onDiagnostics = { route = DIAGNOSTICS },
                )
            }
        }
    }
}
