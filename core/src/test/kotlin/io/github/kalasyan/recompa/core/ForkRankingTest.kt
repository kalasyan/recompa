package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.DeviceReport
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.ForkRanking
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForkRankingTest {
    private val release = Release("v1", "v1", asset = ReleaseAsset("p.apk", "https://example.com/p.apk"))

    private fun fork(repo: String, vararg reports: DeviceReport, released: Boolean = true) =
        Fork(repo, assetPattern = "x", release = release.takeIf { released }, reports = reports.toList())

    @Test
    fun recommendsTheBestRatedVersionWithARelease() {
        val first = fork("a/first", DeviceReport("Thor", great = 1, broken = 2))
        val second = fork("b/second", DeviceReport("Thor", playable = 2))
        val unreleased = fork("c/none", DeviceReport("Thor", great = 9), released = false)
        assertEquals(second, ForkRanking.best(listOf(first, second, unreleased)))
    }

    @Test
    fun tiesGoToMorePerfectReportsThenToTheCatalogOrder() {
        val playable = fork("a/first", DeviceReport("Thor", playable = 2))
        val perfect = fork("b/second", DeviceReport("Thor", great = 2))
        assertEquals(perfect, ForkRanking.best(listOf(playable, perfect)))
        val quiet1 = fork("c/one")
        val quiet2 = fork("d/two")
        assertEquals(quiet1, ForkRanking.best(listOf(quiet1, quiet2)))
        assertNull(ForkRanking.best(listOf(fork("e/none", released = false))))
    }
}
