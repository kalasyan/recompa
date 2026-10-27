package io.github.kalasyan.recompa.core.install

import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import java.io.InputStream
import java.security.MessageDigest

sealed interface ApkCheckResult {
    data object Ok : ApkCheckResult

    /** The catalog has no SHA-256 for this asset, so the download cannot be trusted. */
    data object HashUnavailable : ApkCheckResult
    data class HashMismatch(val expected: String, val actual: String) : ApkCheckResult
    data class WrongPackage(val expected: String, val actual: String) : ApkCheckResult
    data class WrongSigner(val expected: String, val actual: List<String>) : ApkCheckResult

    /** An app with the same package but another certificate is installed: Android would refuse the update. */
    data class ConflictsWithInstalled(val installedSigners: List<String>) : ApkCheckResult
}

/** Everything a downloaded APK must pass before Recompa hands it to the system installer. */
object ApkCheck {
    fun check(
        asset: ReleaseAsset,
        actualSha256: String,
        fork: Fork,
        apkPackage: String,
        apkSigners: List<String>,
        installed: InstalledPackage?,
    ): ApkCheckResult {
        val expected = asset.sha256 ?: return ApkCheckResult.HashUnavailable
        if (!expected.equals(actualSha256, ignoreCase = true)) {
            return ApkCheckResult.HashMismatch(expected, actualSha256)
        }
        fork.packageName?.let { if (it != apkPackage) return ApkCheckResult.WrongPackage(it, apkPackage) }
        fork.signerSha256?.let { signer ->
            if (apkSigners.none { it.equals(signer, ignoreCase = true) }) {
                return ApkCheckResult.WrongSigner(signer, apkSigners)
            }
        }
        if (installed != null && installed.packageName == apkPackage && installed.signerSha256.isNotEmpty() &&
            installed.signerSha256.none { s -> apkSigners.any { it.equals(s, ignoreCase = true) } }
        ) {
            return ApkCheckResult.ConflictsWithInstalled(installed.signerSha256)
        }
        return ApkCheckResult.Ok
    }
}

object Sha256 {
    fun hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Hashes [input] to the end without loading it in memory. The stream is not closed. */
    fun hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
