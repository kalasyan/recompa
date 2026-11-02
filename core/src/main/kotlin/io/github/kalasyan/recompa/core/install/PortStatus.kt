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

    /**
     * The package this version installs as is taken by [fork], another version of the game:
     * Android keeps one app per package, so that one has to go before this one can come in.
     */
    data class OtherFork(val fork: Fork) : PortStatus

    /** True when this version is on the device, whatever its release. */
    val installed: Boolean get() = this == UpToDate || this is UpdateAvailable || this == UnknownSigner
}

object PortStatusResolver {
    /**
     * The status of [fork], one version of a port.
     *
     * @param forks every version of the port, to recognise a build of another one in the same package.
     * @param installed the package [fork] installs as, found on the device, or null.
     * @param installedTag the release tag Recompa recorded when it installed [fork] there, or null.
     * @param claimedBy another version Recompa recorded installing in that same package, if any.
     */
    fun resolve(
        fork: Fork,
        forks: List<Fork>,
        installed: InstalledPackage?,
        installedTag: String?,
        claimedBy: Fork? = null,
    ): PortStatus {
        if (installed == null) return PortStatus.NotInstalled
        fun signs(candidate: Fork) = candidate.signerSha256 != null &&
            installed.signerSha256.any { it.equals(candidate.signerSha256, ignoreCase = true) }
        val owner = if (signs(fork)) fork else forks.firstOrNull(::signs) ?: claimedBy
        if (owner != null && owner.repo != fork.repo) return PortStatus.OtherFork(owner)
        // A certificate the catalog doesn't know, unless Recompa installed this build itself.
        if (owner == null && (fork.signerSha256 != null || installedTag == null && forks.any { it.signerSha256 != null })) {
            return PortStatus.UnknownSigner
        }
        val latest = fork.release?.tag ?: return PortStatus.UpToDate
        return if (installedTag == latest) PortStatus.UpToDate else PortStatus.UpdateAvailable(latest)
    }
}
