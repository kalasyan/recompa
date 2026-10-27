package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.AcceptedFile
import io.github.kalasyan.recompa.core.catalog.Asset
import io.github.kalasyan.recompa.core.catalog.Delivery
import io.github.kalasyan.recompa.core.catalog.DeliveryMode
import io.github.kalasyan.recompa.core.rom.N64ByteOrder
import io.github.kalasyan.recompa.core.rom.N64Rom
import io.github.kalasyan.recompa.core.rom.RomIdentifier
import io.github.kalasyan.recompa.core.rom.RomMatch
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RomTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // A fake 8-byte ROM in each byte order.
    private val z64 = bytes(0x80, 0x37, 0x12, 0x40, 0x01, 0x02, 0x03, 0x04)
    private val v64 = bytes(0x37, 0x80, 0x40, 0x12, 0x02, 0x01, 0x04, 0x03)
    private val n64 = bytes(0x40, 0x12, 0x37, 0x80, 0x04, 0x03, 0x02, 0x01)

    private fun asset(vararg sha1: String, convertFrom: List<String> = listOf("v64", "n64")) = Asset(
        id = "rom",
        label = "ROM",
        accepted = sha1.map { AcceptedFile(it) },
        convertFrom = convertFrom,
        delivery = Delivery(DeliveryMode.IN_APP_PICKER),
    )

    @Test
    fun detectsByteOrder() {
        assertEquals(N64ByteOrder.BIG_ENDIAN, N64Rom.detect(z64))
        assertEquals(N64ByteOrder.BYTE_SWAPPED, N64Rom.detect(v64))
        assertEquals(N64ByteOrder.LITTLE_ENDIAN, N64Rom.detect(n64))
        assertNull(N64Rom.detect(bytes(0, 0, 0, 0)))
    }

    @Test
    fun convertsToBigEndian() {
        assertContentEquals(z64, N64Rom.toBigEndian(v64, N64ByteOrder.BYTE_SWAPPED))
        assertContentEquals(z64, N64Rom.toBigEndian(n64, N64ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun acceptsConvertedRomByItsZ64Hash() {
        val sha = RomIdentifier.sha1Hex(z64)
        val match = assertIs<RomMatch.Accepted>(RomIdentifier.identify(asset(sha), v64))
        assertEquals(N64ByteOrder.BYTE_SWAPPED, match.convertedFrom)
        assertContentEquals(z64, match.bytes)
    }

    @Test
    fun doesNotConvertWhenManifestDoesNotAllowIt() {
        val sha = RomIdentifier.sha1Hex(z64)
        assertIs<RomMatch.WrongVersion>(RomIdentifier.identify(asset(sha, convertFrom = emptyList()), v64))
    }

    @Test
    fun reportsWrongVersionAndUnverifiable() {
        assertIs<RomMatch.WrongVersion>(RomIdentifier.identify(asset("0".repeat(40)), z64))
        assertIs<RomMatch.Unverifiable>(RomIdentifier.identify(asset(), z64))
    }
}
