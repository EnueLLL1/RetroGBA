package com.retro.gba.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.UserProfile
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.data.repository.RetroAchievementsRepository
import com.retro.gba.model.ControlLayoutProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProfileViewModel internal constructor(
    application: Application,
    private val userPrefs: UserPreferences,
    val repository: RetroAchievementsRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        userPrefs = UserPreferences(application),
        repository = RetroAchievementsRepository(RetroAchievementsApi(), UserPreferences(application))
    )

    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(repository.getUsername() ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    val isHardcore: StateFlow<Boolean> = userPrefs.isHardcoreModeFlow

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val hapticFeedback: StateFlow<Boolean> = userPrefs.hapticFeedbackEnabledFlow
    val controllerOpacity: StateFlow<Float> = userPrefs.controllerOpacityFlow
    val fastForwardSpeed: StateFlow<Float> = userPrefs.fastForwardSpeedFlow
    val hideControls: StateFlow<Boolean> = userPrefs.hideControlsFlow

    private val initialProfiles = LayoutProfileHelper.loadMergedProfiles(userPrefs.layoutProfilesJson)
    private val _layoutProfiles = MutableStateFlow(initialProfiles)
    val layoutProfiles: StateFlow<List<ControlLayoutProfile>> = _layoutProfiles.asStateFlow()

    private val _activeLayoutProfile = MutableStateFlow(
        LayoutProfileHelper.resolveActiveProfile(initialProfiles, userPrefs.activeLayoutProfileId)
    )
    val activeLayoutProfile: StateFlow<ControlLayoutProfile> = _activeLayoutProfile.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userPrefs.layoutProfilesJsonFlow,
                userPrefs.activeLayoutProfileIdFlow
            ) { json, activeId ->
                val merged = LayoutProfileHelper.loadMergedProfiles(json)
                merged to LayoutProfileHelper.resolveActiveProfile(merged, activeId)
            }.collect { (merged, active) ->
                _layoutProfiles.value = merged
                _activeLayoutProfile.value = active
            }
        }

        if (_isLoggedIn.value) {
            fetchProfile()
        }
    }

    fun login(user: String, passOrToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = repository.loginWithDetails(user.trim(), passOrToken.trim())
            result.onSuccess { loginResult ->
                _isLoggedIn.value = true
                _username.value = loginResult.username
                _userProfile.value = UserProfile(
                    username = loginResult.username,
                    totalPoints = loginResult.score,
                    hardcorePoints = loginResult.score,
                    rank = 0,
                    avatarUrl = loginResult.avatarUrl,
                    softcorePoints = loginResult.softcoreScore
                )
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
    }

    fun setHapticFeedback(enabled: Boolean) {
        userPrefs.hapticFeedbackEnabled = enabled
    }

    fun setControllerOpacity(value: Float) {
        userPrefs.controllerOpacity = value.coerceIn(0.2f, 1.0f)
    }

    fun setFastForwardSpeed(speed: Float) {
        userPrefs.fastForwardSpeed = speed.coerceIn(1.5f, 5.0f)
    }

    fun setHideControls(hidden: Boolean) {
        userPrefs.hideControls = hidden
    }

    fun selectLayoutProfile(profileId: String) {
        val profiles = LayoutProfileHelper.selectProfile(userPrefs, profileId)
        _layoutProfiles.value = profiles
        _activeLayoutProfile.value = LayoutProfileHelper.resolveActiveProfile(profiles, userPrefs.activeLayoutProfileId)
    }

    fun createCustomLayoutProfile(name: String) {
        val result = LayoutProfileHelper.createCustomProfile(userPrefs, name, _activeLayoutProfile.value)
            ?: return
        val (updated, created) = result
        _layoutProfiles.value = updated
        _activeLayoutProfile.value = created
    }

    fun deleteCustomLayoutProfile(profileId: String) {
        val updated = LayoutProfileHelper.deleteCustomProfile(userPrefs, profileId)
        _layoutProfiles.value = updated
        _activeLayoutProfile.value = LayoutProfileHelper.resolveActiveProfile(updated, userPrefs.activeLayoutProfileId)
    }

    private fun fetchProfile() {
        viewModelScope.launch {
            val result = repository.fetchProfile()
            result.onSuccess { profile ->
                _userProfile.value = profile
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
