package io.github.kalasyan.recompa.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.install.ApkCheck
import io.github.kalasyan.recompa.core.install.ApkCheckResult
import io.github.kalasyan.recompa.core.install.PortStatus
import io.github.kalasyan.recompa.core.install.PortStatusResolver
import io.github.kalasyan.recompa.core.install.Sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

sealed interface InstallPhase {
    data object Idle : InstallPhase
    data class Downloading(val progress: Float?) : InstallPhase
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
    data object Storage : InstallError
    data class Other(val message: String?) : InstallError
}

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

    fun launchIntent(port: Port): Intent? =
        packageName(port)?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun uninstallIntent(port: Port): Intent? =
        packageName(port)?.let { Intent(Intent.ACTION_DELETE, Uri.parse("package:$it")) }

    fun dismiss(portId: String) = setPhase(portId, InstallPhase.Idle)

    fun install(port: Port, fork: Fork) {
        val release = fork.release ?: return
        val current = phaseOf(port.id)
        if (current !is InstallPhase.Failed && current !in IDLE_PHASES) return
        if (!canInstall()) {
            setPhase(port.id, InstallPhase.NeedsPermission)
            return
        }
        scope.launch {
            try {
                val apk = download(port.id, release.asset.url)
                setPhase(port.id, InstallPhase.Verifying)
                val (sha, info) = withContext(Dispatchers.IO) {
                    val sha = apk.inputStream().use { Sha256.hex(it) }
                    sha to context.packageManager.archiveInfo(apk.absolutePath)
                }
                if (info == null) {
                    fail(port.id, InstallError.InvalidApk, apk)
                    return@launch
                }
                val installed = context.packageManager.packageInfoOrNull(info.packageName)?.toInstalledPackage()
                val check = ApkCheck.check(release.asset, sha, fork, info.packageName, info.signerSha256(), installed)
                if (check != ApkCheckResult.Ok) {
                    fail(port.id, InstallError.Check(check), apk)
                    return@launch
                }
                setPhase(port.id, InstallPhase.Installing)
                synchronized(pending) { pending[port.id] = InstallRecord(info.packageName, release.tag, fork.repo) }
                withContext(Dispatchers.IO) { commitSession(port.id, info.packageName, apk) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                setPhase(port.id, InstallPhase.Failed(InstallError.Network))
            } catch (e: Exception) {
                setPhase(port.id, InstallPhase.Failed(InstallError.Other(e.message)))
            }
        }
    }

    internal fun onAwaitingUser(portId: String) = setPhase(portId, InstallPhase.AwaitingUser)

    internal fun onInstalled(portId: String) {
        synchronized(pending) { pending.remove(portId) }?.let { records.put(portId, it) }
        apkFile(portId).delete()
        setPhase(portId, InstallPhase.Idle)
        refresh()
    }

    internal fun onFailed(portId: String, status: Int, message: String?) {
        synchronized(pending) { pending.remove(portId) }
        apkFile(portId).delete()
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

    private suspend fun download(portId: String, url: String): File = withContext(Dispatchers.IO) {
        val target = apkFile(portId)
        target.parentFile?.mkdirs()
        setPhase(portId, InstallPhase.Downloading(null))
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total != null) {
                            val percent = (copied * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                setPhase(portId, InstallPhase.Downloading(percent / 100f))
                            }
                        }
                    }
                }
            }
        }
        target
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
        apk.delete()
        setPhase(portId, InstallPhase.Failed(error))
    }

    private fun isInstalled(pkg: String) = context.packageManager.packageInfoOrNull(pkg) != null

    private fun apkFile(portId: String) = File(context.cacheDir, "apks/$portId.apk")

    private fun phaseOf(portId: String) = _phases.value[portId] ?: InstallPhase.Idle

    private fun setPhase(portId: String, phase: InstallPhase) = _phases.update { it + (portId to phase) }

    private companion object {
        val IDLE_PHASES = setOf(InstallPhase.Idle, InstallPhase.NeedsPermission)
    }
}
