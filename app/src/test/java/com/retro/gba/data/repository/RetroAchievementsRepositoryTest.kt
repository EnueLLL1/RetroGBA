package com.retro.gba.data.repository

import android.content.SharedPreferences
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.PendingAchievementUnlock
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.security.CryptoManager
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
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class RetroAchievementsRepositoryTest {

    private class InMemorySharedPreferences : SharedPreferences {
        val store = ConcurrentHashMap<String, Any>()
        private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = store.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            (store[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (store[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (store[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (store[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (store[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (store[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?) = apply {
                if (key != null) pending[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values
            }

            override fun putInt(key: String?, value: Int) = apply {
                if (key != null) pending[key] = value
            }

            override fun putLong(key: String?, value: Long) = apply {
                if (key != null) pending[key] = value
            }

            override fun putFloat(key: String?, value: Float) = apply {
                if (key != null) pending[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean) = apply {
                if (key != null) pending[key] = value
            }

            override fun remove(key: String?) = apply {
                if (key != null) pending[key] = null
            }

            override fun clear() = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    store.clear()
                }
                val changedKeys = mutableListOf<String>()
                for ((k, v) in pending) {
                    if (v == null) {
                        store.remove(k)
                    } else {
                        store[k] = v
                    }
                    changedKeys.add(k)
                }
                for (listener in listeners) {
                    if (clearRequested) {
                        listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, null)
                    }
                    for (k in changedKeys) {
                        listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, k)
                    }
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener != null) listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener != null) listeners.remove(listener)
        }
    }

    private data class RecordedCall(
        val url: String,
        val formParams: Map<String, String>
    )

    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var preferences: UserPreferences
    private var nowEpochSeconds: Long = 1_700_000_000L

    @Before
    fun setUp() {
        sharedPrefs = InMemorySharedPreferences()
        preferences = UserPreferences(sharedPrefs, CryptoManager("RepoTestKey"))
        nowEpochSeconds = 1_700_000_000L
    }

    private fun createRepository(
        handler: (RecordedCall) -> Response
    ): Pair<RetroAchievementsRepository, MutableList<RecordedCall>> {
        val calls = mutableListOf<RecordedCall>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val formMap = mutableMapOf<String, String>()
                val body = req.body
                if (body is FormBody) {
                    for (i in 0 until body.size) {
                        formMap[body.name(i)] = body.value(i)
                    }
                }
                val call = RecordedCall(req.url.toString(), formMap)
                calls.add(call)
                handler(call)
            })
            .build()
        val api = RetroAchievementsApi(baseUrl = "https://retroachievements.org", client = client)
        val repo = RetroAchievementsRepository(
            api = api,
            preferences = preferences,
            currentEpochSeconds = { nowEpochSeconds }
        )
        return repo to calls
    }

    private fun response(url: String, code: Int = 200, json: String): Response {
        val req = Request.Builder().url(url).build()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun login_savesCachedScoresAndAvatar_andProfileFallsBackCleanlyOn401() = runBlocking {
        val (repo, calls) = createRepository { call ->
            if (call.url.contains("dorequest.php")) {
                response(
                    url = call.url,
                    json = """
                        {
                            "Success": true,
                            "User": "HeroGBA",
                            "Token": "appToken16Chars",
                            "Score": 3100,
                            "SoftcoreScore": 450,
                            "AvatarUrl": "/UserPic/HeroGBA.png"
                        }
                    """.trimIndent()
                )
            } else {
                // Simula 401 do API_GetUserSummary.php pois login2 com senha não tem Web API Key
                response(
                    url = call.url,
                    code = 401,
                    json = """{"message":"Unauthenticated."}"""
                )
            }
        }

        val loginResult = repo.login("herogba", "mySecretPassword")
        assertTrue(loginResult.isSuccess)
        assertEquals("HeroGBA", preferences.username)
        assertEquals("appToken16Chars", preferences.token)
        assertNull(preferences.webApiKey)
        assertEquals(3100, preferences.cachedScore)
        assertEquals(450, preferences.cachedSoftcoreScore)
        assertEquals("https://media.retroachievements.org/UserPic/HeroGBA.png", preferences.cachedAvatarUrl)

        val profileResult = repo.getUserProfile()
        assertTrue(profileResult.isSuccess)
        val profile = profileResult.getOrThrow()
        assertEquals("HeroGBA", profile.username)
        assertEquals(3100, profile.hardcorePoints)
        assertEquals(450, profile.softcorePoints)
        assertEquals("https://media.retroachievements.org/UserPic/HeroGBA.png", profile.avatarUrl)
        assertTrue(calls.any { it.url.contains("API_GetUserSummary.php") })
    }

    @Test
    fun getUserProfile_whenSummaryReturnsEmptyBody_fallsBackToCachedLogin2Data() = runBlocking {
        preferences.username = "OfflineHero"
        preferences.token = "tok"
        preferences.cachedScore = 700
        preferences.cachedSoftcoreScore = 120
        preferences.cachedAvatarUrl = "https://media.retroachievements.org/UserPic/OfflineHero.png"

        val (repo, _) = createRepository { call ->
            response(url = call.url, code = 200, json = "")
        }

        val result = repo.fetchProfile()
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(700, profile.hardcorePoints)
        assertEquals(120, profile.softcorePoints)
        assertEquals("https://media.retroachievements.org/UserPic/OfflineHero.png", profile.avatarUrl)
    }

    @Test
    fun sendPing_forwardsHardcoreAndGameHash() = runBlocking {
        preferences.username = "Player1"
        preferences.token = "tok123"
        val (repo, calls) = createRepository { call ->
            response(url = call.url, json = """{"Success":true}""")
        }

        val result = repo.sendPing(
            gameId = 999L,
            richPresence = "Boss Fight",
            hardcore = false,
            gameHash = "hash123456"
        )

        assertTrue(result.isSuccess)
        assertEquals("0", calls[0].formParams["h"])
        assertEquals("hash123456", calls[0].formParams["x"])
        assertEquals("Boss Fight", calls[0].formParams["m"])
    }

    @Test
    fun awardAchievement_enqueuesOnTransientNetworkErrorAndDeduplicates() = runBlocking {
        preferences.username = "Player1"
        preferences.token = "tok123"
        val (repo, _) = createRepository {
            throw IOException("Sem rede")
        }

        val r1 = repo.awardAchievement(achievementId = 501L, hardcore = true, gameHash = "romMd5Hash")
        assertTrue(r1.isFailure)

        nowEpochSeconds += 10
        val r2 = repo.awardAchievement(achievementId = 501L, hardcore = true, gameHash = "romMd5Hash")
        assertTrue(r2.isFailure)

        // Mesmo achievementId mas hardcore=false deve ser aceito como entrada distinta
        val r3 = repo.awardAchievement(achievementId = 501L, hardcore = false, gameHash = "romMd5Hash")
        assertTrue(r3.isFailure)

        val pending = preferences.getPendingUnlocks()
        assertEquals(2, pending.size)
        assertEquals(1_700_000_000L, pending[0].unlockedAtEpochSeconds)
        assertTrue(pending[0].hardcore)
        assertFalse(pending[1].hardcore)
    }

    @Test
    fun awardAchievement_doesNotEnqueueOnPermanentErrors() = runBlocking {
        preferences.username = "Player1"
        preferences.token = "tok123"

        val (repoAlready, _) = createRepository { call ->
            response(
                url = call.url,
                json = """{"Success":false,"Error":"User already has this achievement awarded."}"""
            )
        }
        val alreadyRes = repoAlready.awardAchievement(101L, hardcore = true, gameHash = "hash1")
        assertTrue(alreadyRes.isFailure)
        assertTrue(preferences.getPendingUnlocks().isEmpty())

        val (repoInvalidCreds, _) = createRepository { call ->
            response(
                url = call.url,
                code = 401,
                json = """{"Success":false,"Error":"Invalid credentials"}"""
            )
        }
        val authRes = repoInvalidCreds.awardAchievement(102L, hardcore = true, gameHash = "hash1")
        assertTrue(authRes.isFailure)
        assertTrue(preferences.getPendingUnlocks().isEmpty())
    }

    @Test
    fun flushPendingUnlocks_sendsSecondsSinceUnlockAndRemovesConfirmedOrAlreadyAwarded() = runBlocking {
        preferences.username = "Username"
        preferences.token = "API_TOKEN"
        preferences.enqueuePendingUnlock(
            PendingAchievementUnlock(
                achievementId = 1234,
                hardcore = true,
                gameHash = "ABCDEF0123456789",
                unlockedAtEpochSeconds = 1_700_000_000L
            )
        )
        preferences.enqueuePendingUnlock(
            PendingAchievementUnlock(
                achievementId = 5678,
                hardcore = false,
                gameHash = "ABCDEF0123456789",
                unlockedAtEpochSeconds = 1_700_000_010L
            )
        )
        preferences.enqueuePendingUnlock(
            PendingAchievementUnlock(
                achievementId = 9999,
                hardcore = true,
                gameHash = "ABCDEF0123456789",
                unlockedAtEpochSeconds = 1_700_000_020L
            )
        )

        // Avança o relógio para testar cálculo de secondsSinceUnlock
        nowEpochSeconds = 1_700_000_050L

        val (repo, calls) = createRepository { call ->
            when (call.formParams["a"]) {
                "1234" -> response(url = call.url, json = """{"Success":true}""")
                "5678" -> response(
                    url = call.url,
                    json = """{"Success":false,"Error":"User already has this achievement awarded."}"""
                )
                else -> response(url = call.url, code = 503, json = """<html><title>503</title></html>""")
            }
        }

        val synced = repo.flushPendingUnlocks()

        assertEquals(2, synced)
        assertEquals(3, calls.size)
        assertEquals("50", calls[0].formParams["o"])
        assertEquals("ABCDEF0123456789", calls[0].formParams["m"])
        assertEquals("40", calls[1].formParams["o"])

        val remaining = preferences.getPendingUnlocks()
        assertEquals(1, remaining.size)
        assertEquals(9999, remaining[0].achievementId)
    }

    @Test
    fun userPreferences_reactiveFlowsSyncAcrossInstancesSharingSamePrefs() {
        val prefsInstanceA = UserPreferences(sharedPrefs, CryptoManager("RepoTestKey"))
        val prefsInstanceB = UserPreferences(sharedPrefs, CryptoManager("RepoTestKey"))

        prefsInstanceA.isHardcoreMode = false
        assertFalse(prefsInstanceB.isHardcoreModeFlow.value)

        prefsInstanceA.controllerOpacity = 0.45f
        assertEquals(0.45f, prefsInstanceB.controllerOpacityFlow.value, 0.001f)

        prefsInstanceA.hapticFeedbackEnabled = false
        assertFalse(prefsInstanceB.hapticFeedbackEnabledFlow.value)

        prefsInstanceA.fastForwardSpeed = 4.0f
        assertEquals(4.0f, prefsInstanceB.fastForwardSpeedFlow.value, 0.001f)

        prefsInstanceA.hideControls = true
        assertTrue(prefsInstanceB.hideControlsFlow.value)

        prefsInstanceA.activeLayoutProfileId = "comfortable"
        assertEquals("comfortable", prefsInstanceB.activeLayoutProfileIdFlow.value)

        prefsInstanceA.layoutProfilesJson = "[{\"id\":\"custom\"}]"
        assertEquals("[{\"id\":\"custom\"}]", prefsInstanceB.layoutProfilesJsonFlow.value)
    }

    @Test
    fun userPreferences_corruptedPendingUnlocksJson_recoversWithoutCrashing() {
        sharedPrefs.edit().putString("ra_pending_unlocks_json", "{invalid-json").apply()
        val items = preferences.getPendingUnlocks()
        assertTrue(items.isEmpty())
    }
}
