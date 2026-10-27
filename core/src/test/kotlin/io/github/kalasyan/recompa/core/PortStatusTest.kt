package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import io.github.kalasyan.recompa.core.install.InstalledPackage
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.core.install.PortStatusResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class PortStatusTest {
    private fun fork(repo: String, signer: String?, tag: String = "v2") = Fork(
        repo = repo,
        assetPattern = "\\.apk$",
        packageName = "com.example.port",
        signerSha256 = signer,
        release = Release(tag, tag, asset = ReleaseAsset("p.apk", "https://example.com/p.apk")),
    )

    private val main = fork("main/port", "aa")
    private val alt = fork("alt/port", "bb")
    private fun installed(signer: String) = InstalledPackage("com.example.port", "1.0", listOf(signer))

    @Test
    fun notInstalled() {
        assertEquals(PortStatus.NotInstalled, PortStatusResolver.resolve(main, listOf(main, alt), null, null))
    }

    @Test
    fun upToDateAndUpdateAvailable() {
        assertEquals(PortStatus.UpToDate, PortStatusResolver.resolve(main, listOf(main), installed("aa"), "v2"))
        assertEquals(PortStatus.UpdateAvailable("v2"), PortStatusResolver.resolve(main, listOf(main), installed("aa"), "v1"))
    }

    @Test
    fun detectsOtherForkAndUnknownSigner() {
        assertEquals(PortStatus.OtherFork(alt), PortStatusResolver.resolve(main, listOf(main, alt), installed("bb"), null))
        assertEquals(PortStatus.UnknownSigner, PortStatusResolver.resolve(main, listOf(main, alt), installed("cc"), null))
    }
}
