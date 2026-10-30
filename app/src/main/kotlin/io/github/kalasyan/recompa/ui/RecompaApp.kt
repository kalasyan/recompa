package io.github.kalasyan.recompa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val DIAGNOSTICS = "#diagnostics"

/** Three screens for now: the catalog, a port's detail page and diagnostics, plus the settings dialog. */
@Composable
fun RecompaApp(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val frontendMode by rememberUserPrefs().frontendMode.collectAsStateWithLifecycle()
    // Keeps the catalog's list, selection and scroll while another screen is open.
    val screens = rememberSaveableStateHolder()
    // The catalog outlives the activity, which is recreated when the language changes.
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.setLanguage(language) }

    CompositionLocalProvider(LocalFeedbackUrl provides (state as? CatalogState.Ready)?.feedbackUrl) {
        // Surface sets the default text colour; without it, text outside a Scaffold is black.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
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
                    // As the home app, Back on the library has nowhere to go.
                    if (frontendMode) BackHandler { }
                    screens.SaveableStateProvider("catalog") {
                        CatalogScreen(
                            state = state,
                            onRetry = viewModel::refresh,
                            onOpen = { route = it.port.id },
                            onDiagnostics = { route = DIAGNOSTICS },
                            onSettings = { settings = true },
                        )
                    }
                    UpdateBanner(Modifier.align(Alignment.BottomCenter))
                    WelcomeDialog()
                    NotificationPermissionRequest()
                }
                if (settings) SettingsDialog(onDismiss = { settings = false })
            }
        }
    }
}
