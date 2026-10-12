package com.retro.gba.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.UserProfile
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.data.repository.RetroAchievementsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferences(application)
    private val raApi = RetroAchievementsApi()
    val repository = RetroAchievementsRepository(raApi, userPrefs)

    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(repository.getUsername() ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isHardcore = MutableStateFlow(repository.isHardcore())
    val isHardcore: StateFlow<Boolean> = _isHardcore.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (_isLoggedIn.value) {
            fetchProfile()
        }
    }

    fun login(user: String, passOrToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.login(user.trim(), passOrToken.trim())
            result.onSuccess { (loggedUser, _) ->
                _isLoggedIn.value = true
                _username.value = loggedUser
                fetchProfile()
            }.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Erro ao autenticar no RetroAchievements"
            }
            _isLoading.value = false
        }
    }

    fun logout() {
        repository.logout()
        _isLoggedIn.value = false
        _userProfile.value = null
        _username.value = ""
    }

    fun setHardcore(enabled: Boolean) {
        repository.setHardcore(enabled)
        _isHardcore.value = enabled
    }

    private fun fetchProfile() {
        viewModelScope.launch {
            val result = repository.getUserProfile()
            result.onSuccess { profile ->
                _userProfile.value = profile
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

