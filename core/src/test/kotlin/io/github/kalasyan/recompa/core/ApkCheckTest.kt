package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import io.github.kalasyan.recompa.core.install.ApkCheck
import io.github.kalasyan.recompa.core.install.ApkCheckResult
import io.github.kalasyan.recompa.core.install.InstalledPackage
import io.github.kalasyan.recompa.core.install.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApkCheckTest {
    private val sha = "a".repeat(64)
    private val asset = ReleaseAsset("p.apk", "https://example.com/p.apk", sha256 = sha)
    private val fork = Fork(repo = "a/b", assetPattern = "\\.apk$", packageName = "com.example.port", signerSha256 = "aa")

    private fun check(
        asset: ReleaseAsset = this.asset,
        actual: String = sha,
        fork: Fork = this.fork,
        pkg: String = "com.example.port",
        signers: List<String> = listOf("AA"),
        installed: InstalledPackage? = null,
    ) = ApkCheck.check(asset, actual, fork, pkg, signers, installed)

    @Test
    fun acceptsMatchingApk() = assertEquals(ApkCheckResult.Ok, check())

    @Test
    fun acceptsUnpinnedForkWhenHashMatches() =
        assertEquals(ApkCheckResult.Ok, check(fork = Fork(repo = "a/b", assetPattern = "x"), pkg = "any.pkg", signers = listOf("zz")))

    @Test
    fun rejectsMissingOrWrongHash() {
        assertEquals(ApkCheckResult.HashUnavailable, check(asset = asset.copy(sha256 = null)))
        assertIs<ApkCheckResult.HashMismatch>(check(actual = "b".repeat(64)))
    }

    @Test
    fun rejectsWrongPackageOrSigner() {
        assertIs<ApkCheckResult.WrongPackage>(check(pkg = "com.other"))
        assertIs<ApkCheckResult.WrongSigner>(check(signers = listOf("bb")))
    }

    @Test
    fun rejectsConflictWithInstalledApp() {
        val installed = InstalledPackage("com.example.port", "1.0", listOf("cc"))
        assertIs<ApkCheckResult.ConflictsWithInstalled>(check(installed = installed))
        assertEquals(ApkCheckResult.Ok, check(installed = installed.copy(signerSha256 = listOf("aa"))))
    }

    @Test
    fun hashesStreamsAndBytesAlike() {
        val data = "hello".toByteArray()
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expected, Sha256.hex(data))
        assertEquals(expected, Sha256.hex(data.inputStream()))
    }
}
