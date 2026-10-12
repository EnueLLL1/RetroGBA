package com.retro.gba.data.repository

import android.content.Context
import android.net.Uri
import com.retro.gba.data.model.RomItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

class RomRepository(private val context: Context) {

    suspend fun parseRom(uri: Uri, fileName: String): Result<RomItem> = withContext(Dispatchers.IO) {
        try {
            var title = fileName.removeSuffix(".gba").removeSuffix(".zip")
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
                    if (rawTitle.isNotEmpty()) {
                        title = rawTitle
                    }
                }

                val digest = md5Digest.digest()
                md5Hex = digest.joinToString("") { "%02x".format(it) }
            } ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo"))

            Result.success(
                RomItem(
                    uri = uri.toString(),
                    fileName = fileName,
                    title = title,
                    md5Hash = md5Hex
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readRomBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
}

