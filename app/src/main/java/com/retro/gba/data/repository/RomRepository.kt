package com.retro.gba.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.retro.gba.data.model.RomItem
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class RomRepository(
    private val context: Context,
    private val preferences: UserPreferences = UserPreferences(context)
) {
    private val gson = Gson()

    fun getSavedRoms(): List<RomItem> {
        val json = preferences.savedRomsJson ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RomItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRomItem(item: RomItem) {
        val current = getSavedRoms().toMutableList()
        val existingIndex = current.indexOfFirst { it.uri == item.uri || it.md5Hash == item.md5Hash }
        if (existingIndex >= 0) {
            current[existingIndex] = item
        } else {
            current.add(0, item)
        }
        val type = object : TypeToken<List<RomItem>>() {}.type
        preferences.savedRomsJson = gson.toJson(current, type)
    }

    fun deleteRomItem(uriStr: String) {
        val current = getSavedRoms().filter { it.uri != uriStr }
        val type = object : TypeToken<List<RomItem>>() {}.type
        preferences.savedRomsJson = gson.toJson(current, type)
    }

    suspend fun parseRom(uri: Uri, fileName: String): Result<RomItem> = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Permissão persistente não suportada para este provider específico de URI
            }

            val cleanFileName = fileName.removeSuffix(".gba").removeSuffix(".GBA").removeSuffix(".zip").removeSuffix(".ZIP")
            var title = cleanFileName
            var md5Hex = ""

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val md5Digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var totalRead = 0L

                val headerBytes = ByteArray(192)
                var headerLoaded = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    md5Digest.update(buffer, 0, bytesRead)
                    if (headerLoaded < 192) {
                        val toCopy = minOf(192 - headerLoaded, bytesRead)
                        System.arraycopy(buffer, 0, headerBytes, headerLoaded, toCopy)
                        headerLoaded += toCopy
                    }
                    totalRead += bytesRead
                }

                // Extrair titulo do Header GBA (offset 0xA0 a 0xAB - 12 bytes ASCII)
                if (headerLoaded >= 0xAC) {
                    val rawTitle = String(headerBytes, 0xA0, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    val isCleanAscii = rawTitle.isNotEmpty() && rawTitle.all { it in ' '..'~' }
                    if (isCleanAscii && rawTitle.length >= 3) {
                        title = rawTitle
                    }
                }

                val digest = md5Digest.digest()
                md5Hex = digest.joinToString("") { "%02x".format(it) }
            } ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo selecionado."))

            val item = RomItem(
                uri = uri.toString(),
                fileName = fileName,
                title = title,
                md5Hash = md5Hex
            )
            saveRomItem(item)

            Result.success(item)
        } catch (e: SecurityException) {
            AppLogger.log("RomRepository", "Acesso negado à URI $uri", "ERROR", e)
            Result.failure(Exception("Permissão de leitura negada pelo sistema Android para esta ROM."))
        } catch (e: Exception) {
            AppLogger.log("RomRepository", "Erro ao processar ROM $fileName", "ERROR", e)
            Result.failure(e)
        }
    }

    suspend fun readRomBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}

            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: SecurityException) {
            AppLogger.log("RomRepository", "Permissão negada ao ler bytes da URI $uri", "ERROR", e)
            ByteArray(0)
        } catch (e: Exception) {
            AppLogger.log("RomRepository", "Falha ao ler bytes do arquivo da ROM", "ERROR", e)
            ByteArray(0)
        }
    }
}
