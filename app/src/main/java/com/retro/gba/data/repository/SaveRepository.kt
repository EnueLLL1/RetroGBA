package com.retro.gba.data.repository

import android.content.Context
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
 * e Save States (.ssX) com proteção contra corrupção de arquivos.
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

    private fun getBatterySaveFile(romHash: String): File {
        return File(saveDirectory, "${romHash.lowercase()}.sav")
    }

    private fun getStateFile(romHash: String, slot: Int): File {
        return File(saveDirectory, "${romHash.lowercase()}.ss$slot")
    }

    /**
     * Persistência atômica de Save RAM (bateria do cartucho).
     * Grava primeiramente em arquivo .tmp e realiza renomeação atômica no sistema de arquivos.
     */
    suspend fun saveBatteryRam(romHash: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (romHash.isBlank() || data.isEmpty()) return@withContext false

        val targetFile = getBatterySaveFile(romHash)
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
        if (romHash.isBlank()) return@withContext null
        val file = getBatterySaveFile(romHash)
        if (!file.exists() || !file.canRead() || file.length() == 0L) return@withContext null

        try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun hasBatteryRam(romHash: String): Boolean = withContext(Dispatchers.IO) {
        val file = getBatterySaveFile(romHash)
        file.exists() && file.length() > 0L
    }

    /**
     * Salva o estado instantâneo (Save State) em um determinado slot de forma atômica.
     */
    suspend fun saveState(romHash: String, slot: Int, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (romHash.isBlank() || slot < 0 || data.isEmpty()) return@withContext false

        val targetFile = getStateFile(romHash, slot)
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
        if (romHash.isBlank() || slot < 0) return@withContext null
        val file = getStateFile(romHash, slot)
        if (!file.exists() || !file.canRead() || file.length() == 0L) return@withContext null

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
        (0 until maxSlots).map { slot ->
            val file = getStateFile(romHash, slot)
            if (file.exists() && file.length() > 0L) {
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
        val file = getStateFile(romHash, slot)
        if (file.exists()) file.delete() else false
    }
}
