package com.retro.gba.data.api

import com.retro.gba.data.model.UserProfile
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetroAchievementsApiTest {

    private data class RecordedRequest(
        val url: String,
        val method: String,
        val queryParams: Map<String, String?>,
        val formParams: Map<String, String>
    )

    private fun createMockApi(
        handler: (RecordedRequest) -> Response
    ): Pair<RetroAchievementsApi, MutableList<RecordedRequest>> {
        val recorded = mutableListOf<RecordedRequest>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val queryMap = req.url.queryParameterNames.associateWith { name ->
                    req.url.queryParameter(name)
                }
                val formMap = mutableMapOf<String, String>()
                val body = req.body
                if (body is FormBody) {
                    for (i in 0 until body.size) {
                        formMap[body.name(i)] = body.value(i)
                    }
                }
                val snapshot = RecordedRequest(
                    url = req.url.toString(),
                    method = req.method,
                    queryParams = queryMap,
                    formParams = formMap
                )
                recorded.add(snapshot)
                handler(snapshot)
            })
            .build()
        return RetroAchievementsApi(baseUrl = "https://retroachievements.org", client = client) to recorded
    }

    private fun jsonResponse(
        url: String = "https://retroachievements.org/dorequest.php",
        code: Int = 200,
        body: String
    ): Response {
        val req = Request.Builder().url(url).build()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun loginWithDetails_usesLogin2AndExtractsScoresTokenAndAvatar() = runBlocking {
        val (api, requests) = createMockApi { req ->
            jsonResponse(
                url = req.url,
                body = """
                    {
                        "Success": true,
                        "User": "GbaMaster",
                        "Token": "appToken16CharsX",
                        "Score": 4250,
                        "SoftcoreScore": 310,
                        "AvatarUrl": "/UserPic/GbaMaster.png"
                    }
                """.trimIndent()
            )
        }

        val result = api.loginWithDetails("gbamaster", "secretPass123")

        assertTrue(result.isSuccess)
        val login = result.getOrThrow()
        assertEquals("GbaMaster", login.username)
        assertEquals("appToken16CharsX", login.token)
        assertEquals(4250, login.score)
        assertEquals(310, login.softcoreScore)
        assertEquals("https://media.retroachievements.org/UserPic/GbaMaster.png", login.avatarUrl)

        assertEquals(1, requests.size)
        assertEquals("POST", requests[0].method)
        assertEquals("login2", requests[0].formParams["r"])
        assertEquals("gbamaster", requests[0].formParams["u"])
        assertEquals("secretPass123", requests[0].formParams["p"])
    }

    @Test
    fun login_keepsPairCompatibilityWhileUsingLogin2() = runBlocking {
        val (api, requests) = createMockApi { req ->
            jsonResponse(
                url = req.url,
                body = """{"Success":true,"User":"PlayerOne","Token":"tok123","Score":100,"SoftcoreScore":50}"""
            )
        }

        val result = api.login("PlayerOne", "myPass")

        assertTrue(result.isSuccess)
        assertEquals(Pair("PlayerOne", "tok123"), result.getOrThrow())
        assertEquals("login2", requests[0].formParams["r"])
    }

    @Test
    fun getGameIdByHash_whenGameIdIsZeroOrNegative_returnsFailure() = runBlocking {
        val (apiZero, _) = createMockApi { req ->
            jsonResponse(url = req.url, body = """{"Success":true,"GameID":0}""")
        }
        val zeroResult = apiZero.getGameIdByHash("deadbeef0123456789abcdef01234567")
        assertTrue(zeroResult.isFailure)
        assertTrue(zeroResult.exceptionOrNull()!!.message!!.contains("Jogo não encontrado no RetroAchievements"))

        val (apiNegative, _) = createMockApi { req ->
            jsonResponse(url = req.url, body = """{"Success":true,"GameID":-1}""")
        }
        val negResult = apiNegative.getGameIdByHash("deadbeef0123456789abcdef01234567")
        assertTrue(negResult.isFailure)

        val (apiValid, _) = createMockApi { req ->
            jsonResponse(url = req.url, body = """{"Success":true,"GameID":1446}""")
        }
        val validResult = apiValid.getGameIdByHash("deadbeef0123456789abcdef01234567")
        assertTrue(validResult.isSuccess)
        assertEquals(1446L, validResult.getOrThrow())
    }

    @Test
    fun getGameAchievements_filtersStrictlyFlags3AndExtractsRichPresencePatch() = runBlocking {
        val (api, _) = createMockApi { req ->
            jsonResponse(
                url = req.url,
                body = """
                    {
                        "Success": true,
                        "PatchData": {
                            "ID": 788,
                            "Title": "Pokemon Emerald",
                            "ConsoleID": 5,
                            "ImageIcon": "/Images/078800.png",
                            "RichPresencePatch": "Display:\n@Area(0x020322E4) - Badges: @Val(0x020244EC)",
                            "Achievements": [
                                {
                                    "ID": 1001,
                                    "Title": "Oficial 1",
                                    "Description": "Conquista oficial",
                                    "Flags": 3,
                                    "Points": 10,
                                    "BadgeName": "1001",
                                    "MemAddr": "0xH02000000=1"
                                },
                                {
                                    "ID": 1002,
                                    "Title": "Nao Oficial",
                                    "Description": "Deve ser ignorada",
                                    "Flags": 5,
                                    "Points": 50,
                                    "BadgeName": "1002",
                                    "MemAddr": "0xH02000001=1"
                                },
                                {
                                    "ID": 1003,
                                    "Title": "Sem Flag",
                                    "Description": "Tambem ignorada",
                                    "Points": 25,
                                    "BadgeName": "1003",
                                    "MemAddr": "0xH02000002=1"
                                },
                                {
                                    "ID": 1004,
                                    "Title": "Oficial 2",
                                    "Description": "Outra oficial",
                                    "Flags": 3,
                                    "Points": 15,
                                    "BadgeName": "1004",
                                    "MemAddr": "0xH02000003=2"
                                }
                            ]
                        }
                    }
                """.trimIndent()
            )
        }

        val result = api.getGameAchievements(788L, "GbaMaster", "tok123")

        assertTrue(result.isSuccess)
        val (gameInfo, achievements) = result.getOrThrow()
        assertEquals("Pokemon Emerald", gameInfo.title)
        assertEquals("Display:\n@Area(0x020322E4) - Badges: @Val(0x020244EC)", gameInfo.richPresencePatch)
        assertEquals(2, achievements.size)
        assertEquals(listOf(1001L, 1004L), achievements.map { it.id })
        assertEquals(2, gameInfo.totalAchievements)
        assertEquals(25, gameInfo.totalPoints)
    }

    @Test
    fun getGameAchievements_handlesNullRichPresencePatchGracefully() = runBlocking {
        val (api, _) = createMockApi { req ->
            jsonResponse(
                url = req.url,
                body = """
                    {
                        "Success": true,
                        "PatchData": {
                            "ID": 55,
                            "Title": "Metroid Fusion",
                            "ImageIcon": "/Images/000055.png",
                            "RichPresencePatch": null,
                            "Achievements": []
                        }
                    }
                """.trimIndent()
            )
        }

        val result = api.getGameAchievements(55L)
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow().first.richPresencePatch)
    }

    @Test
    fun sendPing_includesHardcoreAndGameHashParameters() = runBlocking {
        val (api, requests) = createMockApi { req ->
            jsonResponse(url = req.url, body = """{"Success":true}""")
        }

        val hcResult = api.sendPing(
            username = "Player1",
            token = "tok123",
            gameId = 788L,
            richPresence = "Petalburg Woods",
            hardcore = true,
            gameHash = "abcdef0123456789abcdef0123456789"
        )
        assertTrue(hcResult.isSuccess)
        assertEquals("ping", requests[0].formParams["r"])
        assertEquals("1", requests[0].formParams["h"])
        assertEquals("abcdef0123456789abcdef0123456789", requests[0].formParams["x"])
        assertEquals("Petalburg Woods", requests[0].formParams["m"])

        val softResult = api.sendPing(
            username = "Player1",
            token = "tok123",
            gameId = 788L,
            richPresence = null,
            hardcore = false,
            gameHash = ""
        )
        assertTrue(softResult.isSuccess)
        assertEquals("0", requests[1].formParams["h"])
        assertFalse(requests[1].formParams.containsKey("x"))
        assertFalse(requests[1].formParams.containsKey("m"))
    }

    @Test
    fun awardAchievement_includesGameHashSecondsAndOfficialRcheevosSignature() = runBlocking {
        val (api, requests) = createMockApi { req ->
            jsonResponse(url = req.url, body = """{"Success":true,"Score":1500}""")
        }

        // Caso 1: immediate unlock (secondsSinceUnlock = 0 -> v = md5("1234Username1"))
        val immediate = api.awardAchievement(
            username = "Username",
            token = "API_TOKEN",
            achievementId = 1234L,
            isHardcore = true,
            gameHash = "ABCDEF0123456789",
            secondsSinceUnlock = 0L
        )
        assertTrue(immediate.isSuccess)
        assertEquals("awardachievement", requests[0].formParams["r"])
        assertEquals("1234", requests[0].formParams["a"])
        assertEquals("1", requests[0].formParams["h"])
        assertEquals("ABCDEF0123456789", requests[0].formParams["m"])
        assertEquals("0", requests[0].formParams["o"])
        assertEquals("b8aefaad6f9659e2164bc60da0c3b64d", requests[0].formParams["v"])

        // Caso 2: delayed unlock offline flush (secondsSinceUnlock = 17 -> v = md5("1234Username1123417"))
        val delayed = api.awardAchievement(
            username = "Username",
            token = "API_TOKEN",
            achievementId = 1234L,
            isHardcore = true,
            gameHash = "ABCDEF0123456789",
            secondsSinceUnlock = 17L
        )
        assertTrue(delayed.isSuccess)
        assertEquals("17", requests[1].formParams["o"])
        assertEquals("b2326b09d61e9264eb5d3607d947317d", requests[1].formParams["v"])

        // Caso 3: negativo deve ser clampado para 0 e hash vazio omitido
        val clamped = api.awardAchievement(
            username = "Username",
            token = "API_TOKEN",
            achievementId = 5432L,
            isHardcore = true,
            gameHash = "",
            secondsSinceUnlock = -5L
        )
        assertTrue(clamped.isSuccess)
        assertEquals("0", requests[2].formParams["o"])
        assertFalse(requests[2].formParams.containsKey("m"))
        assertEquals("31048257ab1788386e71ab0c222aa5c8", requests[2].formParams["v"])
    }

    @Test
    fun getUserProfile_parsesHardcoreAndSoftcoreSeparately_andHandlesNullRank() = runBlocking {
        val (api, _) = createMockApi { req ->
            jsonResponse(
                url = req.url,
                body = """
                    {
                        "TotalPoints": 2500,
                        "TotalSoftcorePoints": 420,
                        "Rank": null,
                        "UserPic": "/UserPic/Tester.png"
                    }
                """.trimIndent()
            )
        }

        val result = api.getUserProfile("Tester", "0123456789abcdef0123456789abcdef")

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(2500, profile.hardcorePoints)
        assertEquals(420, profile.softcorePoints)
        assertEquals(0, profile.rank)
        assertEquals("https://media.retroachievements.org/UserPic/Tester.png", profile.avatarUrl)
    }

    @Test
    fun fetchProfile_onHttp401_usesCleanFallbackWhenProvided() = runBlocking {
        val (api, _) = createMockApi { req ->
            jsonResponse(url = req.url, code = 401, body = """{"message":"Unauthenticated."}""")
        }

        val rawFailure = api.getUserProfile("Tester", "appTokenOnly")
        assertTrue(rawFailure.isFailure)

        val fallback = UserProfile(
            username = "Tester",
            totalPoints = 900,
            hardcorePoints = 900,
            rank = 0,
            avatarUrl = "https://media.retroachievements.org/UserPic/Tester.png",
            softcorePoints = 150
        )
        val recovered = api.fetchProfile("Tester", "appTokenOnly", fallbackProfile = fallback)
        assertTrue(recovered.isSuccess)
        assertEquals(fallback, recovered.getOrThrow())
    }

    @Test
    fun awardAchievement_whenNetworkThrowsIOException_returnsFailure() = runBlocking {
        val (api, _) = createMockApi {
            throw IOException("Sem conexão")
        }

        val result = api.awardAchievement("User", "Tok", 101L, true, "hash", 0L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
