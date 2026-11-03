package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.DeviceReport
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.PortSort
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import io.github.kalasyan.recompa.core.catalog.Requirements
import kotlin.test.Test
import kotlin.test.assertEquals

class PortSortTest {
    private fun port(
        id: String,
        game: String = id,
        sortName: String? = null,
        addedAt: String? = null,
        released: List<String> = emptyList(),
        reports: List<DeviceReport> = emptyList(),
    ) = Port(
        id = id, name = "$id port", game = game, sortName = sortName, originalPlatform = "n64", type = "decomp",
        forks = released.mapIndexed { i, at ->
            Fork(
                repo = "owner/$id-$i", assetPattern = "*.apk",
                release = Release(tag = "v$i", name = "v$i", publishedAt = at, asset = ReleaseAsset(name = "a.apk", url = "https://example.com/a.apk")),
            )
        },
        requirements = Requirements(abi = listOf("arm64-v8a"), minSdk = 24),
        addedAt = addedAt,
        reports = reports,
    )

    private fun ids(sort: PortSort, vararg ports: Port) = sort.sort(ports.toList()) { it }.map { it.id }

    @Test
    fun gameOrderUsesTheOriginalGameWithoutArticlesOrAccents() {
        assertEquals(
            listOf("pokemon", "zelda", "sonic", "zz"),
            ids(
                PortSort.GAME,
                port("zz", game = "Zeta"),
                port("sonic", game = "Sonic Mania"),
                port("zelda", game = "The Legend of Zelda"),
                port("pokemon", game = "Ácido Pokémon"),
            ),
        )
    }

    @Test
    fun defaultOrderIsByThePortsOwnNameAndIsWhatUnknownIdsGet() {
        assertEquals(listOf("b", "a"), ids(PortSort.DEFAULT, port("a", game = "Alpha").copy(name = "Zulu"), port("b", game = "Zulu").copy(name = "Alpha")))
        assertEquals(PortSort.DEFAULT, PortSort.fromId(null))
        assertEquals(PortSort.DEFAULT, PortSort.fromId("nope"))
    }

    @Test
    fun sortNameWinsOverTheGame() {
        assertEquals(listOf("b", "a"), ids(PortSort.GAME, port("a", game = "Alpha"), port("b", game = "Zulu", sortName = "Aardvark")))
    }

    @Test
    fun addedOrderIsNewestFirstThenByName() {
        assertEquals(
            listOf("new", "a", "b", "none"),
            ids(
                PortSort.ADDED,
                port("none"),
                port("b", addedAt = "2026-10-27T00:00:00Z"),
                port("new", addedAt = "2026-11-02T06:00:00Z"),
                port("a", addedAt = "2026-10-27T00:00:00Z"),
            ),
        )
    }

    @Test
    fun updatedOrderUsesTheNewestReleaseOfAnyVersion() {
        assertEquals(
            listOf("fresh", "old", "never"),
            ids(
                PortSort.UPDATED,
                port("never"),
                port("old", released = listOf("2026-01-01T00:00:00Z")),
                port("fresh", released = listOf("2025-05-01T00:00:00Z", "2026-10-30T00:00:00Z")),
            ),
        )
    }

    @Test
    fun ratingOrderPutsUnreportedPortsLast() {
        assertEquals(
            listOf("many", "one", "bad", "none"),
            ids(
                PortSort.RATING,
                port("none"),
                port("bad", reports = listOf(DeviceReport("Thor", broken = 3))),
                port("one", reports = listOf(DeviceReport("Thor", great = 1))),
                port("many", reports = listOf(DeviceReport("Thor", great = 40, broken = 2))),
            ),
        )
    }
}
