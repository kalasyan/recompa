package io.github.kalasyan.recompa.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The GitHub token a nightly build uses to read the private nightlies repo, pasted by the
 * user in the settings. It is kept in the app's no-backup folder, so it never leaves the
 * device in a backup. Stable builds never use it.
 */
object NightlyToken {
    private val _token = MutableStateFlow<String?>(null)
    private var loaded = false

    /** The saved token, or null. Changes when the user saves or removes it. */
    fun flow(context: Context): StateFlow<String?> {
        load(context)
        return _token.asStateFlow()
    }

    fun get(context: Context): String? {
        load(context)
        return _token.value
    }

    fun set(context: Context, token: String?) {
        val value = token?.trim()?.takeIf { it.isNotEmpty() }
        val file = file(context)
        if (value == null) file.delete() else file.writeText(value)
        synchronized(this) { loaded = true }
        _token.value = value
    }

    private fun load(context: Context) = synchronized(this) {
        if (loaded) return
        _token.value = file(context).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        loaded = true
    }

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, "nightly_token")
}
