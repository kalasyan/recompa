package io.github.kalasyan.recompa.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The compatibility reports as the feedback endpoint counts them right now (`GET /reports`):
 * by port id ([reports], all its versions together) and by port id and fork repository
 * ([forks]). Same rows as `reports` of each port and each fork in `index.json`, which only
 * change when the catalog is rebuilt. Endpoints from before versions had their own reports
 * answer without [forks].
 */
@Serializable
data class LiveReports(
    @SerialName("generated_at") val generatedAt: String? = null,
    val reports: Map<String, List<DeviceReport>> = emptyMap(),
    val forks: Map<String, Map<String, List<DeviceReport>>>? = null,
) {
    /**
     * [port]'s reports, and those of its version [fork], with one more from [device], until the
     * next answer of the endpoint counts it. The fallbacks are the catalog's rows.
     */
    fun withReport(
        port: String,
        fork: String?,
        device: String,
        result: String,
        tag: String?,
        fallback: List<DeviceReport>,
        forkFallback: List<DeviceReport> = emptyList(),
    ): LiveReports {
        val all = add(reports[port] ?: fallback, device, result, tag) ?: return this
        var byFork = forks
        if (fork != null) {
            val mine = forks?.get(port)
            val rows = add(mine?.get(fork) ?: forkFallback, device, result, tag) ?: return this
            byFork = (forks ?: emptyMap()) + (port to ((mine ?: emptyMap()) + (fork to rows)))
        }
        return copy(reports = reports + (port to all), forks = byFork)
    }

    private fun add(rows: List<DeviceReport>, device: String, result: String, tag: String?): List<DeviceReport>? {
        val existing = rows.firstOrNull { it.device.equals(device, ignoreCase = true) } ?: DeviceReport(device)
        val updated = when (result) {
            "great" -> existing.copy(great = existing.great + 1)
            "playable" -> existing.copy(playable = existing.playable + 1)
            "broken" -> existing.copy(broken = existing.broken + 1)
            else -> return null
        }.copy(lastTag = tag)
        return (rows.filterNot { it === existing } + updated).sortedByDescending { it.total }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun parse(text: String): LiveReports = json.decodeFromString(serializer(), text)
    }
}
