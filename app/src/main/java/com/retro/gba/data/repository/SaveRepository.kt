package com.retro.gba.data.repository

import android.content.Context
import com.retro.gba.security.RomSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class SaveStateInfo(
    val slot: Int,
    val exists: Boolean,
    val timestamp: Long = 0L,
    val sizeBytes: Long = 0L
)

/**
 * Repositório responsável pela persistência atômica e recuperação de Save RAM (.sav)
 * e Save States (.ssX) com proteção contra corrupção de arquivos, Path Traversal
 * e estouro de limites de armazenamento.
 */
class SaveRepository(
    private val saveDirectory: File
) {

    constructor(context: Context) : this(File(context.filesDir, "saves"))

    init {
        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs()
        }
    }

    private fun getBatterySaveFile(romHash: String): File? {
        val sanitized = RomSecurityValidator.sanitizeHash(romHash)
        if (sanitized.isEmpty()) return null

        val target = File(saveDirectory, "$sanitized.sav")
        return if (RomSecurityValidator.isPathConfined(saveDirectory, target)) target else null
    }

    private fun getStateFile(romHash: String, slot: Int): File? {
        if (slot !in 0..9) return null
        val sanitized = RomSecurityValidator.sanitizeHash(romHash)
        if (sanitized.isEmpty()) return null

        val target = File(saveDirectory, "$sanitized.ss$slot")
        return if (RomSecurityValidator.isPathConfined(saveDirectory, target)) target else null
    }

    /**
     * Persistência atômica de Save RAM (bateria do cartucho).
     * Grava primeiramente em arquivo .tmp e realiza renomeação atômica no sistema de arquivos.
     */
    suspend fun saveBatteryRam(romHash: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (data.isEmpty() || data.size > RomSecurityValidator.MAX_SAVE_RAM_SIZE_BYTES) {
            return@withContext false
        }

        val targetFile = getBatterySaveFile(romHash) ?: return@withContext false
        val tempFile = File(saveDirectory, "${targetFile.name}.tmp")

        try {
            FileOutputStream(tempFile).use { it.write(data) }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    /**
     * Carrega a Save RAM salva anteriormente em disco para a ROM especificada.
     */
    suspend fun loadBatteryRam(romHash: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = getBatterySaveFile(romHash) ?: return@withContext null
        if (!file.exists() || !file.canRead() || file.length() == 0L) return@withContext null
        if (file.length() > RomSecurityValidator.MAX_SAVE_RAM_SIZE_BYTES) return@withContext null

        try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun hasBatteryRam(romHash: String): Boolean = withContext(Dispatchers.IO) {
        val file = getBatterySaveFile(romHash) ?: return@withContext false
        file.exists() && file.length() > 0L
    }

    /**
     * Salva o estado instantâneo (Save State) em um determinado slot de forma atômica.
     */
    suspend fun saveState(romHash: String, slot: Int, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (data.isEmpty() || data.size > RomSecurityValidator.MAX_SAVE_STATE_SIZE_BYTES) {
            return@withContext false
        }

        val targetFile = getStateFile(romHash, slot) ?: return@withContext false
        val tempFile = File(saveDirectory, "${targetFile.name}.tmp")

        try {
            FileOutputStream(tempFile).use { it.write(data) }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    /**
     * Carrega os dados de Save State para restauração no core.
     */
    suspend fun loadState(romHash: String, slot: Int): ByteArray? = withContext(Dispatchers.IO) {
        val file = getStateFile(romHash, slot) ?: return@withContext null
        if (!file.exists() || !file.canRead() || file.length() == 0L) return@withContext null
        if (file.length() > RomSecurityValidator.MAX_SAVE_STATE_SIZE_BYTES) return@withContext null

        try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lista todos os slots de Save State configuráveis para a ROM.
     */
    suspend fun listStateSlots(romHash: String, maxSlots: Int = 5): List<SaveStateInfo> = withContext(Dispatchers.IO) {
        val boundedMax = maxSlots.coerceIn(1, 10)
        (0 until boundedMax).map { slot ->
            val file = getStateFile(romHash, slot)
            if (file != null && file.exists() && file.length() > 0L) {
                SaveStateInfo(
                    slot = slot,
                    exists = true,
                    timestamp = file.lastModified(),
                    sizeBytes = file.length()
                )
            } else {
                SaveStateInfo(slot = slot, exists = false)
            }
        }
    }

    suspend fun deleteState(romHash: String, slot: Int): Boolean = withContext(Dispatchers.IO) {
        val file = getStateFile(romHash, slot) ?: return@withContext false
        if (file.exists()) file.delete() else false
    }
}
