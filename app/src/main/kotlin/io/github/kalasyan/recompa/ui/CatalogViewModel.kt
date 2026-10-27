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
import io.github.kalasyan.recompa.data.CatalogRepository
import io.github.kalasyan.recompa.data.CatalogSource
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
    data class Ready(val items: List<PortItem>, val fromCache: Boolean) : CatalogState
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

    init {
        refresh()
    }

    fun refresh() {
        _state.value = CatalogState.Loading
        viewModelScope.launch {
            _state.value = try {
                val loaded = repository.load()
                val items = loaded.index.ports
                    .map { PortItem(it, Compatibility.check(it.requirements, device)) }
                    .sortedWith(compareBy({ !it.compatible }, { it.port.name.lowercase() }))
                CatalogState.Ready(items, fromCache = loaded.source == CatalogSource.CACHE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsupportedCatalogException) {
                CatalogState.Failed(CatalogState.Reason.NEWER_CATALOG)
            } catch (e: Exception) {
                CatalogState.Failed(CatalogState.Reason.NETWORK)
            }
        }
    }
}
