package io.github.kalasyan.recompa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.core.catalog.LiveReports
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.UnsupportedCatalogException
import io.github.kalasyan.recompa.core.device.Compatibility
import io.github.kalasyan.recompa.core.device.CompatibilityIssue
import io.github.kalasyan.recompa.core.device.DeviceProfile
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.data.AppLanguage
import io.github.kalasyan.recompa.data.CatalogRepository
import io.github.kalasyan.recompa.data.CatalogSource
import io.github.kalasyan.recompa.data.FeedbackClient
import io.github.kalasyan.recompa.data.LoadedCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class PortItem(val port: Port, val issues: List<CompatibilityIssue>) {
    val compatible: Boolean get() = issues.isEmpty()
}

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val items: List<PortItem>, val fromCache: Boolean, val feedbackUrl: String? = null) : CatalogState
    data class Failed(val reason: Reason) : CatalogState

    enum class Reason { NETWORK, NEWER_CATALOG }
}

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CatalogRepository(
        catalogUrl = BuildConfig.CATALOG_URL,
        cacheFile = File(application.filesDir, "catalog/index.json"),
    )
    private val device: DeviceProfile = AndroidDevice.profile(application)

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var loaded: LoadedCatalog? = null
    private var language = AppLanguage.current(application)

    /** Reports from the feedback endpoint, newer than the catalog's; null until they arrive. */
    private var live: LiveReports? = null
    /** Whether [live] came from the endpoint (all ports) or only holds reports sent from here. */
    private var liveComplete = false
    private var liveAt = 0L

    init {
        refresh()
    }

    fun refresh() {
        _state.value = CatalogState.Loading
        viewModelScope.launch {
            _state.value = try {
                ready(repository.load().also { loaded = it }).also { refreshReports(force = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsupportedCatalogException) {
                CatalogState.Failed(CatalogState.Reason.NEWER_CATALOG)
            } catch (e: Exception) {
                CatalogState.Failed(CatalogState.Reason.NETWORK)
            }
        }
    }

    /** Shows the catalog's texts in [language] (the activity's), without downloading it again. */
    fun setLanguage(language: String) {
        if (language == this.language) return
        this.language = language
        loaded?.let { _state.value = ready(it) }
    }

    /**
     * Fetches the reports as the feedback endpoint counts them now, so a report shows up in
     * minutes instead of at the next catalog build. At most every 30 seconds unless [force].
     */
    fun refreshReports(force: Boolean = false) {
        val url = feedbackUrl() ?: return
        val now = System.currentTimeMillis()
        if (!force && now - liveAt < 30_000) return
        liveAt = now
        viewModelScope.launch {
            val fresh = FeedbackClient(url).reports() ?: return@launch
            live = fresh
            liveComplete = true
            loaded?.let { _state.value = ready(it) }
        }
    }

    /** Counts a report the player just sent, before the endpoint's next answer includes it. */
    fun reportSent(portId: String, device: String, result: String, tag: String?) {
        val catalog = loaded?.index?.ports?.firstOrNull { it.id == portId }?.reports.orEmpty()
        live = (live ?: LiveReports()).withReport(portId, device, result, tag, fallback = catalog)
        // The endpoint caches its answer for a couple of minutes: don't overwrite this with it yet.
        liveAt = System.currentTimeMillis() + 90_000
        loaded?.let { _state.value = ready(it) }
    }

    private fun feedbackUrl(): String? = loaded?.index?.feedbackUrl?.takeIf { it.startsWith("https://") }

    private fun ready(loaded: LoadedCatalog): CatalogState.Ready {
        val reports = live?.reports
        val items = loaded.index.ports
            .map { it.localized(language) }
            .map { port ->
                val rows = reports?.get(port.id) ?: if (liveComplete) emptyList() else port.reports
                port.copy(reports = rows)
            }
            .map { PortItem(it, Compatibility.check(it.requirements, device)) }
            .sortedWith(compareBy({ !it.compatible }, { it.port.name.lowercase() }))
        return CatalogState.Ready(
            items,
            fromCache = loaded.source == CatalogSource.CACHE,
            feedbackUrl = feedbackUrl(),
        )
    }
}
