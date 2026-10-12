package com.retro.gba.data.repository

import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.model.UserProfile
import com.retro.gba.data.prefs.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RetroAchievementsRepository(
    private val api: RetroAchievementsApi,
    private val preferences: UserPreferences
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
        val result = api.login(user, passOrToken)
        result.onSuccess { (returnedUser, token) ->
            preferences.username = returnedUser
            preferences.token = token
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
        return api.getGameAchievements(gameId)
    }

    suspend fun unlockAchievement(achievement: Achievement): Result<Boolean> {
        val user = preferences.username ?: return Result.failure(Exception("Usuário não logado"))
        val token = preferences.token ?: return Result.failure(Exception("Token não encontrado"))

        val result = api.awardAchievement(
            username = user,
            token = token,
            achievementId = achievement.id,
            isHardcore = preferences.isHardcoreMode
        )

        result.onSuccess {
            _achievementUnlockedEvents.emit(achievement.copy(isUnlocked = true))
        }

        return result
    }

    suspend fun getUserProfile(): Result<UserProfile> {
        val user = preferences.username ?: return Result.failure(Exception("Usuário não logado"))
        val key = preferences.webApiKey ?: preferences.token ?: ""
        return api.getUserProfile(user, key)
    }
}

