package com.retro.gba.security

import java.io.File

object RomSecurityValidator {

    const val MIN_ROM_SIZE_BYTES = 192L // Cabeçalho mínimo GBA
    const val MAX_ROM_SIZE_BYTES = 64L * 1024L * 1024L // 64 MB máximo seguro
    const val MAX_SAVE_RAM_SIZE_BYTES = 1024L * 1024L // 1 MB máximo para SRAM / Flash
    const val MAX_SAVE_STATE_SIZE_BYTES = 8L * 1024L * 1024L // 8 MB máximo para Save State

    private val HASH_PATTERN = Regex("^[a-fA-F0-9]{32}$")
    private val SAFE_FILENAME_PATTERN = Regex("^[a-zA-Z0-9._-]+$")

    /**
     * Valida se o hash MD5 da ROM possui formato estrito de 32 caracteres hexadecimais.
     */
    fun isValidRomHash(hash: String): Boolean {
        return hash.isNotBlank() && HASH_PATTERN.matches(hash.trim())
    }

    /**
     * Sanitiza qualquer identificador de hash para prevenir Path Traversal ou injeções de diretório.
     * Permite estritamente caracteres alfanuméricos.
     */
    fun sanitizeHash(hash: String): String {
        return hash.filter { it.isLetterOrDigit() }.lowercase()
    }

    /**
     * Valida um arquivo de ROM em disco contra corrupção, tamanho e existência.
     */
    fun validateRomFile(file: File): ValidationResult {
        if (!file.exists()) {
            return ValidationResult.Invalid("Arquivo de ROM não encontrado.")
        }
        if (!file.isFile) {
            return ValidationResult.Invalid("O caminho fornecido não é um arquivo regular.")
        }
        if (!file.canRead()) {
            return ValidationResult.Invalid("Sem permissão para leitura do arquivo de ROM.")
        }

        val length = file.length()
        if (length < MIN_ROM_SIZE_BYTES) {
            return ValidationResult.Invalid("Arquivo corrompido ou truncado: tamanho menor que $MIN_ROM_SIZE_BYTES bytes.")
        }
        if (length > MAX_ROM_SIZE_BYTES) {
            return ValidationResult.Invalid("Arquivo de ROM excede o limite máximo permitido (64 MB).")
        }

        return ValidationResult.Valid
    }

    /**
     * Valida dados brutos de ROM em memória antes de envio ao JNI.
     */
    fun validateRomBytes(bytes: ByteArray): ValidationResult {
        if (bytes.size < MIN_ROM_SIZE_BYTES) {
            return ValidationResult.Invalid("Tamanho de buffer de ROM inválido (${bytes.size} bytes).")
        }
        if (bytes.size > MAX_ROM_SIZE_BYTES) {
            return ValidationResult.Invalid("Buffer de ROM excede o tamanho máximo de segurança.")
        }
        return ValidationResult.Valid
    }

    /**
     * Valida e garante que um caminho alvo esteja estritamente confinado dentro do diretório base permitido.
     * Previne vulnerabilidades do tipo Directory Traversal (ex: ../../).
     */
    fun isPathConfined(baseDir: File, targetFile: File): Boolean {
        return try {
            val canonicalBase = baseDir.canonicalPath
            val canonicalTarget = targetFile.canonicalPath
            canonicalTarget.startsWith(canonicalBase)
        } catch (e: Exception) {
            false
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}
