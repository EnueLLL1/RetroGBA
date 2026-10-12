package com.retro.gba.security

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomSecurityValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sanitizeHash_removesPathTraversalSequences() {
        val maliciousInput = "../../etc/passwd"
        val sanitized = RomSecurityValidator.sanitizeHash(maliciousInput)

        assertEquals("etcpasswd", sanitized)
        assertFalse(sanitized.contains("."))
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
    }

    @Test
    fun isValidRomHash_acceptsValidHexAndRejectsMalicious() {
        assertTrue(RomSecurityValidator.isValidRomHash("0123456789abcdef0123456789abcdef"))
        assertFalse(RomSecurityValidator.isValidRomHash("invalid_hash_string"))
        assertFalse(RomSecurityValidator.isValidRomHash("../../0123456789abcdef0123456789"))
    }

    @Test
    fun validateRomBytes_rejectsUndersizedAndOversized() {
        val undersized = ByteArray(100)
        val underResult = RomSecurityValidator.validateRomBytes(undersized)
        assertTrue(underResult is RomSecurityValidator.ValidationResult.Invalid)

        val validSize = ByteArray(512)
        val validResult = RomSecurityValidator.validateRomBytes(validSize)
        assertTrue(validResult is RomSecurityValidator.ValidationResult.Valid)
    }

    @Test
    fun validateRomFile_checksExistenceAndSize() {
        val nonExistent = File(tempFolder.root, "does_not_exist.gba")
        val resultNonExist = RomSecurityValidator.validateRomFile(nonExistent)
        assertTrue(resultNonExist is RomSecurityValidator.ValidationResult.Invalid)

        val tinyFile = tempFolder.newFile("tiny.gba")
        tinyFile.writeBytes(ByteArray(50))
        val resultTiny = RomSecurityValidator.validateRomFile(tinyFile)
        assertTrue(resultTiny is RomSecurityValidator.ValidationResult.Invalid)

        val validFile = tempFolder.newFile("valid.gba")
        validFile.writeBytes(ByteArray(256))
        val resultValid = RomSecurityValidator.validateRomFile(validFile)
        assertTrue(resultValid is RomSecurityValidator.ValidationResult.Valid)
    }

    @Test
    fun isPathConfined_preventsDirectoryTraversal() {
        val baseDir = tempFolder.newFolder("safe_saves")
        val legitimateFile = File(baseDir, "savegame.sav")
        val maliciousFile = File(baseDir, "../malicious.sav")

        assertTrue(RomSecurityValidator.isPathConfined(baseDir, legitimateFile))
        assertFalse(RomSecurityValidator.isPathConfined(baseDir, maliciousFile))
    }
}
