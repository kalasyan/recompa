package io.github.kalasyan.recompa.install

import android.content.Context

/**
 * What Recompa installed for each version (fork) of a port: the package it turned out to be,
 * the release tag and the fork's repository.
 * [held] is set when the user picked an earlier release, so "update all" leaves it alone.
 * [linked] is set when the user picked an app installed outside Recompa as this version; its tag is empty.
 */
data class InstallRecord(
    val packageName: String,
    val tag: String,
    val repo: String,
    val held: Boolean = false,
    val linked: Boolean = false,
)

/**
 * Records are kept per port and fork (`<port>@<repo>`). Versions before players could pick a
 * fork kept one per port (`<port>`): it is still read, as the record of the fork it names, and
 * replaced by the new one the next time that fork is installed, linked or uninstalled.
 */
class InstallRecords(context: Context) {
    private val prefs = context.getSharedPreferences("install_records", Context.MODE_PRIVATE)

    fun get(portId: String, repo: String): InstallRecord? =
        read(key(portId, repo)) ?: legacy(portId)?.takeIf { it.repo == repo }

    /** The record of a version of Recompa that kept one per port, if there is one. */
    fun legacy(portId: String): InstallRecord? = read(portId)

    /** Records [record] for its fork; other forks of the port recorded in the same package lost it. */
    fun put(portId: String, record: InstallRecord) {
        val replaced = prefs.all.filter { (k, v) -> k.startsWith("$portId@") && k.endsWith(".package") && v == record.packageName }
            .keys.map { it.removeSuffix(".package") }
        replaced.forEach(::delete)
        write(key(portId, record.repo), record)
        legacy(portId)?.let { old -> if (old.repo == record.repo || old.repo.isEmpty() || old.packageName == record.packageName) delete(portId) }
    }

    fun remove(portId: String, repo: String) {
        delete(key(portId, repo))
        legacy(portId)?.let { old -> if (old.repo == repo || old.repo.isEmpty()) delete(portId) }
    }

    private fun key(portId: String, repo: String) = "$portId@$repo"

    private fun read(key: String): InstallRecord? {
        val pkg = prefs.getString("$key.package", null) ?: return null
        return InstallRecord(
            packageName = pkg,
            tag = prefs.getString("$key.tag", null).orEmpty(),
            repo = prefs.getString("$key.repo", null).orEmpty(),
            held = prefs.getBoolean("$key.held", false),
            linked = prefs.getBoolean("$key.linked", false),
        )
    }

    private fun write(key: String, record: InstallRecord) {
        prefs.edit()
            .putString("$key.package", record.packageName)
            .putString("$key.tag", record.tag)
            .putString("$key.repo", record.repo)
            .putBoolean("$key.held", record.held)
            .putBoolean("$key.linked", record.linked)
            .apply()
    }

    private fun delete(key: String) {
        prefs.edit()
            .remove("$key.package")
            .remove("$key.tag")
            .remove("$key.repo")
            .remove("$key.held")
            .remove("$key.linked")
            .apply()
    }
}
