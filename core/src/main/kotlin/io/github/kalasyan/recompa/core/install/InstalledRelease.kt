package io.github.kalasyan.recompa.core.install

import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Release

/**
 * Works out which catalog release an installed port is, when Recompa has no record of
 * installing it: for example after Recompa was uninstalled to switch between stable and
 * nightly builds, which wipes its data but leaves the ports installed.
 */
object InstalledRelease {
    /**
     * @param apkSha256 SHA-256 of the installed base APK, or null if it could not be read.
     * @param versionName the installed package's version name.
     * @return the release whose APK has that hash, else the one whose tag reads as that
     *   version, else null.
     */
    fun find(fork: Fork, apkSha256: String?, versionName: String?): Release? {
        val releases = listOfNotNull(fork.release) + fork.history
        if (apkSha256 != null) {
            releases.firstOrNull { release ->
                val expected = release.asset.entry?.sha256 ?: release.asset.sha256
                expected != null && expected.equals(apkSha256, ignoreCase = true)
            }?.let { return it }
        }
        val version = versionName?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return null
        return releases.firstOrNull { normalize(it.tag) == version }
    }

    private fun normalize(version: String): String =
        version.trim().lowercase().removePrefix("version").trim().removePrefix("v").trim()
}
