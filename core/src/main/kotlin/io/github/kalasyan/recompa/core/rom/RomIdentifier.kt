package io.github.kalasyan.recompa.core.rom

import io.github.kalasyan.recompa.core.catalog.AcceptedFile
import io.github.kalasyan.recompa.core.catalog.Asset
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Result of checking a user's file against a port's asset requirements. */
sealed interface RomMatch {
    /** The file is accepted. [file] is what should be delivered, already extracted and converted. */
    data class Accepted(
        val accepted: AcceptedFile,
        val file: File,
        val fileName: String,
        val convertedFrom: N64ByteOrder?,
    ) : RomMatch

    /** The file is readable but is not one of the accepted versions. */
    data class WrongVersion(val sha1: String) : RomMatch

    /** The manifest lists no accepted hashes yet, so the file cannot be verified. */
    data class Unverifiable(val sha1: String, val file: File, val fileName: String) : RomMatch

    /** The file is a ZIP the port does not take, or a ZIP with nothing usable inside. */
    data object BadArchive : RomMatch
}

/**
 * Prepares a file the user picked for a port: unzips it when the manifest allows it,
 * converts N64 ROMs to `.z64`, and checks the SHA-1 of the result against the manifest.
 * Works on files so large assets (hundreds of MB) never sit in memory.
 */
object RomIdentifier {
    // A ZIP starts with a local file header, or with the end record when it is empty.
    private val ZIP_MAGICS = listOf(byteArrayOf(0x50, 0x4b, 0x03, 0x04), byteArrayOf(0x50, 0x4b, 0x05, 0x06))

    fun identify(asset: Asset, input: InputStream, originalName: String, workDir: File): RomMatch {
        workDir.mkdirs()
        val source = File(workDir, "${asset.id}.source")
        val output = File(workDir, "${asset.id}.prepared")
        try {
            source.outputStream().use { input.copyTo(it) }
            var current = source
            var name = originalName.substringAfterLast('/')

            if (ZIP_MAGICS.any { header(current).startsWith(it) }) {
                if (!asset.zipOk) return RomMatch.BadArchive
                val extracted = File(workDir, "${asset.id}.unzipped")
                name = extractLargestEntry(current, extracted) ?: return RomMatch.BadArchive
                current = extracted
            }

            val order = N64Rom.detect(header(current))
            var convertedFrom: N64ByteOrder? = null
            if (order != null && order != N64ByteOrder.BIG_ENDIAN && order.extension in asset.convertFrom) {
                val converted = File(workDir, "${asset.id}.z64")
                current.inputStream().use { i -> converted.outputStream().use { o -> N64Rom.copyAsBigEndian(i, o, order) } }
                if (current != source) current.delete()
                current = converted
                convertedFrom = order
            }

            val fileName = asset.delivery.filename ?: when {
                order != null -> name.substringBeforeLast('.') + ".z64"
                else -> name
            }
            val sha1 = sha1Hex(current)
            if (asset.accepted.isEmpty()) {
                return RomMatch.Unverifiable(sha1, moveTo(current, output), fileName)
            }
            val match = asset.accepted.firstOrNull { it.sha1.equals(sha1, ignoreCase = true) }
                ?: return RomMatch.WrongVersion(sha1)
            return RomMatch.Accepted(match, moveTo(current, output), fileName, convertedFrom)
        } finally {
            workDir.listFiles()?.filter { it.name.startsWith("${asset.id}.") && it != output }?.forEach { it.delete() }
        }
    }

    fun sha1Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

    fun sha1Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun header(file: File): ByteArray = file.inputStream().use { it.readNBytes(4) }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    /** Extracts the biggest file in the archive; returns its name, or null if there is none. */
    private fun extractLargestEntry(zip: File, target: File): String? = ZipFile(zip).use { archive ->
        val entry = archive.entries().asSequence()
            .filter { !it.isDirectory && !it.name.substringAfterLast('/').startsWith(".") }
            .maxByOrNull { it.size } ?: return null
        archive.getInputStream(entry).use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        entry.name.substringAfterLast('/')
    }

    private fun moveTo(file: File, target: File): File {
        if (file != target) {
            target.delete()
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
        return target
    }
}
