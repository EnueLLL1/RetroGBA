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

    private val _hapticFeedback = MutableStateFlow(userPrefs.hapticFeedbackEnabled)
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _controllerOpacity = MutableStateFlow(userPrefs.controllerOpacity)
    val controllerOpacity: StateFlow<Float> = _controllerOpacity.asStateFlow()

    private val _fastForwardSpeed = MutableStateFlow(userPrefs.fastForwardSpeed)
    val fastForwardSpeed: StateFlow<Float> = _fastForwardSpeed.asStateFlow()

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

    fun setHapticFeedback(enabled: Boolean) {
        userPrefs.hapticFeedbackEnabled = enabled
        _hapticFeedback.value = enabled
    }

    fun setControllerOpacity(value: Float) {
        val clamped = value.coerceIn(0.2f, 1.0f)
        userPrefs.controllerOpacity = clamped
        _controllerOpacity.value = clamped
    }

    fun setFastForwardSpeed(speed: Float) {
        val clamped = speed.coerceIn(1.5f, 5.0f)
        userPrefs.fastForwardSpeed = clamped
        _fastForwardSpeed.value = clamped
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

