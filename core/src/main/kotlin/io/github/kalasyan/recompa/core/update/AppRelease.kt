package io.github.kalasyan.recompa.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A published version of Recompa itself, from GitHub's "latest release" API. */
data class AppRelease(
    val tag: String,
    val version: String,
    val apkUrl: String,
    val size: Long?,
    val sha256: String?,
)

object AppReleases {
    const val LATEST_URL = "https://api.github.com/repos/kalasyan/recompa/releases/latest"
    const val APK_NAME = "recompa.apk"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long? = null,
        val digest: String? = null,
    )

    /** Parses the API response; null for drafts, prereleases or a release without the APK. */
    fun parse(body: String): AppRelease? {
        val release = json.decodeFromString<GitHubRelease>(body)
        if (release.draft || release.prerelease) return null
        val apk = release.assets.firstOrNull { it.name == APK_NAME } ?: return null
        return AppRelease(
            tag = release.tagName,
            version = release.tagName.removePrefix("v"),
            apkUrl = apk.url,
            size = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
        )
    }

    /**
     * True when [candidate] is a newer version than [current], comparing the numeric
     * parts (`0.10.0` > `0.9.2`). Anything after a `-` (like `-nightly.12`) is ignored.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String) =
        version.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
}
