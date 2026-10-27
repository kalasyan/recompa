package io.github.kalasyan.recompa.core.install

import io.github.kalasyan.recompa.core.catalog.Fork

/** What the system reports about an installed package. */
data class InstalledPackage(
    val packageName: String,
    val versionName: String?,
    val signerSha256: List<String>,
)

sealed interface PortStatus {
    data object NotInstalled : PortStatus
    data object UpToDate : PortStatus
    data class UpdateAvailable(val tag: String) : PortStatus

    /** Installed with a certificate no catalog fork uses: never offer to update it. */
    data object UnknownSigner : PortStatus

    /** Installed from [fork], whose certificate differs from the recommended one. */
    data class OtherFork(val fork: Fork) : PortStatus
}

object PortStatusResolver {
    /**
     * @param recommended the fork the user would install now.
     * @param forks every fork of the port, to recognise builds installed from another one.
     * @param installed the package found on the device, or null.
     * @param installedTag the release tag Recompa recorded when it installed the package.
     */
    fun resolve(
        recommended: Fork,
        forks: List<Fork>,
        installed: InstalledPackage?,
        installedTag: String?,
    ): PortStatus {
        if (installed == null) return PortStatus.NotInstalled
        val owner = forks.firstOrNull { fork ->
            fork.signerSha256 != null && installed.signerSha256.any { it.equals(fork.signerSha256, ignoreCase = true) }
        }
        if (owner == null && forks.any { it.signerSha256 != null }) return PortStatus.UnknownSigner
        if (owner != null && owner.repo != recommended.repo) return PortStatus.OtherFork(owner)
        val latest = recommended.release?.tag ?: return PortStatus.UpToDate
        return if (installedTag == latest) PortStatus.UpToDate else PortStatus.UpdateAvailable(latest)
    }
}
