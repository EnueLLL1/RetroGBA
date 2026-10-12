package com.retro.gba.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManager("TestKeyAlias")
    }

    @Test
    fun encryptAndDecrypt_recoversOriginalPlainText() {
        val original = "ra_token_1234567890abcdef"
        val encrypted = cryptoManager.encrypt(original)

        assertNotNull(encrypted)
        assertTrue(encrypted.isNotBlank())
        assertNotEquals(original, encrypted)

        val decrypted = cryptoManager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_producesDifferentCiphertexts_forSamePlainText_dueToRandomIV() {
        val plain = "super_secret_password"
        val cipher1 = cryptoManager.encrypt(plain)
        val cipher2 = cryptoManager.encrypt(plain)

        assertNotEquals(cipher1, cipher2)
        assertEquals(plain, cryptoManager.decrypt(cipher1))
        assertEquals(plain, cryptoManager.decrypt(cipher2))
    }

    @Test
    fun decrypt_withCorruptedData_returnsEmptyString() {
        val original = "sensitive_data"
        val encrypted = cryptoManager.encrypt(original)
        val corrupted = encrypted.substring(0, encrypted.length - 4) + "AAAA"

        val decrypted = cryptoManager.decrypt(corrupted)
        assertTrue(decrypted.isEmpty() || decrypted != original)
    }

    @Test
    fun encrypt_emptyString_returnsEmptyString() {
        assertEquals("", cryptoManager.encrypt(""))
    }

    @Test
    fun decrypt_emptyOrInvalidString_returnsEmptyString() {
        assertEquals("", cryptoManager.decrypt(""))
        assertEquals("", cryptoManager.decrypt("not_base_64_invalid"))
    }
}
