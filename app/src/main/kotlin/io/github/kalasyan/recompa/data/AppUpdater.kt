package io.github.kalasyan.recompa.data

import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.core.catalog.Fork
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.catalog.Release
import io.github.kalasyan.recompa.core.catalog.ReleaseAsset
import io.github.kalasyan.recompa.core.catalog.Requirements
import io.github.kalasyan.recompa.core.update.AppRelease
import io.github.kalasyan.recompa.core.update.AppReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Looks for a newer stable release of Recompa on GitHub. */
class AppUpdater(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /**
     * The newer release, or null when there is none or it cannot be checked. Debug and
     * nightly builds are signed with another key and cannot update to a stable release.
     */
    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext null
        val request = Request.Builder()
            .url(AppReleases.LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                AppReleases.parse(response.body?.string().orEmpty())
            }
        }.getOrNull()?.takeIf { AppReleases.isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    companion object {
        const val UPDATE_ID = "recompa"

        /**
         * Recompa described as a port, so the install manager can download, verify and
         * install the update with the same checks as any port. The installed app's
         * signature must match, which ApkCheck enforces.
         */
        fun asPort(release: AppRelease, packageName: String): Port {
            val fork = Fork(
                repo = "kalasyan/recompa",
                assetPattern = AppReleases.APK_NAME,
                packageName = packageName,
                release = Release(
                    tag = release.tag,
                    name = release.tag,
                    asset = ReleaseAsset(AppReleases.APK_NAME, release.apkUrl, release.size, release.sha256),
                ),
            )
            return Port(
                id = UPDATE_ID,
                name = "Recompa",
                game = "Recompa",
                originalPlatform = "other",
                type = "source-port",
                forks = listOf(fork),
                requirements = Requirements(abi = emptyList(), minSdk = 26),
            )
        }
    }
}
