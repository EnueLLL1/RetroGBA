package com.retro.gba.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.retro.gba.data.model.CheatCode
import com.retro.gba.security.RomSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Repositório responsável pela persistência atômica e recuperação
 * de códigos de trapaça (GameShark, Action Replay, CodeBreaker) por ROM.
 */
class CheatRepository(
    private val cheatsDirectory: File
) {
    constructor(context: Context) : this(File(context.filesDir, "cheats"))

    private val gson = Gson()

    init {
        if (!cheatsDirectory.exists()) {
            cheatsDirectory.mkdirs()
        }
    }

    private fun getCheatFile(romHash: String): File? {
        val sanitized = RomSecurityValidator.sanitizeHash(romHash)
        if (sanitized.isEmpty()) return null

        val target = File(cheatsDirectory, "$sanitized.json")
        return if (RomSecurityValidator.isPathConfined(cheatsDirectory, target)) target else null
    }

    /**
     * Carrega a lista de cheats salvos para uma determinada ROM.
     */
    suspend fun loadCheats(romHash: String): List<CheatCode> = withContext(Dispatchers.IO) {
        val file = getCheatFile(romHash) ?: return@withContext emptyList()
        if (!file.exists() || !file.canRead() || file.length() == 0L) return@withContext emptyList()

        try {
            val json = file.readText(Charsets.UTF_8)
            val listType = object : TypeToken<List<CheatCode>>() {}.type
            gson.fromJson<List<CheatCode>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Salva a lista de cheats para a ROM especificada de forma atômica.
     */
    suspend fun saveCheats(romHash: String, cheats: List<CheatCode>): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getCheatFile(romHash) ?: return@withContext false
        val tempFile = File(cheatsDirectory, "${targetFile.name}.tmp")

        try {
            val json = gson.toJson(cheats)
            FileOutputStream(tempFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
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
     * Exclui todos os cheats cadastrados para uma determinada ROM.
     */
    suspend fun deleteCheats(romHash: String): Boolean = withContext(Dispatchers.IO) {
        val file = getCheatFile(romHash) ?: return@withContext false
        if (file.exists()) file.delete() else false
    }
}
