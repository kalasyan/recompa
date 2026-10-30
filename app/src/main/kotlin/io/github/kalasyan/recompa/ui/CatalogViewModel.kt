package io.github.kalasyan.recompa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.UnsupportedCatalogException
import io.github.kalasyan.recompa.core.device.Compatibility
import io.github.kalasyan.recompa.core.device.CompatibilityIssue
import io.github.kalasyan.recompa.core.device.DeviceProfile
import io.github.kalasyan.recompa.data.AndroidDevice
import io.github.kalasyan.recompa.data.AppLanguage
import io.github.kalasyan.recompa.data.CatalogRepository
import io.github.kalasyan.recompa.data.CatalogSource
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

    init {
        refresh()
    }

    fun refresh() {
        _state.value = CatalogState.Loading
        viewModelScope.launch {
            _state.value = try {
                ready(repository.load().also { loaded = it })
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

    private fun ready(loaded: LoadedCatalog): CatalogState.Ready {
        val items = loaded.index.ports
            .map { it.localized(language) }
            .map { PortItem(it, Compatibility.check(it.requirements, device)) }
            .sortedWith(compareBy({ !it.compatible }, { it.port.name.lowercase() }))
        return CatalogState.Ready(
            items,
            fromCache = loaded.source == CatalogSource.CACHE,
            feedbackUrl = loaded.index.feedbackUrl?.takeIf { it.startsWith("https://") },
        )
    }
}
