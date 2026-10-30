package io.github.kalasyan.recompa.install

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import io.github.kalasyan.recompa.core.install.InstalledPackage
import io.github.kalasyan.recompa.core.install.Sha256

@Suppress("DEPRECATION")
private val SIGNATURE_FLAGS: Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
    else PackageManager.GET_SIGNATURES

@Suppress("DEPRECATION")
fun PackageManager.packageInfoOrNull(packageName: String): PackageInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(SIGNATURE_FLAGS.toLong()))
    } else {
        getPackageInfo(packageName, SIGNATURE_FLAGS)
    }
} catch (e: PackageManager.NameNotFoundException) {
    null
}

@Suppress("DEPRECATION")
fun PackageManager.archiveInfo(path: String): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(SIGNATURE_FLAGS.toLong()))
    } else {
        getPackageArchiveInfo(path, SIGNATURE_FLAGS)
    }

@Suppress("DEPRECATION")
fun PackageManager.installedPackagesWithSigners(): List<PackageInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledPackages(PackageManager.PackageInfoFlags.of(SIGNATURE_FLAGS.toLong()))
    } else {
        getInstalledPackages(SIGNATURE_FLAGS)
    }

@Suppress("DEPRECATION")
fun PackageManager.launcherActivities(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        queryIntentActivities(intent, 0)
    }

/** SHA-256 of every certificate that signs the package, lowercase hex. */
@Suppress("DEPRECATION")
fun PackageInfo.signerSha256(): List<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = signingInfo ?: return emptyList()
        if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
    } else {
        signatures
    }
    return signatures.orEmpty().map { Sha256.hex(it.toByteArray()) }.distinct()
}

@Suppress("DEPRECATION")
val PackageInfo.versionCodeCompat: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

fun PackageInfo.toInstalledPackage(): InstalledPackage =
    InstalledPackage(packageName, versionName, signerSha256())
