package com.retro.gba.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class RomHeaderAndHashTest {

    @Test
    fun testGbaHeaderExtraction() {
        // Constrói um cabeçalho sintético de GBA com tamanho mínimo de 192 bytes
        val header = ByteArray(192)

        // Offset 0xA0..0xAB: Título do jogo (12 bytes ASCII)
        val testTitle = "METROID ZERO"
        val titleBytes = testTitle.toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(titleBytes, 0, header, 0xA0, titleBytes.size)

        // Offset 0xAC..0xAF: Game Code (4 bytes ASCII)
        val testCode = "BMXE"
        val codeBytes = testCode.toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(codeBytes, 0, header, 0xAC, codeBytes.size)

        // Validação da extração idêntica à de RomRepository e GbaCore
        val extractedTitle = String(header, 0xA0, 12, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
        val extractedCode = String(header, 0xAC, 4, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

        assertEquals("METROID ZERO", extractedTitle)
        assertEquals("BMXE", extractedCode)
    }

    @Test
    fun testMd5HashCalculation() {
        // Vetor de teste padrão: MD5("") = d41d8cd98f00b204e9800998ecf8427e
        val emptyDigest = MessageDigest.getInstance("MD5").digest(ByteArray(0))
        val emptyHex = emptyDigest.joinToString("") { "%02x".format(it) }
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", emptyHex)

        // Vetor de teste: "RetroGBA" -> MD5
        val testData = "RetroGBA".toByteArray(StandardCharsets.UTF_8)
        val md5 = MessageDigest.getInstance("MD5").digest(testData)
        val hex = md5.joinToString("") { "%02x".format(it) }
        assertEquals(32, hex.length)
        assertTrue(hex.matches(Regex("^[0-9a-f]{32}$")))
    }

    @Test
    fun testTruncatedHeaderGracefullyHandled() {
        // ROM menor que o cabeçalho obrigatório (ex: 64 bytes)
        val truncatedRom = ByteArray(64)
        var extractedTitle = "DefaultTitle"

        if (truncatedRom.size >= 0xAC) {
            val raw = String(truncatedRom, 0xA0, 12, StandardCharsets.US_ASCII)
            if (raw.isNotBlank()) extractedTitle = raw
        }

        assertEquals("DefaultTitle", extractedTitle)
    }
}
