package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.NewPorts
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.Requirements
import kotlin.test.Test
import kotlin.test.assertEquals

class NewPortsTest {
    private fun port(id: String, addedAt: String?) = Port(
        id = id, name = id, game = id, originalPlatform = "n64", type = "decomp",
        forks = emptyList(), requirements = Requirements(abi = listOf("arm64-v8a"), minSdk = 24),
        addedAt = addedAt,
    )

    @Test
    fun latestBatchIsThePortsOfTheNewestGeneration() {
        val ports = listOf(
            port("old", "2026-10-27T00:00:00Z"),
            port("a", "2026-11-02T06:17:00Z"),
            port("b", "2026-11-02T06:17:00Z"),
            port("before", "2026-11-01T18:17:00Z"),
        )
        assertEquals(setOf("a", "b"), NewPorts.latestBatch(ports))
    }

    @Test
    fun catalogsWithoutDatesHaveNoNewPorts() {
        assertEquals(emptySet(), NewPorts.latestBatch(listOf(port("a", null))))
    }
}
