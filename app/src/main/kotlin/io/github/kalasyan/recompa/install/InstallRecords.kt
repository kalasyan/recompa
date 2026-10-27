package io.github.kalasyan.recompa.install

import android.content.Context

/** What Recompa installed for each port: the package it turned out to be and the release tag. */
data class InstallRecord(val packageName: String, val tag: String, val repo: String)

class InstallRecords(context: Context) {
    private val prefs = context.getSharedPreferences("install_records", Context.MODE_PRIVATE)

    fun get(portId: String): InstallRecord? {
        val pkg = prefs.getString("$portId.package", null) ?: return null
        return InstallRecord(
            packageName = pkg,
            tag = prefs.getString("$portId.tag", null).orEmpty(),
            repo = prefs.getString("$portId.repo", null).orEmpty(),
        )
    }

    fun put(portId: String, record: InstallRecord) {
        prefs.edit()
            .putString("$portId.package", record.packageName)
            .putString("$portId.tag", record.tag)
            .putString("$portId.repo", record.repo)
            .apply()
    }

    fun remove(portId: String) {
        prefs.edit().remove("$portId.package").remove("$portId.tag").remove("$portId.repo").apply()
    }
}
