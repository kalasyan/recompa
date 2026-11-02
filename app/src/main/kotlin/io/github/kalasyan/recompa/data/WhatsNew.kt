package io.github.kalasyan.recompa.data

import android.content.Context
import io.github.kalasyan.recompa.BuildConfig
import io.github.kalasyan.recompa.core.update.NotesBlock
import io.github.kalasyan.recompa.core.update.ReleaseNotes
import io.github.kalasyan.recompa.install.packageInfoOrNull

/**
 * The release notes bundled in the APK (from `docs/release-notes`), and whether they still have
 * to be shown: once, the first time Recompa opens after an update, never after a new install.
 */
class WhatsNew(private val context: Context) {
    private val prefs = context.getSharedPreferences("whats_new", Context.MODE_PRIVATE)
    private val files = runCatching { context.assets.list(DIR)?.toList() }.getOrNull().orEmpty()

    /** The version whose notes this build shows, or null when it has none. */
    val version: String? = ReleaseNotes.versionFor(files, BuildConfig.VERSION_NAME, nightly = BuildConfig.DEBUG)

    /** [version]'s notes in [language], falling back to English. */
    fun notes(language: String): List<NotesBlock> {
        val version = version ?: return emptyList()
        val file = ReleaseNotes.fileFor(files, version, language) ?: return emptyList()
        return runCatching { context.assets.open("$DIR/$file").bufferedReader().use { it.readText() } }
            .map(ReleaseNotes::parse)
            .getOrDefault(emptyList())
    }

    /** True when the notes are due; a new install just records them as seen. */
    fun due(): Boolean {
        val seen = prefs.getString(SEEN, null)
        val info = context.packageManager.packageInfoOrNull(context.packageName)
        val fresh = info == null || info.firstInstallTime == info.lastUpdateTime
        if (seen == null && fresh && version != null) markSeen()
        return ReleaseNotes.shouldShow(version, seen, fresh)
    }

    fun markSeen() {
        prefs.edit().putString(SEEN, version).apply()
    }

    private companion object {
        const val DIR = "release-notes"
        const val SEEN = "seen_version"
    }
}
