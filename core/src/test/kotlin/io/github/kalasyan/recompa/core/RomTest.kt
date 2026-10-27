package io.github.kalasyan.recompa.core

import io.github.kalasyan.recompa.core.catalog.AcceptedFile
import io.github.kalasyan.recompa.core.catalog.Asset
import io.github.kalasyan.recompa.core.catalog.Delivery
import io.github.kalasyan.recompa.core.catalog.DeliveryMode
import io.github.kalasyan.recompa.core.rom.N64ByteOrder
import io.github.kalasyan.recompa.core.rom.N64Rom
import io.github.kalasyan.recompa.core.rom.RomIdentifier
import io.github.kalasyan.recompa.core.rom.RomMatch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
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

    private val workDir: File = Files.createTempDirectory("recompa-rom").toFile()

    @AfterTest
    fun cleanUp() {
        workDir.deleteRecursively()
    }

    private fun asset(
        vararg sha1: String,
        convertFrom: List<String> = listOf("v64", "n64"),
        zipOk: Boolean = true,
        filename: String? = null,
    ) = Asset(
        id = "rom",
        label = "ROM",
        accepted = sha1.map { AcceptedFile(it) },
        convertFrom = convertFrom,
        zipOk = zipOk,
        delivery = Delivery(DeliveryMode.IN_APP_PICKER, filename = filename),
    )

    private fun identify(asset: Asset, data: ByteArray, name: String = "game.v64") =
        RomIdentifier.identify(asset, ByteArrayInputStream(data), name, workDir)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

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
        val out = ByteArrayOutputStream()
        N64Rom.copyAsBigEndian(ByteArrayInputStream(n64), out, N64ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(z64, out.toByteArray())
    }

    @Test
    fun acceptsConvertedRomByItsZ64Hash() {
        val sha = RomIdentifier.sha1Hex(z64)
        val match = assertIs<RomMatch.Accepted>(identify(asset(sha), v64))
        assertEquals(N64ByteOrder.BYTE_SWAPPED, match.convertedFrom)
        assertEquals("game.z64", match.fileName)
        assertContentEquals(z64, match.file.readBytes())
        assertEquals(listOf(match.file), workDir.listFiles()!!.toList())
    }

    @Test
    fun doesNotConvertWhenManifestDoesNotAllowIt() {
        val sha = RomIdentifier.sha1Hex(z64)
        assertIs<RomMatch.WrongVersion>(identify(asset(sha, convertFrom = emptyList()), v64))
    }

    @Test
    fun reportsWrongVersionAndUnverifiable() {
        assertIs<RomMatch.WrongVersion>(identify(asset("0".repeat(40)), z64))
        val match = assertIs<RomMatch.Unverifiable>(identify(asset(), z64))
        assertEquals(RomIdentifier.sha1Hex(z64), match.sha1)
    }

    @Test
    fun extractsTheRomFromAZip() {
        val sha = RomIdentifier.sha1Hex(z64 + z64)
        val archive = zip("readme.txt" to bytes(1, 2), "roms/Game (USA).n64" to n64 + n64)
        val match = assertIs<RomMatch.Accepted>(identify(asset(sha), archive, "game.zip"))
        assertEquals("Game (USA).z64", match.fileName)
        assertContentEquals(z64 + z64, match.file.readBytes())
    }

    @Test
    fun rejectsZipWhenNotAllowedAndUsesManifestFileName() {
        assertIs<RomMatch.BadArchive>(identify(asset(zipOk = false), zip("a.bin" to z64), "a.zip"))
        assertIs<RomMatch.BadArchive>(identify(asset(), zip(), "empty.zip"))
        val data = bytes(1, 2, 3)
        val match = assertIs<RomMatch.Unverifiable>(identify(asset(filename = "Data.rsdk"), data, "x.bin"))
        assertEquals("Data.rsdk", match.fileName)
    }
}
