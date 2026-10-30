package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.update.AppReleases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppReleaseTest {
    private fun release(prerelease: Boolean = false, asset: String = "recompa.apk") = """
        {"tag_name": "v0.2.0", "prerelease": $prerelease, "draft": false, "extra": 1,
         "assets": [{"name": "$asset", "size": 42,
                     "browser_download_url": "https://example.com/$asset",
                     "digest": "sha256:${"a".repeat(64)}"}]}
    """

    @Test
    fun parsesTheLatestRelease() {
        val release = assertNotNull(AppReleases.parse(release()))
        assertEquals("0.2.0", release.version)
        assertEquals("https://example.com/recompa.apk", release.apkUrl)
        assertEquals("a".repeat(64), release.sha256)
    }

    @Test
    fun ignoresPrereleasesAndReleasesWithoutTheApk() {
        assertNull(AppReleases.parse(release(prerelease = true)))
        assertNull(AppReleases.parse(release(asset = "notes.txt")))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(AppReleases.isNewer("0.10.0", "0.9.2"))
        assertTrue(AppReleases.isNewer("v1.0", "0.9.9"))
        assertFalse(AppReleases.isNewer("0.2.0", "0.2.0"))
        assertFalse(AppReleases.isNewer("0.2.0", "0.3.0-nightly.5"))
        assertTrue(AppReleases.isNewer("0.2.1", "0.2"))
    }

    @Test
    fun picksTheNewestNightlyWithTheApk() {
        fun entry(tag: String, asset: String = "recompa.apk") = """
            {"tag_name": "$tag", "prerelease": true,
             "assets": [{"name": "$asset", "browser_download_url": "https://example.com/$tag/$asset"}]}
        """
        val body = "[" + listOf(
            entry("v0.2.0"),
            entry("nightly-9"),
            entry("nightly-12", asset = "notes.txt"),
            entry("nightly-11"),
        ).joinToString(",") + "]"
        val nightly = assertNotNull(AppReleases.parseNewestNightly(body))
        assertEquals("nightly-11", nightly.tag)
        assertEquals("nightly 11", nightly.version)
        assertNull(AppReleases.parseNewestNightly("[" + entry("v0.2.0") + "]"))
    }

    @Test
    fun readsNightlyNumbers() {
        assertEquals(42, AppReleases.nightlyNumber("nightly-42"))
        assertNull(AppReleases.nightlyNumber("v0.2.0"))
        assertNull(AppReleases.nightlyNumber("nightly-x"))
    }
}
