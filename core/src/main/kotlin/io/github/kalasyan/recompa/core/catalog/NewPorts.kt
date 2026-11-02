package io.github.kalasyan.recompa.core.catalog

object NewPorts {
    /**
     * Ids of the ports added by the latest catalog generation that added any. A generation that
     * adds nothing keeps the previous batch, so the list only changes when new ports arrive.
     * Empty for catalogs without `added_at`.
     */
    fun latestBatch(ports: List<Port>): Set<String> {
        // Every stamp is written in the same UTC format, so they compare as text.
        val latest = ports.mapNotNull { it.addedAt }.maxOrNull() ?: return emptySet()
        return ports.filter { it.addedAt == latest }.mapTo(mutableSetOf()) { it.id }
    }
}
