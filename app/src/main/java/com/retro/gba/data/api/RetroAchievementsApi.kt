package com.retro.gba.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.LoginResult
import com.retro.gba.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class RetroAchievementsServerException(
    message: String,
    val httpCode: Int = 200,
    val isAlreadyUnlocked: Boolean = false,
    val isAuthError: Boolean = false,
    val isTransient: Boolean = false
) : Exception(message)

class RetroAchievementsApi(
    baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = createDefaultClient()
) {

    companion object {
        private const val DEFAULT_BASE_URL = "https://retroachievements.org"
        private const val MEDIA_BASE_URL = "https://media.retroachievements.org"
        private const val OFFICIAL_ACHIEVEMENT_FLAG = 3

        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "RetroGBA/1.0 (Android; mGBA)")
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(com.retro.gba.security.NetworkSecurityInterceptor(enableLogging = true))
                .build()
        }
    }

    private val baseUrl: String = baseUrl.trimEnd('/')

    /**
     * Faz o parsing seguro de uma string para JsonElement sem lançar exceções não tratadas
     * caso o servidor retorne um primitivo, texto livre, array, HTML ou string vazia.
     */
    fun parseSafeJson(body: String?): JsonElement? {
        if (body.isNullOrBlank()) return null
        val trimmed = body.trim()
        if (trimmed.startsWith("<")) return null // Página HTML de erro
        return try {
            JsonParser.parseString(trimmed)
        } catch (_: Exception) {
            com.google.gson.JsonPrimitive(trimmed)
        }
    }

    /**
     * Extrai a mensagem de erro mais relevante de um JsonObject ou JsonElement.
     */
    fun extractErrorMessage(element: JsonElement?, defaultMsg: String): String {
        if (element == null) return defaultMsg
        if (element.isJsonPrimitive) {
            val prim = element.asString
            if (prim.isNotBlank()) return prim
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            obj.getStringOrNull("Error")?.takeIf { it.isNotBlank() }?.let { return it }
            obj.getStringOrNull("message")?.takeIf { it.isNotBlank() }?.let { return it }
            obj.getStringOrNull("Code")?.takeIf { it.isNotBlank() }?.let { return "Erro: $it" }
            obj.getStringOrNull("Status")?.takeIf { it.isNotBlank() }?.let {
                return "Erro de autenticação (Status: $it)"
            }
        }
        return defaultMsg
    }

    /**
     * Autentica no RetroAchievements via POST urlencoded em /dorequest.php usando r=login2.
     * Retorna Pair(username, token) preservando compatibilidade com chamadores existentes.
     */
    suspend fun login(username: String, tokenOrPass: String): Result<Pair<String, String>> =
        loginWithDetails(username, tokenOrPass).map { Pair(it.username, it.token) }

    /**
     * Autentica via r=login2 e extrai Token, Score, SoftcoreScore e AvatarUrl para cache local.
     * Suporta senha ('p') e token/Web API Key ('t').
     */
    suspend fun loginWithDetails(username: String, tokenOrPass: String): Result<LoginResult> =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || tokenOrPass.isBlank()) {
                return@withContext Result.failure(
                    Exception("Nome de usuário e senha/chave de API são obrigatórios")
                )
            }

            val looksLikeApiToken = tokenOrPass.length == 32 && tokenOrPass.all { it.isLetterOrDigit() }

            val primaryResult = executeLoginAttempt(username, tokenOrPass, useTokenParam = looksLikeApiToken)
            if (primaryResult.isSuccess) {
                return@withContext primaryResult
            }

            if (looksLikeApiToken) {
                val profileResult = getUserProfile(username, tokenOrPass)
                val profile = profileResult.getOrNull()
                if (profile != null) {
                    return@withContext Result.success(
                        LoginResult(
                            username = profile.username,
                            token = tokenOrPass,
                            score = profile.hardcorePoints,
                            softcoreScore = profile.softcorePoints,
                            avatarUrl = profile.avatarUrl
                        )
                    )
                }
            }

            val fallbackResult = executeLoginAttempt(username, tokenOrPass, useTokenParam = !looksLikeApiToken)
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }

            primaryResult
        }

    private fun executeLoginAttempt(
        username: String,
        secret: String,
        useTokenParam: Boolean
    ): Result<LoginResult> {
        return try {
            val formBody = FormBody.Builder()
                .add("r", "login2")
                .add("u", username)
                .add(if (useTokenParam) "t" else "p", secret)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/dorequest.php")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val jsonElement = parseSafeJson(body)

                if (jsonElement != null && jsonElement.isJsonObject) {
                    val json = jsonElement.asJsonObject
                    if (json.getBooleanOrNull("Success") == true) {
                        val user = json.getStringOrNull("User")?.takeIf { it.isNotBlank() } ?: username
                        val token = json.getStringOrNull("Token").orEmpty()
                        if (token.isBlank()) {
                            return Result.failure(Exception("Token de sessão ausente na resposta do servidor"))
                        }
                        val score = json.getIntOrNull("Score") ?: 0
                        val softcoreScore = json.getIntOrNull("SoftcoreScore") ?: 0
                        val rawAvatar = json.getStringOrNull("AvatarUrl").orEmpty()
                        val avatarUrl = resolveAvatarUrl(rawAvatar, user)
                        Result.success(
                            LoginResult(
                                username = user,
                                token = token,
                                score = score,
                                softcoreScore = softcoreScore,
                                avatarUrl = avatarUrl
                            )
                        )
                    } else {
                        val error = extractErrorMessage(
                            json,
                            "Credenciais inválidas. Verifique seu usuário e senha/chave."
                        )
                        Result.failure(Exception(error))
                    }
                } else if (jsonElement != null && jsonElement.isJsonPrimitive) {
                    val primitiveMsg = jsonElement.asString
                    Result.failure(Exception(primitiveMsg.ifBlank { "Credenciais inválidas" }))
                } else {
                    if (!response.isSuccessful) {
                        Result.failure(Exception("Falha na comunicação com RetroAchievements (HTTP ${response.code})"))
                    } else {
                        Result.failure(Exception("Resposta inesperada do servidor do RetroAchievements"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGameIdByHash(romMd5: String): Result<Long> =
        withContext(Dispatchers.IO) {
            if (romMd5.isBlank()) {
                return@withContext Result.failure(Exception("Hash MD5 da ROM inválido"))
            }
            try {
                val url = "$baseUrl/dorequest.php".toHttpUrl().newBuilder()
                    .addQueryParameter("r", "gameid")
                    .addQueryParameter("m", romMd5)
                    .build()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonElement = parseSafeJson(body)

                    if (jsonElement != null && jsonElement.isJsonObject) {
                        val json = jsonElement.asJsonObject
                        val success = json.getBooleanOrNull("Success") == true
                        val gameId = json.getLongOrNull("GameID") ?: 0L
                        if (success && gameId > 0L) {
                            Result.success(gameId)
                        } else {
                            Result.failure(
                                Exception("Jogo não encontrado no RetroAchievements para esta ROM (MD5: $romMd5)")
                            )
                        }
                    } else {
                        Result.failure(Exception("Jogo não catalogado no RetroAchievements para esta ROM"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getGameAchievements(
        gameId: Long,
        username: String? = null,
        token: String? = null
    ): Result<Pair<GameInfo, List<Achievement>>> =
        withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$baseUrl/dorequest.php".toHttpUrl().newBuilder()
                    .addQueryParameter("r", "patch")
                    .addQueryParameter("g", gameId.toString())
                if (!username.isNullOrBlank() && !token.isNullOrBlank()) {
                    urlBuilder
                        .addQueryParameter("u", username)
                        .addQueryParameter("t", token)
                }
                val request = Request.Builder().url(urlBuilder.build()).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonElement = parseSafeJson(body)

                    if (jsonElement != null && jsonElement.isJsonObject) {
                        val json = jsonElement.asJsonObject
                        val patchDataElem = json.get("PatchData")
                        if (json.getBooleanOrNull("Success") == true &&
                            patchDataElem != null &&
                            patchDataElem.isJsonObject
                        ) {
                            val patchData = patchDataElem.asJsonObject
                            val title = patchData.getStringOrNull("Title") ?: "Jogo Desconhecido"
                            val consoleId = patchData.getIntOrNull("ConsoleID") ?: 5
                            val icon = patchData.getStringOrNull("ImageIcon")
                            val richPresencePatch = patchData.getStringOrNull("RichPresencePatch") ?: ""

                            val achievementsList = mutableListOf<Achievement>()
                            val achElem = patchData.get("Achievements")
                            if (achElem != null && achElem.isJsonArray) {
                                for (element in achElem.asJsonArray) {
                                    if (!element.isJsonObject) continue
                                    val item = element.asJsonObject
                                    // Filtra estritamente conquistas oficiais (Flags == 3), descartando Unofficial (Flags == 5, etc.)
                                    val flags = item.getIntOrNull("Flags")
                                    if (flags != OFFICIAL_ACHIEVEMENT_FLAG) continue

                                    val id = item.getLongOrNull("ID") ?: continue
                                    val achTitle = item.getStringOrNull("Title") ?: "Conquista"
                                    val desc = item.getStringOrNull("Description") ?: ""
                                    val points = item.getIntOrNull("Points") ?: 0
                                    val badgeName = item.getStringOrNull("BadgeName") ?: ""
                                    val memAddr = item.getStringOrNull("MemAddr")

                                    achievementsList.add(
                                        Achievement(
                                            id = id,
                                            title = achTitle,
                                            description = desc,
                                            points = points,
                                            badgeName = badgeName,
                                            memAddr = memAddr
                                        )
                                    )
                                }
                            }

                            val totalPoints = achievementsList.sumOf { it.points }
                            val gameInfo = GameInfo(
                                id = gameId,
                                title = title,
                                consoleId = consoleId,
                                imageIcon = icon,
                                totalAchievements = achievementsList.size,
                                totalPoints = totalPoints,
                                richPresencePatch = richPresencePatch
                            )

                            Result.success(Pair(gameInfo, achievementsList))
                        } else {
                            Result.failure(Exception("Não foi possível carregar as conquistas do jogo"))
                        }
                    } else {
                        Result.failure(Exception("Resposta inválida ao obter conquistas do jogo"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun startSession(
        username: String,
        token: String,
        gameId: Long,
        romHash: String,
        isHardcore: Boolean
    ): Result<Set<Long>> = withContext(Dispatchers.IO) {
        try {
            val hardcoreFlag = if (isHardcore) "1" else "0"
            val formBody = FormBody.Builder()
                .add("r", "startsession")
                .add("u", username)
                .add("t", token)
                .add("g", gameId.toString())
                .add("m", romHash)
                .add("h", hardcoreFlag)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/dorequest.php")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val jsonElement = parseSafeJson(body)

                val unlockedIds = mutableSetOf<Long>()

                if (jsonElement != null && jsonElement.isJsonObject) {
                    val json = jsonElement.asJsonObject
                    if (json.getBooleanOrNull("Success") == true) {
                        val arrayKey = if (isHardcore) "HardcoreUnlocks" else "Unlocks"
                        val arraysToProcess = listOfNotNull(
                            json.get(arrayKey)?.takeIf { it.isJsonArray }?.asJsonArray,
                            if (!isHardcore) json.get("HardcoreUnlocks")?.takeIf { it.isJsonArray }?.asJsonArray else null
                        )

                        for (arr in arraysToProcess) {
                            for (elem in arr) {
                                when {
                                    elem.isJsonObject -> {
                                        val obj = elem.asJsonObject
                                        val id = obj.getLongOrNull("ID") ?: obj.getLongOrNull("AchievementID")
                                        if (id != null) unlockedIds.add(id)
                                    }
                                    elem.isJsonPrimitive -> {
                                        val id = try {
                                            elem.asLong
                                        } catch (_: NumberFormatException) {
                                            null
                                        }
                                        if (id != null) unlockedIds.add(id)
                                    }
                                }
                            }
                        }
                        Result.success(unlockedIds)
                    } else {
                        Result.failure(
                            Exception(extractErrorMessage(json, "Falha ao iniciar sessão no RetroAchievements"))
                        )
                    }
                } else {
                    if (response.isSuccessful) {
                        Result.success(emptySet())
                    } else {
                        Result.failure(Exception("Falha na comunicação com servidor (HTTP ${response.code})"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ping(
        username: String,
        token: String,
        gameId: Long,
        richPresence: String? = null,
        hardcore: Boolean = true,
        gameHash: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val hardcoreFlag = if (hardcore) "1" else "0"
            val builder = FormBody.Builder()
                .add("r", "ping")
                .add("u", username)
                .add("t", token)
                .add("g", gameId.toString())
                .add("h", hardcoreFlag)

            if (!richPresence.isNullOrBlank()) {
                builder.add("m", richPresence)
            }
            if (gameHash.isNotBlank()) {
                builder.add("x", gameHash)
            }

            val request = Request.Builder()
                .url("$baseUrl/dorequest.php")
                .post(builder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val jsonElement = parseSafeJson(body)
                if (jsonElement != null && jsonElement.isJsonObject) {
                    val json = jsonElement.asJsonObject
                    if (json.getBooleanOrNull("Success") == false) {
                        return@withContext Result.failure(
                            Exception(extractErrorMessage(json, "Falha ao enviar ping"))
                        )
                    }
                }
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPing(
        username: String,
        token: String,
        gameId: Long,
        richPresence: String? = null,
        hardcore: Boolean = true,
        gameHash: String = ""
    ): Result<Boolean> = ping(
        username = username,
        token = token,
        gameId = gameId,
        richPresence = richPresence,
        hardcore = hardcore,
        gameHash = gameHash
    )

    suspend fun awardAchievement(
        username: String,
        token: String,
        achievementId: Long,
        isHardcore: Boolean,
        gameHash: String = "",
        secondsSinceUnlock: Long = 0L
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val hardcoreFlag = if (isHardcore) "1" else "0"
            val clampedSeconds = secondsSinceUnlock.coerceAtLeast(0L)
            // Conforme rcheevos (rc_api_runtime.c): quando offset > 0, inclui achievementId + offset na assinatura MD5.
            val rawSig = if (clampedSeconds > 0L) {
                "$achievementId$username$hardcoreFlag$achievementId$clampedSeconds"
            } else {
                "$achievementId$username$hardcoreFlag"
            }
            val vSig = MessageDigest.getInstance("MD5")
                .digest(rawSig.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val formBuilder = FormBody.Builder()
                .add("r", "awardachievement")
                .add("u", username)
                .add("t", token)
                .add("a", achievementId.toString())
                .add("h", hardcoreFlag)
                .add("o", clampedSeconds.toString())
                .add("v", vSig)

            if (gameHash.isNotBlank()) {
                formBuilder.add("m", gameHash)
            }

            val request = Request.Builder()
                .url("$baseUrl/dorequest.php")
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val jsonElement = parseSafeJson(body)

                if (jsonElement != null && jsonElement.isJsonObject) {
                    val json = jsonElement.asJsonObject
                    if (json.getBooleanOrNull("Success") == true) {
                        Result.success(true)
                    } else {
                        val err = extractErrorMessage(json, "Falha ao submeter conquista")
                        val alreadyUnlocked = isAlreadyUnlockedMessage(err)
                        val authError = isAuthErrorCodeOrMessage(response.code, err)
                        val transient = !alreadyUnlocked && !authError &&
                            isTransientHttpOrMessage(response.code, err)
                        Result.failure(
                            RetroAchievementsServerException(
                                message = err,
                                httpCode = response.code,
                                isAlreadyUnlocked = alreadyUnlocked,
                                isAuthError = authError,
                                isTransient = transient
                            )
                        )
                    }
                } else if (jsonElement != null && jsonElement.isJsonPrimitive) {
                    val msg = jsonElement.asString
                    val authError = isAuthErrorCodeOrMessage(response.code, msg)
                    val transient = !authError && isTransientHttpOrMessage(response.code, msg)
                    Result.failure(
                        RetroAchievementsServerException(
                            message = msg.ifBlank { "Falha na submissão da conquista (HTTP ${response.code})" },
                            httpCode = response.code,
                            isAlreadyUnlocked = isAlreadyUnlockedMessage(msg),
                            isAuthError = authError,
                            isTransient = transient
                        )
                    )
                } else {
                    val authError = response.code == 401 || response.code == 403
                    val transient = !authError && (
                        response.code == 429 ||
                            response.code == 408 ||
                            response.code >= 500 ||
                            response.isSuccessful
                        )
                    Result.failure(
                        RetroAchievementsServerException(
                            message = "Falha na submissão da conquista (HTTP ${response.code})",
                            httpCode = response.code,
                            isAuthError = authError,
                            isTransient = transient
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(username: String, webApiKey: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || webApiKey.isBlank()) {
                return@withContext Result.failure(
                    RetroAchievementsServerException(
                        message = "Chave de API Web ausente",
                        httpCode = 401,
                        isAuthError = true
                    )
                )
            }
            try {
                val url = "$baseUrl/API/API_GetUserSummary.php".toHttpUrl().newBuilder()
                    .addQueryParameter("z", username)
                    .addQueryParameter("y", webApiKey)
                    .addQueryParameter("u", username)
                    .addQueryParameter("g", "0")
                    .addQueryParameter("a", "0")
                    .build()
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonElement = parseSafeJson(body)

                    if (response.isSuccessful && jsonElement != null && jsonElement.isJsonObject) {
                        val json = jsonElement.asJsonObject
                        val totalPointsElem = json.get("TotalPoints")
                        if (totalPointsElem != null && !totalPointsElem.isJsonNull) {
                            val points = json.getIntOrNull("TotalPoints") ?: 0
                            val softcorePoints = json.getIntOrNull("TotalSoftcorePoints") ?: 0
                            val rank = json.getIntOrNull("Rank") ?: 0
                            val userPic = json.getStringOrNull("UserPic").orEmpty()
                            val avatarUrl = resolveAvatarUrl(userPic, username)

                            Result.success(
                                UserProfile(
                                    username = username,
                                    totalPoints = points,
                                    hardcorePoints = points,
                                    rank = rank,
                                    avatarUrl = avatarUrl,
                                    softcorePoints = softcorePoints
                                )
                            )
                        } else {
                            val error = extractErrorMessage(json, "Perfil não localizado ou chave não autorizada")
                            Result.failure(
                                RetroAchievementsServerException(
                                    message = error,
                                    httpCode = response.code,
                                    isAuthError = response.code == 401 || response.code == 403
                                )
                            )
                        }
                    } else if (jsonElement != null && jsonElement.isJsonPrimitive) {
                        Result.failure(
                            RetroAchievementsServerException(
                                message = jsonElement.asString,
                                httpCode = response.code,
                                isAuthError = response.code == 401 || response.code == 403
                            )
                        )
                    } else {
                        Result.failure(
                            RetroAchievementsServerException(
                                message = "Não foi possível carregar dados do perfil (HTTP ${response.code})",
                                httpCode = response.code,
                                isAuthError = response.code == 401 || response.code == 403,
                                isTransient = response.code >= 500 || response.code == 429
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchProfile(
        username: String,
        webApiKey: String,
        fallbackProfile: UserProfile? = null
    ): Result<UserProfile> {
        val remote = getUserProfile(username, webApiKey)
        if (remote.isSuccess) return remote
        return if (fallbackProfile != null) {
            Result.success(fallbackProfile)
        } else {
            remote
        }
    }

    private fun resolveAvatarUrl(rawAvatar: String, username: String): String {
        val trimmed = rawAvatar.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "$MEDIA_BASE_URL$trimmed"
            trimmed.isNotBlank() -> trimmed
            username.isNotBlank() -> "$MEDIA_BASE_URL/UserPic/$username.png"
            else -> ""
        }
    }

    private fun isAlreadyUnlockedMessage(msg: String): Boolean {
        return msg.contains("already has", ignoreCase = true) ||
            msg.contains("already awarded", ignoreCase = true) ||
            msg.contains("already unlocked", ignoreCase = true)
    }

    private fun isAuthErrorCodeOrMessage(httpCode: Int, msg: String): Boolean {
        if (httpCode == 401 || httpCode == 403) return true
        return msg.contains("invalid credentials", ignoreCase = true) ||
            msg.contains("credentials invalid", ignoreCase = true) ||
            msg.contains("invalid token", ignoreCase = true) ||
            msg.contains("expired token", ignoreCase = true) ||
            msg.contains("invalid user", ignoreCase = true) ||
            msg.contains("unauthenticated", ignoreCase = true) ||
            msg.contains("access denied", ignoreCase = true)
    }

    private fun isTransientHttpOrMessage(httpCode: Int, msg: String): Boolean {
        if (httpCode == 429 || httpCode == 408 || httpCode >= 500) return true
        return msg.contains("too many", ignoreCase = true) ||
            msg.contains("temporarily unavailable", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("error code: 5", ignoreCase = true)
    }

    private fun JsonObject.getStringOrNull(memberName: String): String? {
        val elem = get(memberName) ?: return null
        if (elem.isJsonNull || !elem.isJsonPrimitive) return null
        return elem.asString
    }

    private fun JsonObject.getIntOrNull(memberName: String): Int? {
        val elem = get(memberName) ?: return null
        if (elem.isJsonNull || !elem.isJsonPrimitive) return null
        return try {
            elem.asInt
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun JsonObject.getLongOrNull(memberName: String): Long? {
        val elem = get(memberName) ?: return null
        if (elem.isJsonNull || !elem.isJsonPrimitive) return null
        return try {
            elem.asLong
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun JsonObject.getBooleanOrNull(memberName: String): Boolean? {
        val elem = get(memberName) ?: return null
        if (elem.isJsonNull || !elem.isJsonPrimitive) return null
        val prim = elem.asJsonPrimitive
        return when {
            prim.isBoolean -> prim.asBoolean
            prim.isNumber -> prim.asInt != 0
            prim.isString -> prim.asString.equals("true", ignoreCase = true) || prim.asString == "1"
            else -> null
        }
    }
}
