package com.retro.gba.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.retro.gba.data.model.RomItem
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.security.RomSecurityValidator
import com.retro.gba.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class RomRepository(
    private val context: Context,
    private val preferences: UserPreferences = UserPreferences(context)
) {
    data class RomStreamMetadata(
        val md5Hex: String,
        val headerTitle: String?,
        val entryName: String?,
        val byteCount: Long
    )

    companion object {
        private const val STREAM_BUFFER_SIZE = 16384
        private val EXTENSION_STRIP_REGEX = Regex("\\.(gba|agb|bin|zip)$", RegexOption.IGNORE_CASE)
        private val ROM_EXTENSION_REGEX = Regex(".*\\.(gba|agb|bin)$", RegexOption.IGNORE_CASE)

        fun isZipMagicBytes(header: ByteArray, length: Int = header.size): Boolean {
            return length >= 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }

        private fun shouldTreatAsZip(
            magicBytes: ByteArray,
            magicLen: Int,
            fileNameHint: String?
        ): Boolean {
            if (isZipMagicBytes(magicBytes, magicLen)) return true
            return fileNameHint?.endsWith(".zip", ignoreCase = true) == true
        }

        private fun isIgnoredZipEntry(entryName: String): Boolean {
            if (entryName.startsWith("__MACOSX/", ignoreCase = true)) return true
            val simpleName = entryName.substringAfterLast('/')
            return simpleName.startsWith("._")
        }

        private fun isPreferredRomExtension(entryName: String): Boolean {
            return ROM_EXTENSION_REGEX.matches(entryName)
        }

        fun extractGbaTitleFromHeader(headerBytes: ByteArray, length: Int = headerBytes.size): String? {
            if (length < 0xAC || headerBytes.size < 0xAC) return null
            var titleEnd = 0xA0
            while (titleEnd < 0xAC && headerBytes[titleEnd] != 0.toByte()) {
                titleEnd++
            }
            val rawTitle = String(headerBytes, 0xA0, titleEnd - 0xA0, Charsets.US_ASCII)
                .trim { it <= ' ' }
            val isCleanAscii = rawTitle.isNotEmpty() && rawTitle.all { it in ' '..'~' }
            return if (isCleanAscii && rawTitle.length >= 3) rawTitle else null
        }

        fun extractFallbackDisplayName(uriLastPathSegment: String?, fallbackName: String? = null): String {
            val cleanLastSegment = uriLastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val cleanFallback = fallbackName
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            if (cleanFallback != null && cleanFallback != "game.gba") {
                val lastHasRomExt = cleanLastSegment?.let {
                    isPreferredRomExtension(it) || it.endsWith(".zip", ignoreCase = true)
                } == true
                val fallbackHasRomExt = isPreferredRomExtension(cleanFallback) ||
                    cleanFallback.endsWith(".zip", ignoreCase = true)
                if (lastHasRomExt && !fallbackHasRomExt) {
                    return cleanLastSegment!!
                }
                return cleanFallback
            }

            return cleanLastSegment ?: cleanFallback ?: "game.gba"
        }

        private fun cleanTitleFromFileName(fileName: String): String {
            val simpleName = fileName.substringAfterLast('/')
            val cleaned = simpleName.replace(EXTENSION_STRIP_REGEX, "").trim()
            return cleaned.ifEmpty { simpleName }
        }

        private fun scanStreamToMetadata(
            stream: InputStream,
            entryName: String?
        ): RomStreamMetadata? {
            val md5Digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            val headerBytes = ByteArray(192)
            var headerLoaded = 0
            var totalRead = 0L

            while (true) {
                val bytesRead = stream.read(buffer)
                if (bytesRead == -1) break
                totalRead += bytesRead
                if (totalRead > RomSecurityValidator.MAX_ROM_SIZE_BYTES) {
                    return null
                }
                md5Digest.update(buffer, 0, bytesRead)
                if (headerLoaded < headerBytes.size) {
                    val toCopy = minOf(headerBytes.size - headerLoaded, bytesRead)
                    System.arraycopy(buffer, 0, headerBytes, headerLoaded, toCopy)
                    headerLoaded += toCopy
                }
            }

            if (totalRead <= 0L) return null

            val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
            val headerTitle = extractGbaTitleFromHeader(headerBytes, headerLoaded)
            return RomStreamMetadata(
                md5Hex = md5Hex,
                headerTitle = headerTitle,
                entryName = entryName,
                byteCount = totalRead
            )
        }

        private fun readBoundedBytes(stream: InputStream): ByteArray? {
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            var totalRead = 0L

            while (true) {
                val bytesRead = stream.read(buffer)
                if (bytesRead == -1) break
                totalRead += bytesRead
                if (totalRead > RomSecurityValidator.MAX_ROM_SIZE_BYTES) {
                    return null
                }
                output.write(buffer, 0, bytesRead)
            }

            val bytes = output.toByteArray()
            return if (bytes.isEmpty()) null else bytes
        }

        private fun isBetterCandidate(candidateSize: Long, currentBestSize: Long): Boolean {
            val candidateMinValid = candidateSize >= RomSecurityValidator.MIN_ROM_SIZE_BYTES
            val currentMinValid = currentBestSize >= RomSecurityValidator.MIN_ROM_SIZE_BYTES
            return when {
                candidateMinValid && !currentMinValid -> true
                !candidateMinValid && currentMinValid -> false
                else -> candidateSize > currentBestSize
            }
        }

        private fun prepareStreamAndCheckZip(
            inputStream: InputStream,
            fileNameHint: String?
        ): Pair<BufferedInputStream, Boolean> {
            val buffered = if (inputStream is BufferedInputStream) {
                inputStream
            } else {
                BufferedInputStream(inputStream, STREAM_BUFFER_SIZE)
            }
            val magic = ByteArray(4)
            buffered.mark(8)
            val magicRead = readUpTo(buffered, magic)
            buffered.reset()
            return buffered to shouldTreatAsZip(magic, magicRead, fileNameHint)
        }

        fun extractRomMetadataFromStream(
            inputStream: InputStream,
            fileNameHint: String? = null
        ): RomStreamMetadata? {
            val (buffered, isZip) = prepareStreamAndCheckZip(inputStream, fileNameHint)

            if (!isZip) {
                return scanStreamToMetadata(buffered, fileNameHint)
            }

            var bestFallback: RomStreamMetadata? = null
            ZipInputStream(buffered).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    try {
                        if (entry.isDirectory || isIgnoredZipEntry(entry.name)) {
                            continue
                        }

                        if (isPreferredRomExtension(entry.name)) {
                            val meta = scanStreamToMetadata(zis, entry.name)
                            if (meta != null) {
                                return meta
                            }
                        } else {
                            val meta = scanStreamToMetadata(zis, entry.name)
                            if (meta != null) {
                                val currentBest = bestFallback
                                if (currentBest == null || isBetterCandidate(meta.byteCount, currentBest.byteCount)) {
                                    bestFallback = meta
                                }
                            }
                        }
                    } finally {
                        zis.closeEntry()
                    }
                }
            }
            return bestFallback
        }

        fun calculateMd5FromStream(inputStream: InputStream, fileNameHint: String? = null): String? {
            return extractRomMetadataFromStream(inputStream, fileNameHint)?.md5Hex
        }

        fun calculateMd5FromBytes(bytes: ByteArray, fileNameHint: String? = null): String? {
            return calculateMd5FromStream(ByteArrayInputStream(bytes), fileNameHint)
        }

        fun readRomBytesFromStream(
            inputStream: InputStream,
            fileNameHint: String? = null
        ): ByteArray {
            val (buffered, isZip) = prepareStreamAndCheckZip(inputStream, fileNameHint)

            if (!isZip) {
                return readBoundedBytes(buffered) ?: ByteArray(0)
            }

            var bestFallback: ByteArray? = null
            ZipInputStream(buffered).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    try {
                        if (entry.isDirectory || isIgnoredZipEntry(entry.name)) {
                            continue
                        }

                        if (isPreferredRomExtension(entry.name)) {
                            val bytes = readBoundedBytes(zis)
                            if (bytes != null) {
                                return bytes
                            }
                        } else {
                            val currentBestSize = bestFallback?.size?.toLong() ?: -1L
                            val entryKnownSize = entry.size
                            if (entryKnownSize >= 0L &&
                                (entryKnownSize > RomSecurityValidator.MAX_ROM_SIZE_BYTES ||
                                    (bestFallback != null && !isBetterCandidate(entryKnownSize, currentBestSize)))
                            ) {
                                continue
                            }
                            val bytes = readBoundedBytes(zis)
                            if (bytes != null) {
                                if (bestFallback == null || isBetterCandidate(bytes.size.toLong(), currentBestSize)) {
                                    bestFallback = bytes
                                }
                            }
                        }
                    } finally {
                        zis.closeEntry()
                    }
                }
            }
            return bestFallback ?: ByteArray(0)
        }

        private fun readUpTo(stream: InputStream, target: ByteArray): Int {
            var total = 0
            while (total < target.size) {
                val read = stream.read(target, total, target.size - total)
                if (read == -1) break
                total += read
            }
            return total
        }
    }

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

    fun resolveFileName(uri: Uri, fallbackName: String? = null): String {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (columnIndex >= 0) {
                            val displayName = cursor.getString(columnIndex)
                            if (!displayName.isNullOrBlank()) {
                                return displayName.substringAfterLast('/')
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback seguro para lastPathSegment caso o ContentProvider não exponha DISPLAY_NAME
            }
        }
        return extractFallbackDisplayName(uri.lastPathSegment, fallbackName)
    }

    private fun tryTakePersistableReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Permissão persistente não suportada para este provider específico de URI
        }
    }

    suspend fun calculateMd5(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            tryTakePersistableReadPermission(uri)
            val resolvedFileName = resolveFileName(uri, null)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                calculateMd5FromStream(inputStream, resolvedFileName)
            }
        } catch (e: SecurityException) {
            AppLogger.log("RomRepository", "Permissão negada ao calcular MD5 da URI $uri", "ERROR", e)
            null
        } catch (e: Exception) {
            AppLogger.log("RomRepository", "Erro ao calcular MD5 da URI $uri", "ERROR", e)
            null
        }
    }

    @JvmOverloads
    suspend fun parseRom(uri: Uri, fileName: String = ""): Result<RomItem> = withContext(Dispatchers.IO) {
        try {
            tryTakePersistableReadPermission(uri)

            val resolvedFileName = resolveFileName(uri, fileName)
            val metadata = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                extractRomMetadataFromStream(inputStream, resolvedFileName)
            } ?: return@withContext Result.failure(
                Exception("Não foi possível abrir ou encontrar uma ROM GBA válida no arquivo selecionado.")
            )

            val title = metadata.headerTitle
                ?: cleanTitleFromFileName(metadata.entryName ?: resolvedFileName)

            val item = RomItem(
                uri = uri.toString(),
                fileName = resolvedFileName,
                title = title,
                md5Hash = metadata.md5Hex
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
            tryTakePersistableReadPermission(uri)
            val resolvedFileName = resolveFileName(uri, null)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                readRomBytesFromStream(inputStream, resolvedFileName)
            } ?: ByteArray(0)
        } catch (e: SecurityException) {
            AppLogger.log("RomRepository", "Permissão negada ao ler bytes da URI $uri", "ERROR", e)
            ByteArray(0)
        } catch (e: Exception) {
            AppLogger.log("RomRepository", "Falha ao ler bytes do arquivo da ROM", "ERROR", e)
            ByteArray(0)
        }
    }
}
