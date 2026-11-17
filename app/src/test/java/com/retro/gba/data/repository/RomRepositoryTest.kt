package com.retro.gba.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomRepositoryTest {

    private fun createSyntheticGbaRom(title: String = "POKEMON EMER", extraSize: Int = 512): ByteArray {
        val bytes = ByteArray(maxOf(256, extraSize)) { (it % 251).toByte() }
        bytes.fill(0, fromIndex = 0xA0, toIndex = 0xAC)
        val titleBytes = title.toByteArray(StandardCharsets.US_ASCII)
        val copyLen = minOf(titleBytes.size, 12)
        System.arraycopy(titleBytes, 0, bytes, 0xA0, copyLen)
        return bytes
    }

    private fun createZipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) {
                    zos.write(content)
                }
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun expectedMd5(bytes: ByteArray): String {
        return MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testIsZipMagicBytesDetection() {
        assertTrue(RomRepository.isZipMagicBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
        assertFalse(RomRepository.isZipMagicBytes(byteArrayOf(0x50, 0x4B, 0x03)))
        assertFalse(RomRepository.isZipMagicBytes(byteArrayOf(0x00, 0x00, 0x00, 0xEA.toByte())))
    }

    @Test
    fun testPlainGbaStreamMd5AndBytesExtraction() {
        val rom = createSyntheticGbaRom("METROID ZERO", 1024)
        val expectedHash = expectedMd5(rom)

        val meta = RomRepository.extractRomMetadataFromStream(ByteArrayInputStream(rom), "metroid.gba")
        assertNotNull(meta)
        assertEquals(expectedHash, meta!!.md5Hex)
        assertEquals("METROID ZERO", meta.headerTitle)

        assertEquals(expectedHash, RomRepository.calculateMd5FromBytes(rom, "metroid.gba"))
        assertArrayEquals(rom, RomRepository.readRomBytesFromStream(ByteArrayInputStream(rom), "metroid.gba"))
    }

    @Test
    fun testZipWithReadmeAndGbaExtractsGbaByExtensionAndByMagicBytes() {
        val rom = createSyntheticGbaRom("GOLDEN SUN", 2048)
        val readme = "Read this first!".toByteArray(StandardCharsets.UTF_8)
        val zipBytes = createZipBytes(
            listOf(
                "docs/" to ByteArray(0),
                "docs/readme.txt" to readme,
                "roms/Golden Sun (USA).gba" to rom
            )
        )
        val expectedHash = expectedMd5(rom)

        // Detecção por extensão .zip
        assertEquals(expectedHash, RomRepository.calculateMd5FromBytes(zipBytes, "golden_sun.zip"))
        assertArrayEquals(rom, RomRepository.readRomBytesFromStream(ByteArrayInputStream(zipBytes), "golden_sun.zip"))

        // Detecção sem extensão (ex.: content:// URI opaca) via magic bytes 0x50 0x4B 0x03 0x04
        val metaNoHint = RomRepository.extractRomMetadataFromStream(ByteArrayInputStream(zipBytes), null)
        assertNotNull(metaNoHint)
        assertEquals(expectedHash, metaNoHint!!.md5Hex)
        assertEquals("GOLDEN SUN", metaNoHint.headerTitle)
        assertEquals("roms/Golden Sun (USA).gba", metaNoHint.entryName)
        assertArrayEquals(rom, RomRepository.readRomBytesFromStream(ByteArrayInputStream(zipBytes), null))
    }

    @Test
    fun testZipIgnoresMacOsxMetadataAndSupportsAgbAndBin() {
        val fakeMacEntry = ByteArray(512) { 0x11 }
        val agbRom = createSyntheticGbaRom("ZELDA MINISH", 768)
        val zipWithAgb = createZipBytes(
            listOf(
                "__MACOSX/._zelda.agb" to fakeMacEntry,
                "zelda.agb" to agbRom
            )
        )
        assertEquals(expectedMd5(agbRom), RomRepository.calculateMd5FromBytes(zipWithAgb, null))
        assertArrayEquals(agbRom, RomRepository.readRomBytesFromStream(ByteArrayInputStream(zipWithAgb), null))

        val binRom = createSyntheticGbaRom("MARIO KART", 640)
        val zipWithBin = createZipBytes(listOf("kart.bin" to binRom))
        assertEquals(expectedMd5(binRom), RomRepository.calculateMd5FromBytes(zipWithBin, "kart.zip"))
        assertArrayEquals(binRom, RomRepository.readRomBytesFromStream(ByteArrayInputStream(zipWithBin), "kart.zip"))
    }

    @Test
    fun testZipWithoutKnownExtensionFallsBackToLargestValidEntry() {
        val smallTxt = "small info".toByteArray(StandardCharsets.UTF_8)
        val largerPayload = createSyntheticGbaRom("ADVANCE WARS", 1024)
        val zipBytes = createZipBytes(
            listOf(
                "info.txt" to smallTxt,
                "game_payload.dat" to largerPayload
            )
        )

        assertEquals(expectedMd5(largerPayload), RomRepository.calculateMd5FromBytes(zipBytes, "pack.zip"))
        assertArrayEquals(largerPayload, RomRepository.readRomBytesFromStream(ByteArrayInputStream(zipBytes), "pack.zip"))
    }

    @Test
    fun testEmptyZipReturnsNullMd5AndEmptyBytes() {
        val emptyZip = createZipBytes(listOf("empty_dir/" to ByteArray(0)))
        assertNull(RomRepository.calculateMd5FromBytes(emptyZip, "empty.zip"))
        assertEquals(0, RomRepository.readRomBytesFromStream(ByteArrayInputStream(emptyZip), "empty.zip").size)
    }

    @Test
    fun testFallbackDisplayNameExtraction() {
        assertEquals(
            "Emerald.zip",
            RomRepository.extractFallbackDisplayName("primary:Download/Emerald.zip", null)
        )
        assertEquals(
            "Metroid Fusion.gba",
            RomRepository.extractFallbackDisplayName("1234", "Metroid Fusion.gba")
        )
        assertEquals(
            "Castlevania.gba",
            RomRepository.extractFallbackDisplayName("Roms/Castlevania.gba", "game.gba")
        )
        assertEquals(
            "game.gba",
            RomRepository.extractFallbackDisplayName(null, null)
        )
    }

    @Test
    fun testExtractGbaTitleFromHeaderTruncatesAtFirstNulByte() {
        val header = ByteArray(192) { 0xFF.toByte() }
        val titleBytes = "GOLDEN SUN".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, header, 0xA0, titleBytes.size)
        header[0xAA] = 0x00
        header[0xAB] = 0xAA.toByte()

        assertEquals("GOLDEN SUN", RomRepository.extractGbaTitleFromHeader(header))
    }
}
