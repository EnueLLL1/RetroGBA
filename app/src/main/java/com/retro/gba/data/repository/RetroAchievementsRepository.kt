package com.retro.gba.data.repository

import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.api.RetroAchievementsServerException
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.LoginResult
import com.retro.gba.data.model.PendingAchievementUnlock
import com.retro.gba.data.model.UserProfile
import com.retro.gba.data.prefs.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException

class RetroAchievementsRepository(
    private val api: RetroAchievementsApi,
    private val preferences: UserPreferences,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    private val _achievementUnlockedEvents = MutableSharedFlow<Achievement>()
    val achievementUnlockedEvents: SharedFlow<Achievement> = _achievementUnlockedEvents.asSharedFlow()

    fun isLoggedIn(): Boolean = preferences.isLoggedIn()

    fun getUsername(): String? = preferences.username

    fun isHardcore(): Boolean = preferences.isHardcoreMode

    fun setHardcore(enabled: Boolean) {
        preferences.isHardcoreMode = enabled
    }

    suspend fun login(user: String, passOrToken: String): Result<Pair<String, String>> {
        return loginWithDetails(user, passOrToken).map { Pair(it.username, it.token) }
    }

    suspend fun loginWithDetails(user: String, passOrToken: String): Result<LoginResult> {
        val result = api.loginWithDetails(user, passOrToken)
        result.onSuccess { loginResult ->
            preferences.username = loginResult.username
            preferences.token = loginResult.token
            preferences.cachedScore = loginResult.score
            preferences.cachedSoftcoreScore = loginResult.softcoreScore
            preferences.cachedAvatarUrl = loginResult.avatarUrl
            if (passOrToken.length == 32 && passOrToken.all { it.isLetterOrDigit() }) {
                preferences.webApiKey = passOrToken
            }
        }
        return result
    }

    fun logout() {
        preferences.logout()
    }

    suspend fun findGameByRomHash(md5: String): Result<Long> {
        return api.getGameIdByHash(md5)
    }

    suspend fun getAchievements(gameId: Long): Result<Pair<GameInfo, List<Achievement>>> {
        val user = preferences.username
        val token = preferences.token ?: preferences.webApiKey
        return api.getGameAchievements(gameId, user, token)
    }

    suspend fun startSession(
        gameId: Long,
        romHash: String,
        hardcore: Boolean = preferences.isHardcoreMode
    ): Result<Set<Long>> {
        val user = preferences.username ?: return Result.failure(Exception("Usuário não logado"))
        val token = preferences.token ?: preferences.webApiKey ?: return Result.failure(Exception("Token não encontrado"))
        return api.startSession(user, token, gameId, romHash, hardcore)
    }

    suspend fun ping(
        gameId: Long,
        richPresence: String? = null,
        hardcore: Boolean = preferences.isHardcoreMode,
        gameHash: String = ""
    ): Result<Boolean> {
        val user = preferences.username ?: return Result.failure(Exception("Usuário não logado"))
        val token = preferences.token ?: preferences.webApiKey ?: return Result.failure(Exception("Token não encontrado"))
        return api.sendPing(
            username = user,
            token = token,
            gameId = gameId,
            richPresence = richPresence,
            hardcore = hardcore,
            gameHash = gameHash
        )
    }

    suspend fun sendPing(
        gameId: Long,
        richPresence: String? = null,
        hardcore: Boolean = preferences.isHardcoreMode,
        gameHash: String = ""
    ): Result<Boolean> = ping(gameId, richPresence, hardcore, gameHash)

    suspend fun unlockAchievement(
        achievement: Achievement,
        hardcore: Boolean = preferences.isHardcoreMode,
        gameHash: String = "",
        secondsSinceUnlock: Long = 0L
    ): Result<Boolean> {
        val result = awardAchievement(
            achievementId = achievement.id,
            hardcore = hardcore,
            gameHash = gameHash,
            secondsSinceUnlock = secondsSinceUnlock
        )

        result.onSuccess {
            _achievementUnlockedEvents.emit(achievement.copy(isUnlocked = true))
        }

        return result
    }

    suspend fun awardAchievement(
        achievement: Achievement,
        hardcore: Boolean = preferences.isHardcoreMode,
        gameHash: String = "",
        secondsSinceUnlock: Long = 0L
    ): Result<Boolean> = unlockAchievement(
        achievement = achievement,
        hardcore = hardcore,
        gameHash = gameHash,
        secondsSinceUnlock = secondsSinceUnlock
    )

    suspend fun awardAchievement(
        achievementId: Long,
        hardcore: Boolean = preferences.isHardcoreMode,
        gameHash: String = "",
        secondsSinceUnlock: Long = 0L
    ): Result<Boolean> {
        val user = preferences.username ?: return Result.failure(Exception("Usuário não logado"))
        val token = preferences.token ?: preferences.webApiKey ?: return Result.failure(Exception("Token não encontrado"))

        val result = api.awardAchievement(
            username = user,
            token = token,
            achievementId = achievementId,
            isHardcore = hardcore,
            gameHash = gameHash,
            secondsSinceUnlock = secondsSinceUnlock
        )

        result.onFailure { error ->
            if (isTransientFailure(error)) {
                val unlockedAt = (currentEpochSeconds() - secondsSinceUnlock.coerceAtLeast(0L))
                    .coerceAtLeast(0L)
                preferences.enqueuePendingUnlock(
                    PendingAchievementUnlock(
                        achievementId = achievementId.toInt(),
                        hardcore = hardcore,
                        gameHash = gameHash,
                        unlockedAtEpochSeconds = unlockedAt
                    )
                )
            }
        }

        return result
    }

    suspend fun flushPendingUnlocks(): Int {
        val user = preferences.username ?: return 0
        val token = preferences.token ?: preferences.webApiKey ?: return 0
        val pending = preferences.getPendingUnlocks()
        if (pending.isEmpty()) return 0

        var syncedCount = 0
        val nowEpochSeconds = currentEpochSeconds()

        for (unlock in pending) {
            val secondsSinceUnlock = (nowEpochSeconds - unlock.unlockedAtEpochSeconds).coerceAtLeast(0L)
            val result = api.awardAchievement(
                username = user,
                token = token,
                achievementId = unlock.achievementId.toLong(),
                isHardcore = unlock.hardcore,
                gameHash = unlock.gameHash,
                secondsSinceUnlock = secondsSinceUnlock
            )

            if (result.isSuccess) {
                preferences.removePendingUnlock(unlock.achievementId, unlock.hardcore)
                syncedCount++
            } else {
                val error = result.exceptionOrNull()
                if (error != null && isAlreadyUnlockedFailure(error)) {
                    preferences.removePendingUnlock(unlock.achievementId, unlock.hardcore)
                    syncedCount++
                } else if (error != null && !isTransientFailure(error) && !isAuthFailure(error)) {
                    // Rejeição definitiva não recuperável (ex.: ID/hash inválido)
                    preferences.removePendingUnlock(unlock.achievementId, unlock.hardcore)
                }
            }
        }

        return syncedCount
    }

    suspend fun getUserProfile(): Result<UserProfile> {
        val user = preferences.username ?: return Result.failure(Exception("Usuário não logado"))
        val webApiKey = preferences.webApiKey
        if (!webApiKey.isNullOrBlank()) {
            val remote = api.getUserProfile(user, webApiKey)
            if (remote.isSuccess) {
                remote.getOrNull()?.let { profile ->
                    preferences.cachedScore = profile.hardcorePoints
                    preferences.cachedSoftcoreScore = profile.softcorePoints
                    if (profile.avatarUrl.isNotBlank()) {
                        preferences.cachedAvatarUrl = profile.avatarUrl
                    }
                }
                return remote
            }
        } else {
            val token = preferences.token.orEmpty()
            if (token.isNotBlank()) {
                val remote = api.getUserProfile(user, token)
                if (remote.isSuccess) {
                    return remote
                }
            }
        }
        return Result.success(buildCachedProfile(user))
    }

    suspend fun fetchProfile(): Result<UserProfile> = getUserProfile()

    private fun buildCachedProfile(username: String): UserProfile {
        val avatarUrl = preferences.cachedAvatarUrl.ifBlank {
            "https://media.retroachievements.org/UserPic/$username.png"
        }
        val hardcorePoints = preferences.cachedScore
        val softcorePoints = preferences.cachedSoftcoreScore
        return UserProfile(
            username = username,
            totalPoints = hardcorePoints,
            hardcorePoints = hardcorePoints,
            rank = 0,
            avatarUrl = avatarUrl,
            softcorePoints = softcorePoints
        )
    }

    private fun isTransientFailure(error: Throwable): Boolean {
        return when (error) {
            is RetroAchievementsServerException -> error.isTransient
            is IOException -> true
            else -> false
        }
    }

    private fun isAlreadyUnlockedFailure(error: Throwable): Boolean {
        if (error is RetroAchievementsServerException && error.isAlreadyUnlocked) return true
        val msg = error.message.orEmpty()
        return msg.contains("already has", ignoreCase = true) ||
            msg.contains("already awarded", ignoreCase = true) ||
            msg.contains("already unlocked", ignoreCase = true)
    }

    private fun isAuthFailure(error: Throwable): Boolean {
        if (error is RetroAchievementsServerException && error.isAuthError) return true
        val msg = error.message.orEmpty()
        return msg.contains("invalid credentials", ignoreCase = true) ||
            msg.contains("credentials invalid", ignoreCase = true) ||
            msg.contains("invalid token", ignoreCase = true) ||
            msg.contains("expired token", ignoreCase = true) ||
            msg.contains("unauthenticated", ignoreCase = true)
    }
}
