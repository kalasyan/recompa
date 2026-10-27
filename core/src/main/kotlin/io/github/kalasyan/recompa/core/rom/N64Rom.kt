package io.github.kalasyan.recompa.core.rom

import java.io.InputStream
import java.io.OutputStream

/**
 * N64 ROMs circulate in three byte orders. Ports expect big-endian `.z64`,
 * so Recompa normalizes `.v64` (byte-swapped) and `.n64` (little-endian) files.
 */
enum class N64ByteOrder(val extension: String) {
    BIG_ENDIAN("z64"),
    BYTE_SWAPPED("v64"),
    LITTLE_ENDIAN("n64"),
}

object N64Rom {
    private val Z64_MAGIC = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40)
    private val V64_MAGIC = byteArrayOf(0x37, 0x80.toByte(), 0x40, 0x12)
    private val N64_MAGIC = byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte())

    /** Detects the byte order from the first four bytes, or null if it is not an N64 ROM. */
    fun detect(header: ByteArray): N64ByteOrder? {
        if (header.size < 4) return null
        val magic = header.copyOfRange(0, 4)
        return when {
            magic.contentEquals(Z64_MAGIC) -> N64ByteOrder.BIG_ENDIAN
            magic.contentEquals(V64_MAGIC) -> N64ByteOrder.BYTE_SWAPPED
            magic.contentEquals(N64_MAGIC) -> N64ByteOrder.LITTLE_ENDIAN
            else -> null
        }
    }

    /** Returns a big-endian (`.z64`) copy of [rom]. The input is not modified. */
    fun toBigEndian(rom: ByteArray, order: N64ByteOrder): ByteArray {
        require(rom.size % 4 == 0) { "N64 ROM size must be a multiple of 4 bytes" }
        val out = rom.copyOf()
        when (order) {
            N64ByteOrder.BIG_ENDIAN -> Unit
            N64ByteOrder.BYTE_SWAPPED -> for (i in out.indices step 2) {
                out[i] = rom[i + 1]; out[i + 1] = rom[i]
            }
            N64ByteOrder.LITTLE_ENDIAN -> for (i in out.indices step 4) {
                out[i] = rom[i + 3]; out[i + 1] = rom[i + 2]; out[i + 2] = rom[i + 1]; out[i + 3] = rom[i]
            }
        }
        return out
    }

    /** Streams [input] to [output] in big-endian order. Returns the number of bytes written. */
    fun copyAsBigEndian(input: InputStream, output: OutputStream, order: N64ByteOrder): Long {
        val buffer = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val read = input.readNBytes(buffer, 0, buffer.size)
            if (read == 0) break
            require(read % 4 == 0) { "N64 ROM size must be a multiple of 4 bytes" }
            val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
            output.write(toBigEndian(chunk, order))
            total += read
        }
        return total
    }
}
