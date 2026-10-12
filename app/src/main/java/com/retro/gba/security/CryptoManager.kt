package com.retro.gba.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gerenciador de Criptografia Autenticada AES-256-GCM com proteção via Android KeyStore.
 * Armazena as chaves no hardware seguro quando disponível, com suporte a fallback
 * isolado para ambientes de testes unitários locais (JUnit).
 */
class CryptoManager(
    private val keyAlias: String = "RetroGbaSecureKey"
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128 // bits

        // Fallback simétrico para testes fora do dispositivo Android (ex: JVM Desktop)
        private val FALLBACK_TEST_KEY_BYTES = byteArrayOf(
            0x2A, 0x4B, 0x6D, 0x52, 0x73, 0x35, 0x76, 0x38,
            0x79, 0x2F, 0x42, 0x3F, 0x45, 0x28, 0x48, 0x2B,
            0x4D, 0x62, 0x51, 0x65, 0x54, 0x68, 0x57, 0x6D,
            0x5A, 0x71, 0x34, 0x74, 0x37, 0x77, 0x21, 0x7A
        )
    }

    private var fallbackKey: SecretKey? = null

    private fun getSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }

            keyStore.getKey(keyAlias, null) as SecretKey
        } catch (e: Exception) {
            // Em testes unitários locais ou ambientes sem AndroidKeyStore
            if (fallbackKey == null) {
                fallbackKey = SecretKeySpec(FALLBACK_TEST_KEY_BYTES, "AES")
            }
            fallbackKey!!
        }
    }

    /**
     * Criptografa uma string usando AES-256-GCM.
     * Retorna o IV + dados cifrados codificados em Base64.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""

        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Empacota IV (12 bytes) seguido pelo CipherText
        val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
        byteBuffer.put(iv)
        byteBuffer.put(cipherText)

        return encodeBase64(byteBuffer.array())
    }

    /**
     * Descriptografa uma string previamente cifrada com AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""

        return try {
            val combined = decodeBase64(encryptedBase64)
            if (combined.size <= GCM_IV_LENGTH) return ""

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Se falhar a decodificação ou validação de integridade da tag GCM
            ""
        }
    }

    private fun encodeBase64(data: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(data)
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
}
