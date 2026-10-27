package io.github.kalasyan.recompa.core.rom

import io.github.kalasyan.recompa.core.catalog.AcceptedFile
import io.github.kalasyan.recompa.core.catalog.Asset
import java.security.MessageDigest

/** Result of checking a user's file against a port's asset requirements. */
sealed interface RomMatch {
    /** The file is accepted. [bytes] is what should be delivered (already converted if needed). */
    data class Accepted(val file: AcceptedFile, val bytes: ByteArray, val convertedFrom: N64ByteOrder?) : RomMatch

    /** The file is readable but is not one of the accepted versions. */
    data class WrongVersion(val sha1: String) : RomMatch

    /** The manifest lists no accepted hashes yet, so the file cannot be verified. */
    data class Unverifiable(val sha1: String, val bytes: ByteArray) : RomMatch
}

object RomIdentifier {
    fun identify(asset: Asset, file: ByteArray): RomMatch {
        val order = N64Rom.detect(file)
        val canConvert = order != null && order != N64ByteOrder.BIG_ENDIAN &&
            order.extension in asset.convertFrom
        val normalized = if (canConvert) N64Rom.toBigEndian(file, order!!) else file
        val sha1 = sha1Hex(normalized)
        if (asset.accepted.isEmpty()) return RomMatch.Unverifiable(sha1, normalized)
        val match = asset.accepted.firstOrNull { it.sha1.equals(sha1, ignoreCase = true) }
            ?: return RomMatch.WrongVersion(sha1)
        return RomMatch.Accepted(match, normalized, if (canConvert) order else null)
    }

    fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
