package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.ArchiveEntry
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import io.github.kalasyan.recompa.core.install.InstalledRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstalledReleaseTest {
    private fun release(tag: String, sha: String?, entrySha: String? = null) = Release(
        tag = tag,
        name = tag,
        asset = ReleaseAsset(
            name = "a.apk",
            url = "https://example.com/$tag",
            sha256 = sha,
            entry = entrySha?.let { ArchiveEntry(path = "a.apk", sha256 = it) },
        ),
    )

    private val fork = Fork(
        repo = "o/r",
        assetPattern = "*.apk",
        release = release("v1.2.0", "AAA"),
        history = listOf(release("v1.1.0", "BBB"), release("v1.0.0", "zipsha", entrySha = "CCC")),
    )

    @Test
    fun matchesTheApkHash() {
        assertEquals("v1.2.0", InstalledRelease.find(fork, "aaa", null)?.tag)
        assertEquals("v1.1.0", InstalledRelease.find(fork, "bbb", "something else")?.tag)
    }

    @Test
    fun matchesTheApkInsideAZip() {
        assertEquals("v1.0.0", InstalledRelease.find(fork, "ccc", null)?.tag)
        assertNull(InstalledRelease.find(fork, "zipsha", null))
    }

    @Test
    fun fallsBackToTheVersionName() {
        assertEquals("v1.1.0", InstalledRelease.find(fork, "unknown", "1.1.0")?.tag)
        assertEquals("v1.2.0", InstalledRelease.find(fork, null, "V1.2.0")?.tag)
    }

    @Test
    fun givesUpWhenNothingMatches() {
        assertNull(InstalledRelease.find(fork, "unknown", "2.0"))
        assertNull(InstalledRelease.find(fork, null, null))
    }
}
