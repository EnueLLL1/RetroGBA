package com.retro.gba.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class RetroAchievementsApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
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

    private val baseUrl = "https://retroachievements.org"

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
            when {
                obj.has("Error") && !obj.get("Error").isJsonNull -> return obj.get("Error").asString
                obj.has("message") && !obj.get("message").isJsonNull -> return obj.get("message").asString
                obj.has("Code") && !obj.get("Code").isJsonNull -> return "Erro: ${obj.get("Code").asString}"
                obj.has("Status") && !obj.get("Status").isJsonNull -> return "Erro de autenticação (Status: ${obj.get("Status").asString})"
            }
        }
        return defaultMsg
    }

    /**
     * Autentica no RetroAchievements via requisição POST urlencoded para /dorequest.php.
     * Suporta autenticação tanto por senha (parâmetro 'p') quanto por Web API Key / Token (parâmetro 't').
     */
    suspend fun login(username: String, tokenOrPass: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || tokenOrPass.isBlank()) {
                return@withContext Result.failure(Exception("Nome de usuário e senha/chave de API são obrigatórios"))
            }

            val looksLikeApiToken = tokenOrPass.length == 32 && tokenOrPass.all { it.isLetterOrDigit() }

            // Se parece um token/chave web, tenta primeiro como token ('t'); caso contrário, tenta como senha ('p')
            val primaryResult = executeLoginAttempt(username, tokenOrPass, useTokenParam = looksLikeApiToken)
            if (primaryResult.isSuccess) {
                return@withContext primaryResult
            }

            // Se parece uma Web API Key de 32 caracteres, tenta validar via API_GetUserSummary.php
            if (looksLikeApiToken) {
                val profileResult = getUserProfile(username, tokenOrPass)
                if (profileResult.isSuccess) {
                    return@withContext Result.success(Pair(username, tokenOrPass))
                }
            }

            // Fallback: se a primeira tentativa falhar, tenta com o parâmetro alternativo ('p' vs 't')
            val fallbackResult = executeLoginAttempt(username, tokenOrPass, useTokenParam = !looksLikeApiToken)
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }

            // Retorna o erro mais descritivo
            primaryResult
        }

    private fun executeLoginAttempt(username: String, secret: String, useTokenParam: Boolean): Result<Pair<String, String>> {
        return try {
            val formBody = FormBody.Builder()
                .add("r", "login")
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
                    if (json.has("Success") && json.get("Success").asBoolean) {
                        val user = json.get("User")?.asString ?: username
                        val token = json.get("Token")?.asString ?: ""
                        Result.success(Pair(user, token))
                    } else {
                        val error = extractErrorMessage(json, "Credenciais inválidas. Verifique seu usuário e senha/chave.")
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
            try {
                val url = "$baseUrl/dorequest.php?r=gameid&m=$romMd5"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonElement = parseSafeJson(body)

                    if (jsonElement != null && jsonElement.isJsonObject) {
                        val json = jsonElement.asJsonObject
                        if (json.has("Success") && json.get("Success").asBoolean && json.has("GameID")) {
                            val gameId = json.get("GameID").asLong
                            Result.success(gameId)
                        } else {
                            Result.failure(Exception("Jogo não encontrado no RetroAchievements para esta ROM (MD5: $romMd5)"))
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
                var url = "$baseUrl/dorequest.php?r=patch&g=$gameId"
                if (!username.isNullOrBlank() && !token.isNullOrBlank()) {
                    url += "&u=$username&t=$token"
                }
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonElement = parseSafeJson(body)

                    if (jsonElement != null && jsonElement.isJsonObject) {
                        val json = jsonElement.asJsonObject
                        if (json.has("Success") && json.get("Success").asBoolean && json.has("PatchData")) {
                            val patchData = json.getAsJsonObject("PatchData")
                            val title = patchData.get("Title")?.asString ?: "Jogo Desconhecido"
                            val icon = patchData.get("ImageIcon")?.asString

                            val achievementsList = mutableListOf<Achievement>()
                            if (patchData.has("Achievements")) {
                                val achArray = patchData.getAsJsonArray("Achievements")
                                for (element in achArray) {
                                    if (!element.isJsonObject) continue
                                    val item = element.asJsonObject
                                    val id = item.get("ID")?.asLong ?: continue
                                    val achTitle = item.get("Title")?.asString ?: "Conquista"
                                    val desc = item.get("Description")?.asString ?: ""
                                    val points = item.get("Points")?.asInt ?: 0
                                    val badgeName = item.get("BadgeName")?.asString ?: ""
                                    val memAddr = item.get("MemAddr")?.asString

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
                                imageIcon = icon,
                                totalAchievements = achievementsList.size,
                                totalPoints = totalPoints
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
                    if (json.has("Success") && json.get("Success").asBoolean) {
                        val arrayKey = if (isHardcore) "HardcoreUnlocks" else "Unlocks"
                        val arraysToProcess = listOfNotNull(
                            if (json.has(arrayKey) && json.get(arrayKey).isJsonArray) json.getAsJsonArray(arrayKey) else null,
                            if (!isHardcore && json.has("HardcoreUnlocks") && json.get("HardcoreUnlocks").isJsonArray) json.getAsJsonArray("HardcoreUnlocks") else null
                        )

                        for (arr in arraysToProcess) {
                            for (elem in arr) {
                                when {
                                    elem.isJsonObject -> {
                                        val obj = elem.asJsonObject
                                        val id = obj.get("ID")?.asLong ?: obj.get("AchievementID")?.asLong
                                        if (id != null) unlockedIds.add(id)
                                    }
                                    elem.isJsonPrimitive -> {
                                        val id = elem.asLong
                                        unlockedIds.add(id)
                                    }
                                }
                            }
                        }
                        Result.success(unlockedIds)
                    } else {
                        Result.failure(Exception(extractErrorMessage(json, "Falha ao iniciar sessão no RetroAchievements")))
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
        richPresence: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val builder = FormBody.Builder()
                .add("r", "ping")
                .add("u", username)
                .add("t", token)
                .add("g", gameId.toString())

            if (!richPresence.isNullOrBlank()) {
                builder.add("m", richPresence)
            }

            val request = Request.Builder()
                .url("$baseUrl/dorequest.php")
                .post(builder.build())
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun awardAchievement(
        username: String,
        token: String,
        achievementId: Long,
        isHardcore: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val hardcoreFlag = if (isHardcore) "1" else "0"
            val rawSig = "$achievementId$username$hardcoreFlag"
            val vSig = MessageDigest.getInstance("MD5").digest(rawSig.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

            val formBody = FormBody.Builder()
                .add("r", "awardachievement")
                .add("u", username)
                .add("t", token)
                .add("a", achievementId.toString())
                .add("h", hardcoreFlag)
                .add("v", vSig)
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
                    if (json.has("Success") && json.get("Success").asBoolean) {
                        Result.success(true)
                    } else {
                        val err = extractErrorMessage(json, "Falha ao submeter conquista")
                        Result.failure(Exception(err))
                    }
                } else if (jsonElement != null && jsonElement.isJsonPrimitive) {
                    Result.failure(Exception(jsonElement.asString))
                } else {
                    Result.failure(Exception("Falha na submissão da conquista (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(username: String, webApiKey: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/API/API_GetUserSummary.php?z=$username&y=$webApiKey&u=$username&g=0&a=0"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonElement = parseSafeJson(body)

                    if (jsonElement != null && jsonElement.isJsonObject) {
                        val json = jsonElement.asJsonObject
                        if (json.has("TotalPoints")) {
                            val points = json.get("TotalPoints").asInt
                            val hardcorePoints = json.get("TotalSoftcorePoints")?.asInt ?: points
                            val rank = json.get("Rank")?.asInt ?: 0
                            val userPic = json.get("UserPic")?.asString ?: ""
                            val avatarUrl = "$baseUrl$userPic"

                            Result.success(
                                UserProfile(
                                    username = username,
                                    totalPoints = points,
                                    hardcorePoints = hardcorePoints,
                                    rank = rank,
                                    avatarUrl = avatarUrl
                                )
                            )
                        } else {
                            val error = extractErrorMessage(json, "Perfil não localizado ou chave não autorizada")
                            Result.failure(Exception(error))
                        }
                    } else if (jsonElement != null && jsonElement.isJsonPrimitive) {
                        Result.failure(Exception(jsonElement.asString))
                    } else {
                        Result.failure(Exception("Não foi possível carregar dados do perfil (HTTP ${response.code})"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
