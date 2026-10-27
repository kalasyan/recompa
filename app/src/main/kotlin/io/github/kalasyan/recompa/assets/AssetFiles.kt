package io.github.kalasyan.recompa.assets

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.github.kalasyan.recompa.core.catalog.Asset
import io.github.kalasyan.recompa.core.catalog.Port
import io.github.kalasyan.recompa.core.rom.RomIdentifier
import io.github.kalasyan.recompa.core.rom.RomMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Prepares the game files a port needs (the user's own copy, never downloaded) and
 * hands them to the port in the way its manifest asks for.
 */
class AssetFiles(private val context: Context) {
    private val resolver get() = context.contentResolver

    private fun workDir(port: Port) = File(context.cacheDir, "assets/${port.id}")

    /** Copies the picked file, unzips and converts it, and checks it against the manifest. */
    suspend fun prepare(port: Port, asset: Asset, source: Uri): RomMatch = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(source) ?: throw IOException("cannot open $source")
        input.use { RomIdentifier.identify(asset, it, displayName(source) ?: asset.id, workDir(port)) }
    }

    /** Writes [file] to a document the user created with the system file picker. */
    suspend fun saveTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(target, "wt") ?: throw IOException("cannot write $target")
        output.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    /** Writes [file] as [fileName] inside a folder the user granted, replacing any file with that name. */
    suspend fun saveToFolder(file: File, fileName: String, tree: Uri) = withContext(Dispatchers.IO) {
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        findChild(tree, fileName)?.let { DocumentsContract.deleteDocument(resolver, it) }
        val created = DocumentsContract.createDocument(resolver, folder, "application/octet-stream", fileName)
            ?: throw IOException("cannot create $fileName")
        saveTo(file, created)
    }

    /** A share intent that sends [file] to the port's app, named [fileName]. */
    suspend fun shareIntent(port: Port, file: File, fileName: String, packageName: String?): Intent =
        withContext(Dispatchers.IO) {
            val shared = File(workDir(port), "share/$fileName")
            shared.parentFile?.mkdirs()
            file.copyTo(shared, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", shared)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(fileName, uri)
            if (packageName != null) send.setPackage(packageName) else Intent.createChooser(send, null)
        }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun findChild(tree: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
        }
        return null
    }
}
