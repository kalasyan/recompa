package io.github.kalasyan.recompa.core.catalog

object NewPorts {
    /**
     * Ids of the ports added on the latest day (UTC) that added any, whatever the number of
     * catalog generations that day. A day that adds nothing keeps the previous batch, so the
     * list only changes when new ports arrive. Empty for catalogs without `added_at`.
     */
    fun latestBatch(ports: List<Port>): Set<String> {
        // Stamps are ISO 8601 in UTC ("2026-11-02T06:17:00Z"): the first 10 characters are the day.
        val day: (Port) -> String? = { it.addedAt?.take(10) }
        val latest = ports.mapNotNull(day).maxOrNull() ?: return emptySet()
        return ports.filter { day(it) == latest }.mapTo(mutableSetOf()) { it.id }
    }
}
