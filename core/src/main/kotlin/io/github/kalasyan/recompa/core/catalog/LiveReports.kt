package io.github.kalasyan.recompa.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The compatibility reports as the feedback endpoint counts them right now (`GET /reports`),
 * by port id. Same rows as each port's `reports` in `index.json`, which only change when
 * the catalog is rebuilt.
 */
@Serializable
data class LiveReports(
    @SerialName("generated_at") val generatedAt: String? = null,
    val reports: Map<String, List<DeviceReport>> = emptyMap(),
) {
    /** [port]'s reports with one more from [device], until the next answer of the endpoint counts it. */
    fun withReport(port: String, device: String, result: String, tag: String?, fallback: List<DeviceReport>): LiveReports {
        val rows = reports[port] ?: fallback
        val existing = rows.firstOrNull { it.device.equals(device, ignoreCase = true) } ?: DeviceReport(device)
        val updated = when (result) {
            "great" -> existing.copy(great = existing.great + 1)
            "playable" -> existing.copy(playable = existing.playable + 1)
            "broken" -> existing.copy(broken = existing.broken + 1)
            else -> return this
        }.copy(lastTag = tag)
        val merged = (rows.filterNot { it === existing } + updated).sortedByDescending { it.total }
        return copy(reports = reports + (port to merged))
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun parse(text: String): LiveReports = json.decodeFromString(serializer(), text)
    }
}
