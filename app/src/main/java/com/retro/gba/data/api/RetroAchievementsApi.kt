package com.retro.gba.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class RetroAchievementsApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(com.retro.gba.security.NetworkSecurityInterceptor(enableLogging = true))
        .build()

    private val gson = Gson()
    private val baseUrl = "https://retroachievements.org"

    suspend fun login(username: String, tokenOrPass: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/dorequest.php?r=login&u=$username&p=$tokenOrPass"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)

                    if (json.has("Success") && json.get("Success").asBoolean) {
                        val user = json.get("User").asString
                        val token = json.get("Token").asString
                        Result.success(Pair(user, token))
                    } else {
                        val error = if (json.has("Error")) json.get("Error").asString else "Falha no login"
                        Result.failure(Exception(error))
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
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)

                    if (json.has("Success") && json.get("Success").asBoolean) {
                        val gameId = json.get("GameID").asLong
                        Result.success(gameId)
                    } else {
                        Result.failure(Exception("Jogo não encontrado no RetroAchievements para esta ROM (MD5: $romMd5)"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getGameAchievements(gameId: Long): Result<Pair<GameInfo, List<Achievement>>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/dorequest.php?r=patch&g=$gameId"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)

                    if (json.has("Success") && json.get("Success").asBoolean) {
                        val patchData = json.getAsJsonObject("PatchData")
                        val title = patchData.get("Title").asString
                        val icon = patchData.get("ImageIcon")?.asString

                        val achievementsList = mutableListOf<Achievement>()
                        if (patchData.has("Achievements")) {
                            val achArray = patchData.getAsJsonArray("Achievements")
                            for (element in achArray) {
                                val item = element.asJsonObject
                                val id = item.get("ID").asLong
                                val achTitle = item.get("Title").asString
                                val desc = item.get("Description").asString
                                val points = item.get("Points").asInt
                                val badgeName = item.get("BadgeName").asString
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
            val formBody = FormBody.Builder()
                .add("r", "awardachievement")
                .add("u", username)
                .add("t", token)
                .add("a", achievementId.toString())
                .add("h", hardcoreFlag)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/dorequest.php")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val json = gson.fromJson(body, JsonObject::class.java)

                if (json.has("Success") && json.get("Success").asBoolean) {
                    Result.success(true)
                } else {
                    val err = if (json.has("Error")) json.get("Error").asString else "Falha ao submeter conquista"
                    Result.failure(Exception(err))
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
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)

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
                        Result.failure(Exception("Usuário não encontrado"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

