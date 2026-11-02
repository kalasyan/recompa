package io.github.kalasyan.recompa.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.install.ApkCheck
import io.github.kalasyan.recompa.core.install.ApkCheckResult
import io.github.kalasyan.recompa.core.install.DownloadResume
import io.github.kalasyan.recompa.core.install.InstalledRelease
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.core.install.PortStatusResolver
import io.github.kalasyan.recompa.core.install.Sha256
import io.github.kalasyan.recompa.core.install.ZipEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

sealed interface InstallPhase {
    data object Idle : InstallPhase
    data class Downloading(val progress: Float?) : InstallPhase

    /** The user paused the download; the bytes so far stay on disk until it resumes or is cancelled. */
    data class Paused(val progress: Float?) : InstallPhase
    data object Verifying : InstallPhase
    data object Installing : InstallPhase
    data object AwaitingUser : InstallPhase
    data object NeedsPermission : InstallPhase
    data class Failed(val error: InstallError) : InstallPhase
}

sealed interface InstallError {
    data object Network : InstallError
    data object InvalidApk : InstallError
    data class Check(val result: ApkCheckResult) : InstallError

    /** The system refused the install, for example because of developer verification. */
    data class Blocked(val message: String?) : InstallError
    data object Aborted : InstallError
    data class Conflict(val message: String?) : InstallError
    data class Incompatible(val message: String?) : InstallError

    /** The release is older than the installed build: Android only accepts it after an uninstall. */
    data object Downgrade : InstallError
    data object Storage : InstallError
    data class Other(val message: String?) : InstallError
}

/** An installed app the user can pick as a port. */
data class LinkCandidate(val label: String, val packageName: String)

/**
 * Downloads, verifies and installs ports with PackageInstaller sessions, and reports
 * what is installed. One install runs per port at a time.
 */
class InstallManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val records = InstallRecords(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val pending = mutableMapOf<String, InstallRecord>()
    private val labels = ConcurrentHashMap<String, String>()

    /** What each port is installing, so a paused download can resume with the same release. */
    private val requests = ConcurrentHashMap<String, InstallRequest>()

    /** The last install of each port, so a failed one can be retried with "install anyway" or after an uninstall. */
    private val lastRequests = ConcurrentHashMap<String, InstallRequest>()

    /** Installs that wait for the user to uninstall the port's current build. */
    private val reinstalls = ConcurrentHashMap<String, InstallRequest>()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()

    private val _phases = MutableStateFlow<Map<String, InstallPhase>>(emptyMap())
    val phases: StateFlow<Map<String, InstallPhase>> = _phases.asStateFlow()

    /** Bumped whenever installed packages may have changed, so screens recompute statuses. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun refresh() = _revision.update { it + 1 }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** The package this port is installed as, from the catalog or from what Recompa installed. */
    fun packageName(port: Port): String? =
        port.forks.firstNotNullOfOrNull { it.packageName?.takeIf { pkg -> isInstalled(pkg) } }
            ?: records.get(port.id)?.packageName
            ?: port.recommendedFork?.packageName

    fun status(port: Port): PortStatus {
        val fork = port.recommendedFork ?: return PortStatus.NotInstalled
        val pkg = packageName(port) ?: return PortStatus.NotInstalled
        val installed = context.packageManager.packageInfoOrNull(pkg)?.toInstalledPackage()
        if (installed == null) {
            records.get(port.id)?.takeIf { it.packageName == pkg }?.let { records.remove(port.id) }
            return PortStatus.NotInstalled
        }
        return PortStatusResolver.resolve(fork, port.forks, installed, records.get(port.id)?.tag)
    }

    /**
     * Rebuilds the install records that are missing for ports already on the device, for
     * example after Recompa was uninstalled to switch between stable and nightly builds.
     * Without a record every such port looks out of date. The release is found by the hash of
     * the installed APK, or by its version name. Each package is only looked at once per
     * version, so a port that matches no release is not hashed again on every launch.
     */
    suspend fun recoverRecords(ports: List<Port>) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val checked = context.getSharedPreferences(RECOVERY_PREFS, Context.MODE_PRIVATE)
        var recovered = false
        for (port in ports) {
            if (port.id == SELF_ID || records.get(port.id) != null || phaseOf(port.id) !in IDLE_PHASES) continue
            val pkg = packageName(port) ?: continue
            val info = pm.packageInfoOrNull(pkg) ?: continue
            val stamp = "${info.versionCodeCompat}:${info.lastUpdateTime}:${port.recommendedFork?.release?.tag}"
            if (checked.getString("${port.id}.$pkg", null) == stamp) continue
            val signers = info.signerSha256()
            val candidates = port.forks.filter { fork ->
                (fork.packageName == null || fork.packageName == pkg) &&
                    (fork.signerSha256?.let { signer -> signers.any { it.equals(signer, ignoreCase = true) } }
                        ?: (fork.packageName == pkg))
            }
            if (candidates.isNotEmpty()) {
                val apkSha = info.applicationInfo?.sourceDir?.let { path ->
                    try {
                        File(path).inputStream().use { Sha256.hex(it) }
                    } catch (e: IOException) {
                        null
                    }
                }
                val match = candidates.firstNotNullOfOrNull { fork ->
                    InstalledRelease.find(fork, apkSha, info.versionName)?.let { fork to it }
                }
                if (match != null && records.get(port.id) == null) {
                    val (fork, release) = match
                    records.put(port.id, InstallRecord(pkg, release.tag, fork.repo))
                    recovered = true
                }
            }
            checked.edit().putString("${port.id}.$pkg", stamp).apply()
        }
        if (recovered) refresh()
    }

    /** The release tag Recompa installed, or null when it did not install this port. */
    fun installedTag(port: Port): String? = records.get(port.id)?.tag?.takeIf { it.isNotEmpty() }

    /** True when the user chose to stay on an earlier release of [port]. */
    fun isHeld(port: Port): Boolean = records.get(port.id)?.held == true

    /** Ports with an update that "update all" would install: not held on an earlier release. */
    fun pendingUpdates(ports: List<Port>): List<Port> =
        ports.filter { it.id != SELF_ID && status(it) is PortStatus.UpdateAvailable && !isHeld(it) }

    private val _updatingAll = MutableStateFlow(false)
    val updatingAll: StateFlow<Boolean> = _updatingAll.asStateFlow()

    /**
     * Updates [ports] one after another, so at most one confirmation shows at a time.
     * A port that fails is skipped; its error stays on its page.
     */
    fun updateAll(ports: List<Port>) {
        if (_updatingAll.value) return
        val queue = pendingUpdates(ports)
        if (queue.isEmpty()) return
        if (!canInstall()) {
            setPhase(queue.first().id, InstallPhase.NeedsPermission)
            return
        }
        _updatingAll.value = true
        scope.launch {
            try {
                for (port in queue) {
                    val fork = port.recommendedFork ?: continue
                    install(port, fork)
                    // A paused port waits for the user; the others go on.
                    phases.first { phase ->
                        phase[port.id].let { it == null || it in IDLE_PHASES || it is InstallPhase.Failed || it is InstallPhase.Paused }
                    }
                }
            } finally {
                _updatingAll.value = false
            }
        }
    }

    /**
     * Apps the user can link to a port by hand: those with a launcher icon, which are the ones
     * Recompa can see, without system apps or Recompa itself.
     */
    fun linkCandidates(): List<LinkCandidate> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.launcherActivities(launcher)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { LinkCandidate(it.loadLabel(pm).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * True when [packageName] looks like a build from one of the port's repositories: same
     * package (when the catalog knows it) and signed with the same certificate. Only then can
     * Recompa update it in place.
     */
    fun matchesCatalog(port: Port, packageName: String): Boolean {
        val installed = context.packageManager.packageInfoOrNull(packageName)?.toInstalledPackage() ?: return false
        return port.forks.any { fork ->
            val signer = fork.signerSha256 ?: return@any false
            (fork.packageName == null || fork.packageName == packageName) &&
                installed.signerSha256.any { it.equals(signer, ignoreCase = true) }
        }
    }

    /** Treats [packageName], installed outside Recompa, as this port from now on. */
    fun link(port: Port, packageName: String) {
        records.put(port.id, InstallRecord(packageName, tag = "", repo = "", linked = true))
        refresh()
    }

    fun unlink(port: Port) {
        records.remove(port.id)
        refresh()
    }

    /** True when the user picked the installed app by hand, rather than Recompa installing it. */
    fun isLinked(port: Port): Boolean {
        val record = records.get(port.id) ?: return false
        return record.linked && packageName(port) == record.packageName
    }

    fun launchIntent(port: Port): Intent? =
        packageName(port)?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun uninstallIntent(port: Port): Intent? =
        packageName(port)?.let { Intent(Intent.ACTION_DELETE, Uri.parse("package:$it")) }

    fun dismiss(portId: String) {
        reinstalls.remove(portId)
        setPhase(portId, InstallPhase.Idle)
    }

    /**
     * Retries the failed install of [portId] without comparing the download with the catalog:
     * the user chose to trust it, for example when the developer replaced the file of a release.
     */
    fun installAnyway(portId: String) {
        val request = lastRequests[portId] ?: return
        install(request.port, request.fork, request.release, trusted = true)
    }

    /**
     * For a build Android only installs once the current one is gone (another signer, or an
     * older version): returns the system uninstall screen and remembers the install, which
     * [resumeReinstall] starts when the port is no longer on the device.
     */
    fun uninstallToReinstall(port: Port): Intent? {
        val request = lastRequests[port.id] ?: return null
        val intent = uninstallIntent(port) ?: return null
        reinstalls[port.id] = request
        return intent
    }

    /** Starts the install that waited for an uninstall, once [port] is not installed any more. */
    fun resumeReinstall(port: Port) {
        if (!reinstalls.containsKey(port.id) || status(port) != PortStatus.NotInstalled) return
        val request = reinstalls.remove(port.id) ?: return
        install(request.port, request.fork, request.release, request.trusted)
    }

    /** True while an install waits for the user to uninstall [portId]. */
    fun awaitsUninstall(portId: String): Boolean = reinstalls.containsKey(portId)

    /** Stops [portId]'s download and keeps what it got so far, to carry on later with [resume]. */
    fun pause(portId: String) {
        // Set before cancelling, so the download that stops does not report a network error.
        // Only while downloading: once the file is complete, the install goes on.
        var paused = false
        _phases.update { phases ->
            val phase = phases[portId]
            paused = phase is InstallPhase.Downloading
            if (phase is InstallPhase.Downloading) phases + (portId to InstallPhase.Paused(phase.progress)) else phases
        }
        if (!paused) return
        calls.remove(portId)?.cancel()
        jobs[portId]?.cancel()
        DownloadService.notifyPaused(context, portId, label(portId))
    }

    /** Carries on a paused download from where it stopped. */
    fun resume(portId: String) {
        if (phaseOf(portId) !is InstallPhase.Paused) return
        val request = requests[portId] ?: return
        install(request.port, request.fork, request.release, request.trusted)
    }

    /** Drops a paused download and the bytes it got. */
    fun cancelDownload(portId: String) {
        if (phaseOf(portId) !is InstallPhase.Paused) return
        DownloadService.cancelPaused(context, portId)
        requests.remove(portId)
        listOf(apkFile(portId), zipFile(portId)).forEach { discard(it) }
        setPhase(portId, InstallPhase.Idle)
    }

    /** The port's name, for notifications about an install in progress. */
    fun label(portId: String): String = labels[portId] ?: portId

    /**
     * Installs [release] of [fork], the fork's latest by default. Installing an earlier
     * release marks the port as held, so "update all" does not undo the user's choice.
     */
    fun install(port: Port, fork: Fork, release: Release? = fork.release, trusted: Boolean = false) {
        release ?: return
        val current = phaseOf(port.id)
        if (current !is InstallPhase.Failed && current !is InstallPhase.Paused && current !in IDLE_PHASES) return
        if (!canInstall()) {
            setPhase(port.id, InstallPhase.NeedsPermission)
            return
        }
        val held = release.tag != fork.release?.tag
        // Set before the download starts, so callers waiting on the phase see the install begin.
        setPhase(port.id, InstallPhase.Downloading((current as? InstallPhase.Paused)?.progress))
        labels[port.id] = port.name
        val request = InstallRequest(port, fork, release, trusted)
        requests[port.id] = request
        lastRequests[port.id] = request
        reinstalls.remove(port.id)
        DownloadService.cancelPaused(context, port.id)
        DownloadService.start(context)
        // A download paused a moment ago may still be closing its file.
        val previous = jobs[port.id]
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.join()
                val entry = release.asset.entry
                val apk = apkFile(port.id)
                val downloaded = download(port.id, release.asset.url, if (entry != null) zipFile(port.id) else apk)
                // A pause that came as the last bytes arrived wins: the file stays for resume.
                if (!startVerifying(port.id)) return@launch
                val (sha, entrySha, info) = withContext(Dispatchers.IO) {
                    val sha = downloaded.inputStream().use { Sha256.hex(it) }
                    var entrySha: String? = null
                    if (entry != null) {
                        // Some ports only publish a ZIP; the catalog names the APK inside it.
                        apk.delete()
                        val found = (trusted || sha.equals(release.asset.sha256, ignoreCase = true)) &&
                            ZipEntries.extract(downloaded, entry.path, apk)
                        downloaded.delete()
                        if (found) entrySha = apk.inputStream().use { Sha256.hex(it) }
                    }
                    val info = if (apk.exists()) context.packageManager.archiveInfo(apk.absolutePath) else null
                    Triple(sha, entrySha, info)
                }
                val expected = release.asset.sha256
                if (!trusted && (expected == null || !expected.equals(sha, ignoreCase = true))) {
                    val result = if (expected == null) ApkCheckResult.HashUnavailable else ApkCheckResult.HashMismatch(expected, sha)
                    fail(port.id, InstallError.Check(result), apk)
                    return@launch
                }
                if (info == null) {
                    fail(port.id, InstallError.InvalidApk, apk)
                    return@launch
                }
                val installedInfo = context.packageManager.packageInfoOrNull(info.packageName)
                val installed = installedInfo?.toInstalledPackage()
                val check = ApkCheck.check(release.asset, sha, fork, info.packageName, info.signerSha256(), installed, entrySha, trusted)
                if (check != ApkCheckResult.Ok) {
                    fail(port.id, InstallError.Check(check), apk)
                    return@launch
                }
                if (installedInfo != null && info.versionCodeCompat < installedInfo.versionCodeCompat) {
                    fail(port.id, InstallError.Downgrade, apk)
                    return@launch
                }
                setPhase(port.id, InstallPhase.Installing)
                synchronized(pending) { pending[port.id] = InstallRecord(info.packageName, release.tag, fork.repo, held) }
                withContext(Dispatchers.IO) { commitSession(port.id, info.packageName, apk) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Cancelling the request on pause ends the download with an IOException too.
                if (phaseOf(port.id) !is InstallPhase.Paused) setPhase(port.id, InstallPhase.Failed(InstallError.Network))
            } catch (e: Exception) {
                setPhase(port.id, InstallPhase.Failed(InstallError.Other(e.message)))
            } finally {
                jobs.remove(port.id, job)
                if (phaseOf(port.id) !is InstallPhase.Paused) requests.remove(port.id, request)
            }
        }
        jobs[port.id] = job
        job.start()
    }

    internal fun onAwaitingUser(portId: String) = setPhase(portId, InstallPhase.AwaitingUser)

    internal fun onInstalled(portId: String) {
        synchronized(pending) { pending.remove(portId) }?.let { records.put(portId, it) }
        lastRequests.remove(portId)
        apkFile(portId).delete()
        DownloadService.cancelConfirm(context, portId)
        setPhase(portId, InstallPhase.Idle)
        refresh()
    }

    internal fun onFailed(portId: String, status: Int, message: String?) {
        synchronized(pending) { pending.remove(portId) }
        apkFile(portId).delete()
        DownloadService.cancelConfirm(context, portId)
        val error = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallError.Aborted
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallError.Blocked(message)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallError.Conflict(message)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallError.Incompatible(message)
            PackageInstaller.STATUS_FAILURE_INVALID -> InstallError.InvalidApk
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallError.Storage
            else -> InstallError.Other(message)
        }
        setPhase(portId, if (error == InstallError.Aborted) InstallPhase.Idle else InstallPhase.Failed(error))
        refresh()
    }

    /**
     * Downloads [url] into [target]. When [target] holds part of the same URL, from a pause or
     * a dropped connection, it asks only for the rest; the SHA-256 check covers the whole file.
     */
    private suspend fun download(portId: String, url: String, target: File): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val source = sourceFile(target)
        val savedUrl = if (source.exists()) source.readText() else null
        val offset = DownloadResume.offset(if (target.exists()) target.length() else 0, savedUrl, url)
        if (offset == 0L) source.writeText(url)
        // When the server does not take the range, start over.
        val done = fetch(portId, url, target, offset) || offset > 0 && fetch(portId, url, target, 0)
        if (!done) throw IOException("Unexpected answer to the download")
        source.delete()
        target
    }

    /**
     * Writes [url] into [target] from byte [offset]. Returns false when the server's answer
     * does not continue from there, so the caller can download from the start.
     */
    private suspend fun fetch(portId: String, url: String, target: File, offset: Long): Boolean {
        val request = Request.Builder().url(url)
            .apply { DownloadResume.rangeHeader(offset)?.let { header("Range", it) } }
            .build()
        val call = client.newCall(request)
        // pause() cancels the call, so a blocked read stops at once.
        calls[portId] = call
        try {
            // A pause that came while the request was being set up.
            currentCoroutineContext().ensureActive()
            call.execute().use { response ->
                val start = DownloadResume.bodyStart(response.code, response.header("Content-Range"), offset)
                    ?: if (offset > 0) return false else throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength().takeIf { it > 0 }?.plus(start)
                body.byteStream().use { input ->
                    FileOutputStream(target, start > 0).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = start
                        var lastReported = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total != null) {
                                val percent = (copied * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    reportProgress(portId, percent / 100f)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            calls.remove(portId, call)
        }
        return true
    }

    private fun startVerifying(portId: String): Boolean {
        var started = false
        _phases.update { phases ->
            started = phases[portId] is InstallPhase.Downloading
            if (started) phases + (portId to InstallPhase.Verifying) else phases
        }
        return started
    }

    /** Leaves a paused port alone: a last chunk read after the pause must not undo it. */
    private fun reportProgress(portId: String, progress: Float) = _phases.update {
        if (it[portId] is InstallPhase.Paused) it else it + (portId to InstallPhase.Downloading(progress))
    }

    /** Next to a partial download, the URL it came from. */
    private fun sourceFile(target: File) = File(target.path + ".src")

    private fun discard(file: File) {
        file.delete()
        sourceFile(file).delete()
    }

    private fun commitSession(portId: String, packageName: String, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Lets updates of ports Recompa installed skip the confirmation when the system allows it.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setPackage(context.packageName)
                .putExtra(InstallResultReceiver.EXTRA_PORT_ID, portId)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun fail(portId: String, error: InstallError, apk: File) {
        discard(apk)
        setPhase(portId, InstallPhase.Failed(error))
    }

    private fun isInstalled(pkg: String) = context.packageManager.packageInfoOrNull(pkg) != null

    private fun apkFile(portId: String) = File(context.cacheDir, "apks/$portId.apk")

    private fun zipFile(portId: String) = File(context.cacheDir, "apks/$portId.zip")

    private fun phaseOf(portId: String) = _phases.value[portId] ?: InstallPhase.Idle

    private fun setPhase(portId: String, phase: InstallPhase) = _phases.update { it + (portId to phase) }

    private data class InstallRequest(val port: Port, val fork: Fork, val release: Release, val trusted: Boolean = false)

    private companion object {
        val IDLE_PHASES = setOf(InstallPhase.Idle, InstallPhase.NeedsPermission)

        /** Recompa's own update goes through this manager too (see AppUpdater.UPDATE_ID). */
        const val SELF_ID = "recompa"
        private const val RECOVERY_PREFS = "record_recovery"
    }
}
