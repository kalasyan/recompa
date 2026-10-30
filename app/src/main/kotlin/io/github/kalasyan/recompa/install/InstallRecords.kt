package io.github.kalasyan.recompa.install

import android.content.Context

/**
 * What Recompa installed for each port: the package it turned out to be and the release tag.
 * [held] is set when the user picked an earlier release, so "update all" leaves it alone.
 * [linked] is set when the user picked an app installed outside Recompa as this port; its tag is empty.
 */
data class InstallRecord(
    val packageName: String,
    val tag: String,
    val repo: String,
    val held: Boolean = false,
    val linked: Boolean = false,
)

class InstallRecords(context: Context) {
    private val prefs = context.getSharedPreferences("install_records", Context.MODE_PRIVATE)

    fun get(portId: String): InstallRecord? {
        val pkg = prefs.getString("$portId.package", null) ?: return null
        return InstallRecord(
            packageName = pkg,
            tag = prefs.getString("$portId.tag", null).orEmpty(),
            repo = prefs.getString("$portId.repo", null).orEmpty(),
            held = prefs.getBoolean("$portId.held", false),
            linked = prefs.getBoolean("$portId.linked", false),
        )
    }

    fun put(portId: String, record: InstallRecord) {
        prefs.edit()
            .putString("$portId.package", record.packageName)
            .putString("$portId.tag", record.tag)
            .putString("$portId.repo", record.repo)
            .putBoolean("$portId.held", record.held)
            .putBoolean("$portId.linked", record.linked)
            .apply()
    }

    fun remove(portId: String) {
        prefs.edit()
            .remove("$portId.package")
            .remove("$portId.tag")
            .remove("$portId.repo")
            .remove("$portId.held")
            .remove("$portId.linked")
            .apply()
    }
}
